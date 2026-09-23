package com.duoc.banco_legacy.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:anomaly_real;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.listener.auto-startup=${week7.kafka.integration:false}",
        "spring.kafka.consumer.group-id=anomaly-real-evidence-${random.uuid}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false",
        "banking.kafka.consumer-instance=real-consumer-evidence"
})
class KafkaRealConsumerIntegrationTests {

    @Autowired JdbcClient jdbcClient;
    @Autowired KafkaTemplate<String, AnomalousTransactionEvent> kafkaTemplate;

    @Test
    void consumeEventoRealDelBatchYRedeliveryNoDuplicaElEfecto() throws Exception {
        if (!Boolean.getBoolean("week7.kafka.integration")) {
            return;
        }
        UUID expectedEventId = UUID.fromString(System.getProperty("week7.expected.event-id"));
        Map<String, Object> row = awaitEvent(expectedEventId);

        assertThat(row.get("event_id").toString()).isEqualTo(expectedEventId.toString());
        assertThat(row.get("topic")).isEqualTo("banco.transacciones.anomalas.v1");
        assertThat(((Number) row.get("partition_id")).intValue()).isBetween(0, 2);
        assertThat(((Number) row.get("offset_value")).longValue()).isGreaterThanOrEqualTo(0L);
        assertThat(row.get("consumer_instance")).isEqualTo("real-consumer-evidence");

        AnomalousTransactionEvent redelivery = new AnomalousTransactionEvent(
                expectedEventId,
                row.get("correlation_id").toString(),
                ((Number) row.get("event_version")).intValue(),
                ((java.time.OffsetDateTime) row.get("occurred_at")).toInstant(),
                ((Number) row.get("transaction_id")).longValue(),
                ((Date) row.get("transaction_date")).toLocalDate(),
                (BigDecimal) row.get("amount"),
                row.get("transaction_type").toString(),
                row.get("anomaly_reason").toString());
        kafkaTemplate.send("banco.transacciones.anomalas.v1",
                redelivery.transactionId().toString(), redelivery).get();
        Thread.sleep(1000);

        assertThat(count(expectedEventId)).isOne();
        System.out.printf(
                "KAFKA_CONSUMER_EVIDENCE eventId=%s transactionId=%s amount=%s transactionDate=%s transactionType=%s topic=%s partition=%s offset=%s consumerInstance=%s rowsAfterRedelivery=1%n",
                expectedEventId, row.get("transaction_id"), row.get("amount"),
                row.get("transaction_date"), row.get("transaction_type"), row.get("topic"),
                row.get("partition_id"), row.get("offset_value"), row.get("consumer_instance"));
    }

    private Map<String, Object> awaitEvent(UUID eventId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (count(eventId) == 1) {
                return jdbcClient.sql("SELECT * FROM processed_anomaly_event WHERE event_id=:id")
                        .param("id", eventId).query().singleRow();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No se consumió el eventId real " + eventId);
    }

    private long count(UUID eventId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM processed_anomaly_event WHERE event_id=:id")
                .param("id", eventId).query(Long.class).single();
    }
}
