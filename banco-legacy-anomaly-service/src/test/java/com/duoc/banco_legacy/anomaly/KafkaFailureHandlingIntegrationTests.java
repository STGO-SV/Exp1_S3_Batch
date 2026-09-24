package com.duoc.banco_legacy.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duoc.banco_legacy.anomaly.repository.ProcessedAnomalyEventRepository;
import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import java.math.BigDecimal;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.group-id=anomaly-failure-${random.uuid}",
        "banking.kafka.consumer-instance=failure-test-consumer"
})
@EmbeddedKafka(partitions = 1, topics = {
        "banco.transacciones.anomalas.v1", "banco.transacciones.anomalas.v1.DLT"
})
class KafkaFailureHandlingIntegrationTests {

    private static final String TOPIC = "banco.transacciones.anomalas.v1";
    private static final String DLT = TOPIC + ".DLT";

    @Autowired KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired EmbeddedKafkaBroker broker;
    @MockBean ProcessedAnomalyEventRepository repository;

    private Consumer<String, byte[]> dltConsumer;

    @BeforeEach
    void setUp() {
        reset(repository);
        Map<String, Object> properties = KafkaTestUtils.consumerProps(
                "dlt-assertions-" + UUID.randomUUID(), "false", broker);
        dltConsumer = new DefaultKafkaConsumerFactory<>(properties,
                new StringDeserializer(), new ByteArrayDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(dltConsumer, DLT);
    }

    @AfterEach
    void tearDown() {
        dltConsumer.close();
    }

    @Test
    void errorTransitorioTieneTresIntentosYFinalmenteSeProcesaSinDlt() throws Exception {
        AnomalousTransactionEvent event = event(810001L);
        when(repository.save(any(), anyString(), anyInt(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("transient-1"))
                .thenThrow(new IllegalStateException("transient-2"))
                .thenReturn(true);

        kafkaTemplate.send(TOPIC, event.transactionId().toString(), event).get();

        verify(repository, timeout(8_000).times(3))
                .save(any(), anyString(), anyInt(), anyLong(), anyString());
        assertThat(findDlt(event.transactionId().toString(), Duration.ofSeconds(2))).isNull();
    }

    @Test
    void errorPermanenteTieneTresIntentosYUnaPublicacionDlt() throws Exception {
        AnomalousTransactionEvent event = event(810002L);
        when(repository.save(any(), anyString(), anyInt(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("permanent-database-error"));

        kafkaTemplate.send(TOPIC, event.transactionId().toString(), event).get();

        verify(repository, timeout(8_000).times(3))
                .save(any(), anyString(), anyInt(), anyLong(), anyString());
        ConsumerRecord<String, byte[]> dlt = findDlt(event.transactionId().toString(), Duration.ofSeconds(8));
        assertThat(dlt).isNotNull();
        assertThat(dlt.topic()).isEqualTo(DLT);
        assertThat(dlt.partition()).isZero();
        assertThat(header(dlt, "kafka_dlt-original-topic")).isEqualTo(TOPIC);
        assertThat(header(dlt, "kafka_dlt-exception-message"))
                .contains("permanent-database-error");
        assertThat(findDlt(event.transactionId().toString(), Duration.ofMillis(500))).isNull();
    }

    @Test
    void jsonInvalidoNoInvocaNegocioNiReintentaYLlegaDirectamenteADlt() throws Exception {
        String key = "malformed-810003";
        byte[] invalidJson = "{not-valid-json".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        kafkaTemplate.send(TOPIC, key, invalidJson).get();

        ConsumerRecord<String, byte[]> dlt = findDlt(key, Duration.ofSeconds(8));
        assertThat(dlt).isNotNull();
        assertThat(dlt.value()).isEqualTo(invalidJson);
        assertThat(header(dlt, "kafka_dlt-exception-fqcn"))
                .contains("DeserializationException");
        verify(repository, never()).save(any(), anyString(), anyInt(), anyLong(), anyString());

        AnomalousTransactionEvent valid = event(810004L);
        when(repository.save(any(), anyString(), anyInt(), anyLong(), anyString())).thenReturn(true);
        kafkaTemplate.send(TOPIC, valid.transactionId().toString(), valid).get();
        verify(repository, timeout(5_000).times(1))
                .save(any(), anyString(), anyInt(), anyLong(), anyString());
    }

    private ConsumerRecord<String, byte[]> findDlt(String key, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, byte[]> record : dltConsumer.poll(Duration.ofMillis(100))) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
        }
        return null;
    }

    private String header(ConsumerRecord<String, byte[]> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private AnomalousTransactionEvent event(long transactionId) {
        return new AnomalousTransactionEvent(UUID.randomUUID(), "failure-tests", 1,
                Instant.parse("2026-09-22T20:00:00Z"), transactionId,
                LocalDate.parse("2026-09-22"), new BigDecimal("4500.25"),
                "debito", "AMOUNT_ABOVE_2000");
    }
}
