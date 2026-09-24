package com.duoc.banco_legacy_batch.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AnomalyOutboxRepositoryTests {

    @Autowired AnomalyOutboxRepository repository;
    @Autowired JdbcClient jdbcClient;

    @Test
    void recuperaExclusivamenteEventosPendingYReconstruyeElContrato() {
        UUID pendingId = UUID.randomUUID();
        UUID publishedId = UUID.randomUUID();
        insert(pendingId, 3001L, "PENDING");
        insert(publishedId, 3002L, "PUBLISHED");

        assertThat(repository.findPending())
                .filteredOn(event -> event.eventId().equals(pendingId) || event.eventId().equals(publishedId))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.eventId()).isEqualTo(pendingId);
                    assertThat(event.correlationId()).isEqualTo("transaccionesDiariasJob:91");
                    assertThat(event.eventVersion()).isEqualTo(1);
                    assertThat(event.transactionId()).isEqualTo(3001L);
                    assertThat(event.transactionDate()).isEqualTo(LocalDate.of(2026, 9, 22));
                    assertThat(event.amount()).isEqualByComparingTo("2500.75");
                    assertThat(event.transactionType()).isEqualTo("debito");
                    assertThat(event.anomalyReason()).isEqualTo("AMOUNT_ABOVE_2000");
                });
    }

    @Test
    void marcaPublishedConFechaYLimpiaElError() {
        UUID eventId = UUID.randomUUID();
        insert(eventId, 3010L, "PENDING");
        repository.recordFailure(eventId, new IllegalStateException("fallo transitorio"));

        repository.markPublished(eventId);

        var row = row(eventId);
        assertThat(row.status()).isEqualTo("PUBLISHED");
        assertThat(row.publishedAt()).isNotNull();
        assertThat(row.attempts()).isEqualTo(1);
        assertThat(row.lastError()).isNull();
    }

    @Test
    void incrementaIntentosYRegistraElErrorManteniendoPending() {
        UUID eventId = UUID.randomUUID();
        insert(eventId, 3020L, "PENDING");

        repository.recordFailure(eventId, new IllegalArgumentException("broker no disponible"));

        var row = row(eventId);
        assertThat(row.status()).isEqualTo("PENDING");
        assertThat(row.publishedAt()).isNull();
        assertThat(row.attempts()).isEqualTo(1);
        assertThat(row.lastError()).contains("IllegalArgumentException", "broker no disponible");
    }

    private void insert(UUID eventId, long transactionId, String status) {
        jdbcClient.sql("""
                        INSERT INTO anomaly_event_outbox
                            (event_id, correlation_id, event_version, occurred_at,
                             transaction_id, transaction_date, amount, transaction_type,
                             anomaly_reason, status, publish_attempts, published_at)
                        VALUES
                            (:eventId, 'transaccionesDiariasJob:91', 1, CURRENT_TIMESTAMP,
                             :transactionId, DATE '2026-09-22', 2500.75, 'debito',
                             'AMOUNT_ABOVE_2000', :status, 0,
                             CASE WHEN :status = 'PUBLISHED' THEN CURRENT_TIMESTAMP ELSE NULL END)
                        """)
                .param("eventId", eventId)
                .param("transactionId", transactionId)
                .param("status", status)
                .update();
    }

    private OutboxState row(UUID eventId) {
        return jdbcClient.sql("""
                        SELECT status, publish_attempts, published_at, last_error
                        FROM anomaly_event_outbox WHERE event_id = :eventId
                        """)
                .param("eventId", eventId)
                .query((rs, rowNum) -> new OutboxState(
                        rs.getString("status"), rs.getInt("publish_attempts"),
                        rs.getTimestamp("published_at"), rs.getString("last_error")))
                .single();
    }

    private record OutboxState(String status, int attempts, java.sql.Timestamp publishedAt,
                               String lastError) {
    }
}
