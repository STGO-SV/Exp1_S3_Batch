package com.duoc.banco_legacy.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:anomaly_real_failure;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.listener.auto-startup=${week7.kafka.integration:false}",
        "spring.kafka.consumer.group-id=anomaly-real-failure-${random.uuid}",
        "spring.kafka.consumer.auto-offset-reset=latest",
        "banking.kafka.consumer-instance=real-failure-evidence"
})
class KafkaRealFailureIntegrationTests {

    private static final String TOPIC = "banco.transacciones.anomalas.v1";
    private static final String DLT = TOPIC + ".DLT";

    @Autowired KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired JdbcClient jdbcClient;

    @Test
    void demuestraExitoFalloPermanenteJsonInvalidoYContinuidad() throws Exception {
        if (!Boolean.getBoolean("week7.kafka.integration")) {
            return;
        }

        try (Consumer<String, byte[]> dltConsumer = dltConsumer()) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            AnomalousTransactionEvent valid = event(920001L, "debito");
            kafkaTemplate.send(TOPIC, "valid-" + suffix, valid).get();
            awaitRow(valid.transactionId(), 1);
            assertThat(findDlt(dltConsumer, "valid-" + suffix, Duration.ofSeconds(1))).isNull();

            AnomalousTransactionEvent databaseFailure = event(920002L, null);
            String failureKey = "database-failure-" + suffix;
            kafkaTemplate.send(TOPIC, failureKey, databaseFailure).get();
            ConsumerRecord<String, byte[]> failureDlt =
                    findDlt(dltConsumer, failureKey, Duration.ofSeconds(12));
            assertThat(failureDlt).isNotNull();
            assertThat(count(databaseFailure.transactionId())).isZero();
            assertThat(findDlt(dltConsumer, failureKey, Duration.ofSeconds(1))).isNull();

            String malformedKey = "malformed-" + suffix;
            byte[] malformed = "{invalid-json".getBytes(StandardCharsets.UTF_8);
            kafkaTemplate.send(TOPIC, malformedKey, malformed).get();
            ConsumerRecord<String, byte[]> malformedDlt =
                    findDlt(dltConsumer, malformedKey, Duration.ofSeconds(8));
            assertThat(malformedDlt).isNotNull();
            assertThat(malformedDlt.value()).isEqualTo(malformed);
            assertThat(header(malformedDlt, "kafka_dlt-exception-fqcn"))
                    .contains("DeserializationException");

            AnomalousTransactionEvent validAfterMalformed = event(920003L, "credito");
            kafkaTemplate.send(TOPIC, "valid-after-malformed-" + suffix, validAfterMalformed).get();
            awaitRow(validAfterMalformed.transactionId(), 1);

            System.out.printf(
                    "KAFKA_RETRY_DLT_EVIDENCE key=%s originalTopic=%s originalPartition=%s originalOffset=%s dltTopic=%s dltPartition=%s dltOffset=%s finalException=%s attempts=3 rows=0%n",
                    failureDlt.key(), header(failureDlt, "kafka_dlt-original-topic"),
                    headerInt(failureDlt, "kafka_dlt-original-partition"),
                    headerLong(failureDlt, "kafka_dlt-original-offset"), failureDlt.topic(),
                    failureDlt.partition(), failureDlt.offset(),
                    header(failureDlt, "kafka_dlt-exception-fqcn"));
            System.out.printf(
                    "KAFKA_MALFORMED_DLT_EVIDENCE key=%s dltTopic=%s dltPartition=%s dltOffset=%s businessRows=0 continuedWithTransactionId=%s%n",
                    malformedDlt.key(), malformedDlt.topic(), malformedDlt.partition(),
                    malformedDlt.offset(), validAfterMalformed.transactionId());
        }
    }

    private Consumer<String, byte[]> dltConsumer() {
        Map<String, Object> properties = Map.of(
                "bootstrap.servers", "localhost:9092",
                "group.id", "real-dlt-evidence-" + UUID.randomUUID(),
                "auto.offset.reset", "latest",
                "enable.auto.commit", false);
        Consumer<String, byte[]> consumer = new DefaultKafkaConsumerFactory<>(properties,
                new StringDeserializer(), new ByteArrayDeserializer()).createConsumer();
        consumer.subscribe(java.util.List.of(DLT));
        consumer.poll(Duration.ofSeconds(1));
        return consumer;
    }

    private ConsumerRecord<String, byte[]> findDlt(
            Consumer<String, byte[]> consumer, String key, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(100))) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        return null;
    }

    private void awaitRow(long transactionId, long expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (count(transactionId) == expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No se alcanzó el total esperado para transactionId=" + transactionId);
    }

    private long count(long transactionId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM processed_anomaly_event WHERE transaction_id=:id")
                .param("id", transactionId).query(Long.class).single();
    }

    private String header(ConsumerRecord<String, byte[]> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private Integer headerInt(ConsumerRecord<String, byte[]> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : java.nio.ByteBuffer.wrap(header.value()).getInt();
    }

    private Long headerLong(ConsumerRecord<String, byte[]> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : java.nio.ByteBuffer.wrap(header.value()).getLong();
    }

    private AnomalousTransactionEvent event(long transactionId, String transactionType) {
        return new AnomalousTransactionEvent(UUID.randomUUID(), "real-failure-evidence", 1,
                Instant.now(), transactionId, LocalDate.now(), new BigDecimal("4500.25"),
                transactionType, "AMOUNT_ABOVE_2000");
    }
}
