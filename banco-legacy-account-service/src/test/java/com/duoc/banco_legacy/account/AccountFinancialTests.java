package com.duoc.banco_legacy.account;
import com.duoc.banco_legacy.account.registry.*;
import com.duoc.banco_legacy.account.financial.*;
import com.duoc.banco_legacy.core.event.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:financial_account;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:schema-eft-account.sql"})
@AutoConfigureMockMvc
class AccountFinancialTests extends JwtTestSupport {
 @Autowired AccountPostingService posting;@Autowired AccountRegistryService registry;@Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;
 @MockBean CustomerRegistryClient customers;
 @BeforeEach void setup(){
  jdbc.execute("CREATE TABLE IF NOT EXISTS interes_procesado(cuenta_id BIGINT)");
  jdbc.execute("CREATE TABLE IF NOT EXISTS movimiento_anual_procesado(cuenta_id BIGINT)");
  for(String table:List.of("eft_financial_outbox","eft_account_posting","eft_account_balance","eft_account_holder","eft_account"))jdbc.update("DELETE FROM "+table);
  registry.open(101,"ahorro",List.of(UUID.randomUUID()));registry.open(102,"ahorro",List.of(UUID.randomUUID()));
 }
 FinancialOperationRequest request(String type,Long source,Long target,String amount){
  return new FinancialOperationRequest(UUID.randomUUID(),type,source,target,new BigDecimal(amount));
 }
 FinancialOperationResult post(String key,String type,Long source,Long target,String amount){return posting.post("operator",key,request(type,source,target,amount)).result();}
 @Test void depositTransferAndPaymentKeepIndependentOperationalBalances(){
  assertThat(registry.get(101).balance()).isEqualByComparingTo("0");
  post("deposit","DEPOSIT",null,101L,"100");
  var transfer=post("transfer","TRANSFER",101L,102L,"40");
  assertThat(transfer.sourceBalanceAfter()).isEqualByComparingTo("60");
  assertThat(transfer.targetBalanceAfter()).isEqualByComparingTo("40");
  post("payment","PAYMENT",102L,null,"10");
  assertThat(registry.get(102).balance()).isEqualByComparingTo("30");
  assertThat(registry.get(101).version()).isEqualTo(2);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_financial_outbox",Integer.class)).isEqualTo(3);
 }
 @Test void replayReturnsOriginalReceiptWithoutChangingBalanceOrVersion(){
  var request=request("DEPOSIT",null,101L,"20.00");var first=posting.post("operator","key",request);
  var replay=posting.post("operator","key",request("DEPOSIT",null,101L,"20.0"));
  assertThat(replay.created()).isFalse();assertThat(replay.result()).isEqualTo(first.result());
  assertThat(registry.get(101).balance()).isEqualByComparingTo("20");assertThat(registry.get(101).version()).isEqualTo(1);
  assertThatThrownBy(()->posting.post("operator","key",request("DEPOSIT",null,101L,"21"))).isInstanceOf(RegistryException.class).satisfies(error->assertThat(((RegistryException)error).code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
 }
 @Test void failedTransferRollsBackDebitReceiptAndOutbox(){
  post("seed","DEPOSIT",null,101L,"100");registry.close(102,0);
  assertThatThrownBy(()->post("bad","TRANSFER",101L,102L,"50")).satisfies(error->assertThat(((RegistryException)error).code()).isEqualTo("ACCOUNT_CLOSED"));
  assertThat(registry.get(101).balance()).isEqualByComparingTo("100");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_account_posting",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_financial_outbox",Integer.class)).isEqualTo(1);
 }
 @Test void insufficientBalanceAndMissingAccountDoNotProduceFinancialRecords(){
  assertThatThrownBy(()->post("pay","PAYMENT",101L,null,"1")).satisfies(error->assertThat(((RegistryException)error).code()).isEqualTo("INSUFFICIENT_BALANCE"));
  assertThatThrownBy(()->post("absent","TRANSFER",101L,999L,"1")).satisfies(error->assertThat(((RegistryException)error).code()).isEqualTo("ACCOUNT_NOT_FOUND"));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_account_posting",Integer.class)).isZero();
 }
 @Test void closedAccountRetainsBalanceRejectsCreditDebitAndMaintenance(){
  post("seed","DEPOSIT",null,101L,"5");registry.close(101,1);
  assertThat(registry.get(101).status()).isEqualTo("CLOSED");assertThat(registry.get(101).balance()).isEqualByComparingTo("5");
  assertThatThrownBy(()->post("credit","DEPOSIT",null,101L,"1")).satisfies(error->assertThat(((RegistryException)error).code()).isEqualTo("ACCOUNT_CLOSED"));
  assertThatThrownBy(()->post("debit","PAYMENT",101L,null,"1")).satisfies(error->assertThat(((RegistryException)error).code()).isEqualTo("ACCOUNT_CLOSED"));
  assertThatThrownBy(()->registry.update(101,"prestamo",2)).satisfies(error->assertThat(((RegistryException)error).code()).isEqualTo("ACCOUNT_STATE_CONFLICT"));
 }
 @Test void invalidAmountsSameAccountAndKeysAreRejected(){
  for(String amount:List.of("0","-1","0.001","100000000000000000"))
   assertThatThrownBy(()->post("invalid","DEPOSIT",null,101L,amount)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->post("same","TRANSFER",101L,101L,"1")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->post("spaces key","DEPOSIT",null,101L,"1")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void simultaneousDebitsCannotOverdraw()throws Exception {
  post("seed","DEPOSIT",null,101L,"100");
  try(var pool=Executors.newFixedThreadPool(2)){
   var start=new CountDownLatch(1);var tasks=new ArrayList<Future<Boolean>>();
   for(int i=0;i<2;i++){String key="debit"+i;tasks.add(pool.submit(()->{start.await();try{post(key,"PAYMENT",101L,null,"80");return true;}catch(RegistryException rejected){return false;}}));}
   start.countDown();int completed=0;for(var task:tasks)if(task.get(10,TimeUnit.SECONDS))completed++;
   assertThat(completed).isEqualTo(1);
  }
  assertThat(registry.get(101).balance()).isEqualByComparingTo("20");
 }
 @Test void simultaneousSameKeyHasOnlyOneEffect()throws Exception{
  try(var pool=Executors.newFixedThreadPool(2)){
   var start=new CountDownLatch(1);
   Callable<FinancialOperationResult> task=()->{start.await();return post("same","DEPOSIT",null,101L,"10");};
   var first=pool.submit(task);var second=pool.submit(task);start.countDown();
   assertThat(first.get(10,TimeUnit.SECONDS)).isEqualTo(second.get(10,TimeUnit.SECONDS));
  }
  assertThat(registry.get(101).balance()).isEqualByComparingTo("10");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_financial_outbox",Integer.class)).isEqualTo(1);
 }
 @Test void oppositeTransfersLockInDeterministicOrderAndConserveBalance()throws Exception{
  post("a","DEPOSIT",null,101L,"100");post("b","DEPOSIT",null,102L,"100");
  try(var pool=Executors.newFixedThreadPool(2)){
   var a=pool.submit(()->post("ab","TRANSFER",101L,102L,"40"));var b=pool.submit(()->post("ba","TRANSFER",102L,101L,"30"));
   a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
  }
  assertThat(registry.get(101).balance().add(registry.get(102).balance())).isEqualByComparingTo("200");
 }
 @Test void financialApiRequiresSignedTokenAndFinancialScope()throws Exception{
  String body="{\"operationId\":\""+UUID.randomUUID()+"\",\"type\":\"DEPOSIT\",\"targetAccountId\":101,\"amount\":10}";
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/accounts/postings").header("Idempotency-Key","api").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/accounts/postings").with(bearer("DOMAIN_OPERATOR","accounts.write")).header("Idempotency-Key","api").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/accounts/postings").with(bearer("PAYMENT_OPERATOR","accounts.post")).header("Idempotency-Key","api").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
 }
 @Test void actorCannotReadAnotherActorsReceipt(){
  var result=post("key","DEPOSIT",null,101L,"1");
  assertThatThrownBy(()->posting.get("other",result.operationId())).satisfies(error->assertThat(((RegistryException)error).code()).isEqualTo("OPERATION_NOT_FOUND"));
 }

 @Test void schemaUpgradeBackfillsZeroAndNeverOverwritesExistingOperationalBalance(){
  post("seed","DEPOSIT",null,101L,"25");
  jdbc.update("INSERT INTO eft_account(account_id,account_type,status,version) VALUES (103,'ahorro','OPEN',0)");
  new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("schema-eft-account.sql")).execute(jdbc.getDataSource());
  assertThat(registry.get(103).balance()).isEqualByComparingTo("0");
  assertThat(registry.get(101).balance()).isEqualByComparingTo("25");
 }
 @Test void outboxPersistenceFailureRollsBackBalancesAndReceipt(){
  jdbc.execute("ALTER TABLE eft_financial_outbox ADD CONSTRAINT test_outbox_failure CHECK(status='PUBLISHED')");
  try {
   assertThatThrownBy(()->post("rollback","DEPOSIT",null,101L,"10")).isInstanceOf(org.springframework.dao.DataAccessException.class);
   assertThat(registry.get(101).balance()).isEqualByComparingTo("0");
   assertThat(registry.get(101).version()).isZero();
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_account_posting",Integer.class)).isZero();
  } finally { jdbc.execute("ALTER TABLE eft_financial_outbox DROP CONSTRAINT test_outbox_failure"); }
 }
}
