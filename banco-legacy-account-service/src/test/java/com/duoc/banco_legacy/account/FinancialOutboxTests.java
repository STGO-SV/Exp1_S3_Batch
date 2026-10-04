package com.duoc.banco_legacy.account;
import com.duoc.banco_legacy.account.financial.*;
import com.duoc.banco_legacy.account.registry.*;
import com.duoc.banco_legacy.core.event.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:financial_outbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:schema-eft-account.sql"})
class FinancialOutboxTests extends JwtTestSupport {
 @Autowired JdbcTemplate jdbc;@Autowired JdbcClient client;@Autowired ObjectMapper json;@Autowired AccountPostingService posting;
 @Autowired AccountRegistryService registry;@MockBean CustomerRegistryClient customers;
 @Test void failureKeepsPendingAndAcknowledgedRetryPublishesExactlyOneStoredEvent(){
  jdbc.execute("CREATE TABLE IF NOT EXISTS interes_procesado(cuenta_id BIGINT)");
  jdbc.execute("CREATE TABLE IF NOT EXISTS movimiento_anual_procesado(cuenta_id BIGINT)");
  registry.open(301,"ahorro",List.of(UUID.randomUUID()));
  posting.post("operator","deposit",new FinancialOperationRequest(UUID.randomUUID(),"DEPOSIT",null,301L,new BigDecimal("10")));
  @SuppressWarnings("unchecked") KafkaTemplate<String,Object> kafka=mock(KafkaTemplate.class);
  when(kafka.send(anyString(),anyString(),any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));
  var publisher=new FinancialOutboxPublisher(client,json,kafka,"banco.operaciones.completadas.v1");
  publisher.publish();
  assertThat(jdbc.queryForObject("SELECT status FROM eft_financial_outbox",String.class)).isEqualTo("PENDING");
  assertThat(jdbc.queryForObject("SELECT attempts FROM eft_financial_outbox",Integer.class)).isEqualTo(1);
  when(kafka.send(anyString(),anyString(),any())).thenReturn(CompletableFuture.completedFuture(null));
  publisher.publish();publisher.publish();
  assertThat(jdbc.queryForObject("SELECT status FROM eft_financial_outbox",String.class)).isEqualTo("PUBLISHED");
  assertThat(jdbc.queryForObject("SELECT attempts FROM eft_financial_outbox",Integer.class)).isEqualTo(2);
  verify(kafka,times(2)).send(eq("banco.operaciones.completadas.v1"),anyString(),isA(FinancialOperationCompletedEvent.class));
  assertThat(registry.get(301).balance()).isEqualByComparingTo("10");
 }
}
