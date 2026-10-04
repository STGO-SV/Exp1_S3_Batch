package com.duoc.banco_legacy.payment;
import com.duoc.banco_legacy.payment.domain.*;
import com.duoc.banco_legacy.core.event.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class PaymentFinancialTests extends JwtTestSupport {
 @Autowired PaymentStore store;@Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired ObjectMapper json;
 @MockBean AccountPostingClient client;
 @BeforeEach void setup(){
  jdbc.update("DELETE FROM eft_payment_event_audit");jdbc.update("DELETE FROM eft_payment_operation");
  doAnswer(invocation->receipt(invocation.getArgument(2))).when(client).post(anyString(),anyString(),any());
 }
 static FinancialOperationResult receipt(FinancialOperationRequest request){
  return new FinancialOperationResult(request.operationId(),request.type(),request.sourceAccountId(),request.targetAccountId(),
   request.amount(),"COMPLETED",request.sourceAccountId()==null?null:new BigDecimal("50"),request.targetAccountId()==null?null:new BigDecimal("50"),Instant.parse("2026-10-04T00:00:00Z"));
 }
 String execute(String uri,String key,String body,int expected)throws Exception{
  return mvc.perform(post(uri).with(bearer("PAYMENT_OPERATOR","payments.write")).header("Idempotency-Key",key)
   .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
 }
 @Test void depositPersistsAndReplayDoesNotCallAccountTwice()throws Exception{
  var first=execute("/api/payments/deposits","deposit","{\"accountId\":101,\"amount\":10}",201);
  var second=execute("/api/payments/deposits","deposit","{\"accountId\":101,\"amount\":10.00}",200);
  assertThat(json.readTree(second).get("operationId")).isEqualTo(json.readTree(first).get("operationId"));
  assertThat(json.readTree(second).get("status").asText()).isEqualTo("COMPLETED");
  verify(client,times(1)).post(startsWith("Bearer "),eq("deposit"),any());
 }
 @Test void transferAndRecordedPaymentArePersisted()throws Exception{
  execute("/api/payments/transfers","transfer","{\"sourceAccountId\":101,\"targetAccountId\":102,\"amount\":10}",201);
  execute("/api/payments","payment","{\"sourceAccountId\":101,\"amount\":10}",201);
  execute("/api/payments","payment","{\"sourceAccountId\":101,\"amount\":10}",200);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_payment_operation WHERE status='COMPLETED'",Integer.class)).isEqualTo(2);
  verify(client,times(2)).post(anyString(),anyString(),any());
 }
 @Test void sameKeyWithDifferentPayloadConflicts()throws Exception{
  execute("/api/payments/deposits","key","{\"accountId\":101,\"amount\":10}",201);
  execute("/api/payments/deposits","key","{\"accountId\":101,\"amount\":11}",409);
  verify(client,times(1)).post(anyString(),anyString(),any());
 }
 @Test void invalidAmountsSameAccountAndMissingKeyDoNotPrepareOperation()throws Exception{
  execute("/api/payments/transfers","same","{\"sourceAccountId\":101,\"targetAccountId\":101,\"amount\":1}",400);
  for(String amount:List.of("0","-1","0.001"))
   execute("/api/payments/deposits","invalid","{\"accountId\":101,\"amount\":"+amount+"}",400);
  mvc.perform(post("/api/payments").with(bearer("PAYMENT_OPERATOR","payments.write")).contentType(MediaType.APPLICATION_JSON)
   .content("{\"sourceAccountId\":101,\"amount\":1}")).andExpect(status().isBadRequest());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_payment_operation",Integer.class)).isZero();
  verifyNoInteractions(client);
 }
 @Test void businessRejectionIsFailedAndReplayedWithoutCallingAccountAgain()throws Exception{
  doThrow(new PaymentException(409,"INSUFFICIENT_BALANCE")).when(client).post(anyString(),anyString(),any());
  execute("/api/payments","failed","{\"sourceAccountId\":101,\"amount\":10}",409);
  execute("/api/payments","failed","{\"sourceAccountId\":101,\"amount\":10}",409);
  assertThat(jdbc.queryForObject("SELECT status FROM eft_payment_operation",String.class)).isEqualTo("FAILED");
  verify(client,times(1)).post(anyString(),anyString(),any());
 }
 @Test void closedOrMissingAccountAreTerminalRejections()throws Exception{
  doThrow(new PaymentException(409,"ACCOUNT_CLOSED")).when(client).post(anyString(),anyString(),any());
  execute("/api/payments/deposits","closed","{\"accountId\":101,\"amount\":1}",409);
  doThrow(new PaymentException(404,"ACCOUNT_NOT_FOUND")).when(client).post(anyString(),anyString(),any());
  execute("/api/payments/deposits","missing","{\"accountId\":999,\"amount\":1}",404);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_payment_operation WHERE status='FAILED'",Integer.class)).isEqualTo(2);
 }
 @Test void unavailableAccountStaysPendingAndRetryRecoversSameOperation()throws Exception{
  doThrow(new PaymentException(503,"ACCOUNT_UNAVAILABLE")).when(client).post(anyString(),anyString(),any());
  execute("/api/payments/deposits","retry","{\"accountId\":101,\"amount\":10}",503);
  UUID pending=jdbc.queryForObject("SELECT operation_id FROM eft_payment_operation",UUID.class);
  assertThat(store.get("test-user",pending).status()).isEqualTo("PENDING");
  doAnswer(invocation->receipt(invocation.getArgument(2))).when(client).post(anyString(),anyString(),any());
  var recovered=execute("/api/payments/deposits","retry","{\"accountId\":101,\"amount\":10}",200);
  assertThat(json.readTree(recovered).get("operationId").asText()).isEqualTo(pending.toString());
  assertThat(store.get("test-user",pending).status()).isEqualTo("COMPLETED");
 }
 @Test void lostHttpResponseIsReconciledByValidatedEventWithoutNewFinancialCall()throws Exception{
  doThrow(new PaymentException(503,"ACCOUNT_UNAVAILABLE")).when(client).post(anyString(),anyString(),any());
  execute("/api/payments","lost","{\"sourceAccountId\":101,\"amount\":10}",503);
  UUID id=jdbc.queryForObject("SELECT operation_id FROM eft_payment_operation",UUID.class);
  var operation=store.get("test-user",id);
  var event=new FinancialOperationCompletedEvent(UUID.randomUUID(),1,operation.actor(),operation.request().fingerprint(),receipt(operation.request()));
  store.acceptEvent(event);store.acceptEvent(event);
  execute("/api/payments","lost","{\"sourceAccountId\":101,\"amount\":10}",200);
  verify(client,times(1)).post(anyString(),anyString(),any());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_payment_event_audit",Integer.class)).isEqualTo(1);
 }
 @Test void incorrectEventCorrelationRollsBackAuditAndDoesNotComplete(){
  var request=new FinancialOperationRequest(UUID.randomUUID(),"DEPOSIT",null,101L,new BigDecimal("1"));
  var operation=store.prepare("actor","key",request).operation();
  var event=new FinancialOperationCompletedEvent(UUID.randomUUID(),1,"other",request.fingerprint(),receipt(request));
  assertThatThrownBy(()->store.acceptEvent(event)).isInstanceOf(IllegalArgumentException.class);
  assertThat(store.get("actor",operation.operationId()).status()).isEqualTo("PENDING");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_payment_event_audit",Integer.class)).isZero();
 }
 @Test void directAccountEventsHaveUsefulAuditWithoutInventingPayment(){
  var request=new FinancialOperationRequest(UUID.randomUUID(),"DEPOSIT",null,101L,new BigDecimal("1"));
  store.acceptEvent(new FinancialOperationCompletedEvent(UUID.randomUUID(),1,"actor",request.fingerprint(),receipt(request)));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_payment_event_audit",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_payment_operation",Integer.class)).isZero();
 }
 @Test void actorIsolationAndSecurityScopes()throws Exception{
  var request=new FinancialOperationRequest(UUID.randomUUID(),"DEPOSIT",null,101L,new BigDecimal("1"));
  var operation=store.prepare("other","key",request).operation();
  mvc.perform(get("/api/payments/operations/"+operation.operationId()).with(bearer("PAYMENT_OPERATOR","payments.read"))).andExpect(status().isNotFound());
  mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/payments").with(bearer("DOMAIN_OPERATOR","accounts.write")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
 }
}
