package com.duoc.banco_legacy_batch.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@SpringBootTest(properties = {
        "banking.kafka.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:kafka_real;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class KafkaRealOutboxIntegrationTests {

    private static final String TOPIC = "banco.transacciones.anomalas.v1";

    @Autowired JdbcClient jdbcClient;
    @Autowired AnomalyOutboxRepository repository;
    @Autowired KafkaTemplate<String, AnomalousTransactionEvent> kafkaTemplate;

    @Test
    void publicaEventoRealConKeyJsonYConfirmacionPersistida() throws Exception {
        if (!Boolean.getBoolean("week7.kafka.integration")) {
            return;
        }

        UUID eventId = UUID.randomUUID();
        long transactionId = 970000L + Math.abs(eventId.hashCode() % 10000);
        insertPending(eventId, transactionId);
        var properties = new AnomalyKafkaProperties();
        properties.setAnomalyTopic(TOPIC);
        properties.setPublishRetries(3);
        properties.setPublishBackoff(100);
        var publisher = new AnomalyOutboxPublisher(repository, kafkaTemplate, properties);

        try (var consumer = consumer()) {
            consumer.subscribe(List.of(TOPIC));
            consumer.poll(Duration.ofMillis(500));

            publisher.publishPending();

            ConsumerRecord<String, String> received = findEvent(consumer, eventId);
            JsonNode json = new ObjectMapper().findAndRegisterModules().readTree(received.value());
            Map<String, Object> row = jdbcClient.sql("""
                            SELECT status, publish_attempts, published_at, last_error
                            FROM anomaly_event_outbox WHERE event_id = :eventId
                            """)
                    .param("eventId", eventId).query().singleRow();

            assertThat(received.key()).isEqualTo(Long.toString(transactionId));
            assertThat(json.get("eventId").asText()).isEqualTo(eventId.toString());
            assertThat(json.get("transactionId").asLong()).isEqualTo(transactionId);
            assertThat(json.has("status")).isFalse();
            assertThat(json.has("publishAttempts")).isFalse();
            assertThat(json.has("lastError")).isFalse();
            assertThat(row.get("status")).isEqualTo("PUBLISHED");
            assertThat(row.get("published_at")).isNotNull();
            assertThat(((Number) row.get("publish_attempts")).intValue()).isZero();
            assertThat(row.get("last_error")).isNull();

            System.out.printf(
                    "KAFKA_EVIDENCE eventId=%s transactionId=%d key=%s partition=%d offset=%d publishedAt=%s%n",
                    eventId, transactionId, received.key(), received.partition(), received.offset(),
                    row.get("published_at"));
        }
    }

    @Test
    void conservaPendingAnteBrokerInaccesibleYRecuperaAlVolverLaConectividad() throws Exception {
        if (!Boolean.getBoolean("week7.kafka.integration")) {
            return;
        }

        UUID eventId = UUID.randomUUID();
        long transactionId = 980000L + Math.abs(eventId.hashCode() % 10000);
        insertPending(eventId, transactionId);
        var publisherProperties = new AnomalyKafkaProperties();
        publisherProperties.setAnomalyTopic(TOPIC);
        publisherProperties.setPublishRetries(3);
        publisherProperties.setPublishBackoff(0);

        var unavailableFactory = unavailableProducerFactory();
        try {
            var unavailableTemplate = new KafkaTemplate<String, AnomalousTransactionEvent>(unavailableFactory);
            new AnomalyOutboxPublisher(repository, unavailableTemplate, publisherProperties).publishPending();
        } finally {
            unavailableFactory.destroy();
        }

        Map<String, Object> failedRow = outboxRow(eventId);
        assertThat(failedRow.get("status")).isEqualTo("PENDING");
        assertThat(((Number) failedRow.get("publish_attempts")).intValue()).isEqualTo(3);
        assertThat(failedRow.get("last_error")).isNotNull();
        assertThat(failedRow.get("published_at")).isNull();

        try (var consumer = consumer()) {
            consumer.subscribe(List.of(TOPIC));
            consumer.poll(Duration.ofMillis(500));

            new AnomalyOutboxPublisher(repository, kafkaTemplate, publisherProperties).publishPending();

            ConsumerRecord<String, String> recovered = findEvent(consumer, eventId);
            Map<String, Object> recoveredRow = outboxRow(eventId);
            assertThat(recovered.key()).isEqualTo(Long.toString(transactionId));
            assertThat(recoveredRow.get("status")).isEqualTo("PUBLISHED");
            assertThat(((Number) recoveredRow.get("publish_attempts")).intValue()).isEqualTo(3);
            assertThat(recoveredRow.get("last_error")).isNull();
            assertThat(recoveredRow.get("published_at")).isNotNull();

            System.out.printf(
                    "KAFKA_RECOVERY_EVIDENCE eventId=%s transactionId=%d attempts=%s key=%s partition=%d offset=%d publishedAt=%s%n",
                    eventId, transactionId, recoveredRow.get("publish_attempts"), recovered.key(),
                    recovered.partition(), recovered.offset(), recoveredRow.get("published_at"));
        }
    }

    private KafkaConsumer<String, String> consumer() {
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "week7-publisher-evidence-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }

    private DefaultKafkaProducerFactory<String, AnomalousTransactionEvent> unavailableProducerFactory() {
        Map<String, Object> properties = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:19093",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1000,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 500,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 500);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    private Map<String, Object> outboxRow(UUID eventId) {
        return jdbcClient.sql("""
                        SELECT status, publish_attempts, published_at, last_error
                        FROM anomaly_event_outbox WHERE event_id = :eventId
                        """)
                .param("eventId", eventId).query().singleRow();
    }

    private ConsumerRecord<String, String> findEvent(KafkaConsumer<String, String> consumer,
                                                      UUID eventId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (record.value().contains(eventId.toString())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No se consumió el evento Kafka " + eventId);
    }

    private void insertPending(UUID eventId, long transactionId) {
        jdbcClient.sql("""
                        INSERT INTO anomaly_event_outbox
                            (event_id, correlation_id, event_version, occurred_at,
                             transaction_id, transaction_date, amount, transaction_type,
                             anomaly_reason, status, publish_attempts)
                        VALUES
                            (:eventId, 'transaccionesDiariasJob:kafka-real', 1, CURRENT_TIMESTAMP,
                             :transactionId, DATE '2026-09-22', 4500.25, 'debito',
                             'AMOUNT_ABOVE_2000', 'PENDING', 0)
                        """)
                .param("eventId", eventId)
                .param("transactionId", transactionId)
                .update();
    }
}
