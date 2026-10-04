package com.duoc.banco_legacy.payment;
import com.duoc.banco_legacy.payment.domain.*;
import com.duoc.banco_legacy.core.event.*;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.util.*;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.springframework.cloud.circuitbreaker.resilience4j.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class AccountPostingClientTests {
 static final String URL="https://banco-legacy-account-service/internal/accounts/postings";
 MockRestServiceServer server;AccountPostingClient client;Resilience4JCircuitBreakerFactory factory;
 FinancialOperationRequest request=new FinancialOperationRequest(UUID.randomUUID(),"DEPOSIT",null,101L,new BigDecimal("10"));
 @BeforeEach void configure(){
  var builder=RestClient.builder();server=MockRestServiceServer.bindTo(builder).build();
  var properties=new Resilience4JConfigurationProperties();
  factory=new Resilience4JCircuitBreakerFactory(CircuitBreakerRegistry.ofDefaults(),TimeLimiterRegistry.ofDefaults(),
   new Resilience4jBulkheadProvider(ThreadPoolBulkheadRegistry.ofDefaults(),BulkheadRegistry.ofDefaults(),properties),properties);
  factory.configureDefault(id->new Resilience4JConfigBuilder(id).circuitBreakerConfig(CircuitBreakerConfig.custom()
   .slidingWindowSize(3).minimumNumberOfCalls(3).failureRateThreshold(50).permittedNumberOfCallsInHalfOpenState(1)
   .waitDurationInOpenState(Duration.ofSeconds(1)).ignoreExceptions(HttpClientErrorException.class).build()).build());
  client=new AccountPostingClient(builder,factory,"banco-legacy-account-service");
 }
 String result(FinancialOperationRequest request)throws Exception{return new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(PaymentFinancialTests.receipt(request));}
 @Test void relaysTechnicalTokenIdempotencyKeyAndValidatesReceipt()throws Exception{
  server.expect(requestTo(URL)).andExpect(header("Authorization","Bearer signed")).andExpect(header("Idempotency-Key","key"))
   .andExpect(method(HttpMethod.POST)).andRespond(withSuccess(result(request),MediaType.APPLICATION_JSON));
  assertThat(client.post("Bearer signed","key",request).operationId()).isEqualTo(request.operationId());server.verify();
 }
 @Test void rejectsBusinessErrorsWithoutOpeningAvailabilityCircuit(){
  server.expect(times(4),requestTo(URL)).andRespond(withStatus(HttpStatus.CONFLICT).body("{\"code\":\"ACCOUNT_CLOSED\"}").contentType(MediaType.APPLICATION_JSON));
  for(int i=0;i<4;i++)assertThatThrownBy(()->client.post("Bearer signed","key",request)).hasMessageContaining("ACCOUNT_CLOSED");
  assertThat(factory.getCircuitBreakerRegistry().circuitBreaker("accountPosting").getState()).isEqualTo(CircuitBreaker.State.CLOSED);server.verify();
 }
 @Test void failuresOpenCircuitThenHealthyProbeRecovers()throws Exception{
  server.expect(times(3),requestTo(URL)).andRespond(withServerError());
  server.expect(requestTo(URL)).andRespond(withSuccess(result(request),MediaType.APPLICATION_JSON));
  for(int i=0;i<4;i++)assertThatThrownBy(()->client.post("Bearer signed","key",request)).hasMessageContaining("ACCOUNT_UNAVAILABLE");
  var circuit=factory.getCircuitBreakerRegistry().circuitBreaker("accountPosting");
  assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);
  circuit.transitionToHalfOpenState();
  assertThat(client.post("Bearer signed","key",request).status()).isEqualTo("COMPLETED");
  assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);server.verify();
 }
 @Test void emptyAndMismatchedResponsesAreUnavailable()throws Exception{
  server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NO_CONTENT));
  server.expect(requestTo(URL)).andRespond(withSuccess(result(new FinancialOperationRequest(UUID.randomUUID(),"DEPOSIT",null,101L,new BigDecimal("10"))),MediaType.APPLICATION_JSON));
  for(int i=0;i<2;i++)assertThatThrownBy(()->client.post("Bearer signed","key",request)).hasMessageContaining("ACCOUNT_UNAVAILABLE");
  server.verify();
 }
 @Test void networkTimeoutNeverProducesReceipt(){
  server.expect(requestTo(URL)).andRespond(withException(new java.net.SocketTimeoutException("timeout")));
  assertThatThrownBy(()->client.post("Bearer signed","key",request)).hasMessageContaining("ACCOUNT_UNAVAILABLE");server.verify();
 }
}
