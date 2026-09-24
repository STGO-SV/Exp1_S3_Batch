package com.duoc.banco_legacy.anomaly.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@JdbcTest
@Import(ProcessedAnomalyEventRepository.class)
class ProcessedAnomalyEventRepositoryTests {

    @Autowired ProcessedAnomalyEventRepository repository;
    @Autowired JdbcClient jdbcClient;

    @BeforeEach
    void clean() {
        jdbcClient.sql("DELETE FROM processed_anomaly_event").update();
    }

    @Test
    void conservaPayloadCompletoYMetadatosKafka() {
        AnomalousTransactionEvent event = event(UUID.randomUUID(), 501L);

        assertThat(repository.save(event, "banco.transacciones.anomalas.v1", 2, 47L, "consumer-a"))
                .isTrue();

        Map<String, Object> row = jdbcClient.sql("SELECT * FROM processed_anomaly_event")
                .query().singleRow();
        assertThat(row.get("event_id").toString()).isEqualTo(event.eventId().toString());
        assertThat(row.get("correlation_id")).isEqualTo(event.correlationId());
        assertThat(((Number) row.get("event_version")).intValue()).isEqualTo(event.eventVersion());
        assertThat(row.get("occurred_at")).isNotNull();
        assertThat(((Number) row.get("transaction_id")).longValue()).isEqualTo(501L);
        assertThat(row.get("transaction_date").toString()).isEqualTo("2026-09-22");
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("4500.25");
        assertThat(row.get("transaction_type")).isEqualTo("DEBIT");
        assertThat(row.get("anomaly_reason")).isEqualTo("AMOUNT_ABOVE_2000");
        assertThat(row.get("topic")).isEqualTo("banco.transacciones.anomalas.v1");
        assertThat(((Number) row.get("partition_id")).intValue()).isEqualTo(2);
        assertThat(((Number) row.get("offset_value")).longValue()).isEqualTo(47L);
        assertThat(row.get("consumer_instance")).isEqualTo("consumer-a");
        assertThat(row.get("processed_at")).isNotNull();
    }

    @Test
    void mismoEventIdDosVecesProduceUnaSolaFila() {
        AnomalousTransactionEvent event = event(UUID.randomUUID(), 502L);

        assertThat(repository.save(event, "topic", 0, 1, "consumer-a")).isTrue();
        assertThat(repository.save(event, "topic", 0, 2, "consumer-a")).isFalse();
        assertThat(count()).isOne();
    }

    @Test
    void distintoEventIdMismaTransaccionProduceUnaSolaFila() {
        assertThat(repository.save(event(UUID.randomUUID(), 503L), "topic", 0, 1, "consumer-a")).isTrue();
        assertThat(repository.save(event(UUID.randomUUID(), 503L), "topic", 0, 2, "consumer-a")).isFalse();
        assertThat(count()).isOne();
    }

    @Test
    void errorRealDeBaseDeDatosNoSeInterpretaComoDuplicado() {
        AnomalousTransactionEvent invalid = event(UUID.randomUUID(), null);

        assertThatThrownBy(() -> repository.save(invalid, "topic", 0, 1, "consumer-a"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count()).isZero();
    }

    private long count() {
        return jdbcClient.sql("SELECT COUNT(*) FROM processed_anomaly_event")
                .query(Long.class).single();
    }

    private AnomalousTransactionEvent event(UUID eventId, Long transactionId) {
        return new AnomalousTransactionEvent(eventId, "transaccionesDiariasJob:42", 1,
                Instant.parse("2026-09-22T15:30:00Z"), transactionId,
                LocalDate.parse("2026-09-22"), new BigDecimal("4500.25"),
                "DEBIT", "AMOUNT_ABOVE_2000");
    }
}
