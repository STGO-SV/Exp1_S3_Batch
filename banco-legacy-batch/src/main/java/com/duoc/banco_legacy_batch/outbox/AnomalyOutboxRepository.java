package com.duoc.banco_legacy_batch.outbox;

import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AnomalyOutboxRepository {

    private static final int DEFAULT_BATCH_SIZE = 100;

    private final JdbcClient jdbcClient;
    private final Clock clock;

    @Autowired
    public AnomalyOutboxRepository(JdbcClient jdbcClient) {
        this(jdbcClient, Clock.systemUTC());
    }

    AnomalyOutboxRepository(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    public List<AnomalousTransactionEvent> findPending() {
        return jdbcClient.sql("""
                        SELECT event_id, correlation_id, event_version, occurred_at,
                               transaction_id, transaction_date, amount,
                               transaction_type, anomaly_reason
                        FROM anomaly_event_outbox
                        WHERE status = 'PENDING'
                        ORDER BY occurred_at, event_id
                        LIMIT :limit
                        """)
                .param("limit", DEFAULT_BATCH_SIZE)
                .query(this::mapEvent)
                .list();
    }

    public void markPublished(UUID eventId) {
        int updated = jdbcClient.sql("""
                        UPDATE anomaly_event_outbox
                        SET status = 'PUBLISHED', published_at = :publishedAt, last_error = NULL
                        WHERE event_id = :eventId AND status = 'PENDING'
                        """)
                .param("publishedAt", Instant.now(clock))
                .param("eventId", eventId)
                .update();
        requireSingleUpdate(updated, eventId, "marcar como PUBLISHED");
    }

    public void recordFailure(UUID eventId, Throwable failure) {
        String message = errorMessage(failure);
        int updated = jdbcClient.sql("""
                        UPDATE anomaly_event_outbox
                        SET publish_attempts = publish_attempts + 1, last_error = :lastError
                        WHERE event_id = :eventId AND status = 'PENDING'
                        """)
                .param("lastError", message)
                .param("eventId", eventId)
                .update();
        requireSingleUpdate(updated, eventId, "registrar el fallo");
    }

    private AnomalousTransactionEvent mapEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AnomalousTransactionEvent(
                resultSet.getObject("event_id", UUID.class),
                resultSet.getString("correlation_id"),
                resultSet.getInt("event_version"),
                resultSet.getObject("occurred_at", java.time.OffsetDateTime.class).toInstant(),
                resultSet.getLong("transaction_id"),
                resultSet.getObject("transaction_date", java.time.LocalDate.class),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("transaction_type"),
                resultSet.getString("anomaly_reason"));
    }

    private static String errorMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static void requireSingleUpdate(int updated, UUID eventId, String action) {
        if (updated != 1) {
            throw new IllegalStateException("No fue posible " + action + " para el evento " + eventId);
        }
    }
}
