package com.duoc.banco_legacy.anomaly.repository;

import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedAnomalyEventRepository {

    private final JdbcClient jdbcClient;
    private final Clock clock;

    @Autowired
    public ProcessedAnomalyEventRepository(JdbcClient jdbcClient) {
        this(jdbcClient, Clock.systemUTC());
    }

    ProcessedAnomalyEventRepository(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    public boolean save(AnomalousTransactionEvent event, String topic, int partition,
                        long offset, String consumerInstance) {
        try {
            jdbcClient.sql("""
                            INSERT INTO processed_anomaly_event
                                (event_id, transaction_id, event_version, correlation_id,
                                 occurred_at, transaction_date, amount, transaction_type,
                                 anomaly_reason, processed_at, topic, partition_id,
                                 offset_value, consumer_instance)
                            VALUES
                                (:eventId, :transactionId, :eventVersion, :correlationId,
                                 :occurredAt, :transactionDate, :amount, :transactionType,
                                 :anomalyReason, :processedAt, :topic, :partitionId,
                                 :offsetValue, :consumerInstance)
                            """)
                    .param("eventId", event.eventId())
                    .param("transactionId", event.transactionId())
                    .param("eventVersion", event.eventVersion())
                    .param("correlationId", event.correlationId())
                    .param("occurredAt", OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
                    .param("transactionDate", event.transactionDate())
                    .param("amount", event.amount())
                    .param("transactionType", event.transactionType())
                    .param("anomalyReason", event.anomalyReason())
                    .param("processedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                    .param("topic", topic)
                    .param("partitionId", partition)
                    .param("offsetValue", offset)
                    .param("consumerInstance", consumerInstance)
                    .update();
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }
}
