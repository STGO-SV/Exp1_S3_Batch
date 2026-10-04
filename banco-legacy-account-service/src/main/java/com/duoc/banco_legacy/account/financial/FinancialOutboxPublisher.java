package com.duoc.banco_legacy.account.financial;
import com.duoc.banco_legacy.core.event.FinancialOperationCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
@Component
@ConditionalOnProperty(name="banking.kafka.enabled",havingValue="true",matchIfMissing=true)
public class FinancialOutboxPublisher {
 private record Pending(UUID id,String payload){}
 private final JdbcClient jdbc;private final ObjectMapper json;private final KafkaTemplate<String,Object> kafka;private final String topic;
 public FinancialOutboxPublisher(JdbcClient jdbc,ObjectMapper json,KafkaTemplate<String,Object> kafka,
  @Value("${banking.kafka.financial-topic:banco.operaciones.completadas.v1}")String topic){
  this.jdbc=jdbc;this.json=json;this.kafka=kafka;this.topic=topic;
 }
 @Scheduled(fixedDelayString="${banking.kafka.outbox-delay-ms:2000}")
 public void publish() {
  var pending=jdbc.sql("SELECT event_id,payload FROM eft_financial_outbox WHERE status='PENDING' ORDER BY event_id LIMIT 100")
   .query((rs,n)->new Pending(UUID.fromString(rs.getString(1)),rs.getString(2))).list();
  for(var row:pending) {
   try {
    var event=json.readValue(row.payload(),FinancialOperationCompletedEvent.class);event.validate();
    kafka.send(topic,event.result().operationId().toString(),event).get(5,TimeUnit.SECONDS);
    jdbc.sql("UPDATE eft_financial_outbox SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP,attempts=attempts+1,last_error=NULL WHERE event_id=:id AND status='PENDING'")
     .param("id",row.id()).update();
   } catch(Exception failure) {
    if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
    jdbc.sql("UPDATE eft_financial_outbox SET attempts=attempts+1,last_error=:error WHERE event_id=:id AND status='PENDING'")
     .param("id",row.id()).param("error",failure.getClass().getSimpleName()).update();
   }
  }
 }
}
