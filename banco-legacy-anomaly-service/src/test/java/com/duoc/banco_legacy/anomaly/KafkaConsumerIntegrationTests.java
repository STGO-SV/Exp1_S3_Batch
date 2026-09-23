package com.duoc.banco_legacy.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import java.math.BigDecimal;
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
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:anomaly_kafka;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.group-id=anomaly-integration-${random.uuid}",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false",
        "banking.kafka.consumer-instance=embedded-consumer"
})
@EmbeddedKafka(partitions = 1, topics = "banco.transacciones.anomalas.v1")
class KafkaConsumerIntegrationTests {

    @Autowired KafkaTemplate<String, AnomalousTransactionEvent> kafkaTemplate;
    @Autowired JdbcClient jdbcClient;

    @Test
    void contratoJsonDelProductorEsConsumidoYPersistidoSinDuplicarEfecto() throws Exception {
        UUID eventId = UUID.randomUUID();
        long transactionId = 700000L + Math.abs(eventId.hashCode() % 10000);
        AnomalousTransactionEvent event = new AnomalousTransactionEvent(
                eventId, "transaccionesDiariasJob:embedded", 1,
                Instant.parse("2026-09-22T18:20:00Z"), transactionId,
                LocalDate.parse("2026-09-22"), new BigDecimal("9876.54"),
                "TRANSFER", "AMOUNT_ABOVE_2000");

        kafkaTemplate.send("banco.transacciones.anomalas.v1", Long.toString(transactionId), event).get();
        awaitCount(transactionId, 1);
        kafkaTemplate.send("banco.transacciones.anomalas.v1", Long.toString(transactionId), event).get();
        Thread.sleep(500);

        Map<String, Object> row = jdbcClient.sql("""
                        SELECT * FROM processed_anomaly_event WHERE transaction_id = :transactionId
                        """).param("transactionId", transactionId).query().singleRow();
        assertThat(count(transactionId)).isOne();
        assertThat(row.get("event_id").toString()).isEqualTo(eventId.toString());
        assertThat(row.get("correlation_id")).isEqualTo(event.correlationId());
        assertThat(((Number) row.get("event_version")).intValue()).isEqualTo(1);
        assertThat(row.get("occurred_at")).isNotNull();
        assertThat(row.get("transaction_date").toString()).isEqualTo("2026-09-22");
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("9876.54");
        assertThat(row.get("transaction_type")).isEqualTo("TRANSFER");
        assertThat(row.get("anomaly_reason")).isEqualTo("AMOUNT_ABOVE_2000");
        assertThat(row.get("topic")).isEqualTo("banco.transacciones.anomalas.v1");
        assertThat(((Number) row.get("partition_id")).intValue()).isZero();
        assertThat(((Number) row.get("offset_value")).longValue()).isGreaterThanOrEqualTo(0L);
        assertThat(row.get("consumer_instance")).isEqualTo("embedded-consumer");
    }

    private void awaitCount(long transactionId, long expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (count(transactionId) == expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("El consumidor no persistió transactionId=" + transactionId);
    }

    private long count(long transactionId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM processed_anomaly_event WHERE transaction_id=:id")
                .param("id", transactionId).query(Long.class).single();
    }
}
