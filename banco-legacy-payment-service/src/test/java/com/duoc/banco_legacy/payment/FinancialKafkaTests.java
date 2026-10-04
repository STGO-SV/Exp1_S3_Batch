package com.duoc.banco_legacy.payment;
import com.duoc.banco_legacy.payment.domain.*;
import com.duoc.banco_legacy.core.event.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.kafka.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.kafka.common.serialization.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:financial_kafka;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
 "banking.kafka.enabled=true","spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
 "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
 "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
 "spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer",
 "spring.kafka.consumer.properties.spring.json.value.default.type=com.duoc.banco_legacy.core.event.FinancialOperationCompletedEvent",
 "spring.kafka.consumer.properties.spring.json.trusted.packages=com.duoc.banco_legacy.core.event",
 "spring.kafka.consumer.properties.spring.json.use.type.headers=false",
 "spring.kafka.consumer.enable-auto-commit=false","spring.kafka.consumer.auto-offset-reset=earliest",
 "spring.kafka.listener.ack-mode=record","spring.kafka.producer.acks=all",
 "spring.kafka.producer.properties.spring.json.add.type.headers=false"
})
@EmbeddedKafka(partitions=3,topics={"banco.operaciones.completadas.v1","banco.operaciones.completadas.v1.DLT"})
class FinancialKafkaTests extends JwtTestSupport {
 @Autowired PaymentStore store;@Autowired JdbcTemplate jdbc;@Autowired KafkaTemplate<String,Object> kafka;@Autowired EmbeddedKafkaBroker broker;
 @Test void brokerDeliversCompletedEventAndDeduplicatesProjection()throws Exception{
  var request=new FinancialOperationRequest(UUID.randomUUID(),"DEPOSIT",null,101L,new BigDecimal("10"));
  var operation=store.prepare("operator","embedded",request).operation();
  var event=new FinancialOperationCompletedEvent(UUID.randomUUID(),1,"operator",request.fingerprint(),PaymentFinancialTests.receipt(request));
  kafka.send("banco.operaciones.completadas.v1",request.operationId().toString(),event).get(10,java.util.concurrent.TimeUnit.SECONDS);
  await(()->store.get("operator",operation.operationId()).status().equals("COMPLETED"));
  kafka.send("banco.operaciones.completadas.v1",request.operationId().toString(),event).get(10,java.util.concurrent.TimeUnit.SECONDS);
  Thread.sleep(500);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_payment_event_audit WHERE event_id=?",Integer.class,event.eventId())).isEqualTo(1);
 }
 @Test void poisonJsonGoesToDltAndNextValidEventStillGetsAudited()throws Exception{
  var properties=KafkaTestUtils.consumerProps("financial-dlt-"+UUID.randomUUID(),"false",broker);
  try(var consumer=new DefaultKafkaConsumerFactory<>(properties,new StringDeserializer(),new ByteArrayDeserializer()).createConsumer()){
   broker.consumeFromAnEmbeddedTopic(consumer,"banco.operaciones.completadas.v1.DLT");
   kafka.send("banco.operaciones.completadas.v1","poison","{broken".getBytes(java.nio.charset.StandardCharsets.UTF_8)).get(10,java.util.concurrent.TimeUnit.SECONDS);
   var records=KafkaTestUtils.getRecords(consumer,Duration.ofSeconds(15),1);
   assertThat(records.count()).isPositive();
   assertThat(java.util.stream.StreamSupport.stream(records.spliterator(),false).anyMatch(record->record.key().equals("poison"))).isTrue();
  }
  var request=new FinancialOperationRequest(UUID.randomUUID(),"PAYMENT",101L,null,new BigDecimal("1"));
  var event=new FinancialOperationCompletedEvent(UUID.randomUUID(),1,"operator",request.fingerprint(),PaymentFinancialTests.receipt(request));
  kafka.send("banco.operaciones.completadas.v1",request.operationId().toString(),event).get(10,java.util.concurrent.TimeUnit.SECONDS);
  await(()->jdbc.queryForObject("SELECT COUNT(*) FROM eft_payment_event_audit WHERE event_id=?",Integer.class,event.eventId())==1);
 }
 static void await(java.util.function.BooleanSupplier condition)throws Exception{
  long deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();
  while(System.nanoTime()<deadline){if(condition.getAsBoolean())return;Thread.sleep(100);}
  assertThat(condition.getAsBoolean()).isTrue();
 }
}
