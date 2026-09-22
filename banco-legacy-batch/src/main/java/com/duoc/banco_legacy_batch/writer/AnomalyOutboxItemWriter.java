package com.duoc.banco_legacy_batch.writer;

import com.duoc.banco_legacy_batch.event.AnomalousTransactionEventMapper;
import com.duoc.banco_legacy_batch.model.TransaccionProcesada;
import java.sql.Timestamp;
import java.util.function.Supplier;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.simple.JdbcClient;

public class AnomalyOutboxItemWriter implements ItemWriter<TransaccionProcesada> {

    private final JdbcClient jdbcClient;
    private final AnomalousTransactionEventMapper mapper;
    private final Supplier<String> correlationIdSupplier;

    public AnomalyOutboxItemWriter(JdbcClient jdbcClient, AnomalousTransactionEventMapper mapper,
                                   String correlationId) {
        this(jdbcClient, mapper, () -> correlationId);
    }

    public AnomalyOutboxItemWriter(JdbcClient jdbcClient, AnomalousTransactionEventMapper mapper,
                                   Supplier<String> correlationIdSupplier) {
        this.jdbcClient = jdbcClient;
        this.mapper = mapper;
        this.correlationIdSupplier = correlationIdSupplier;
    }

    @Override
    public void write(Chunk<? extends TransaccionProcesada> chunk) {
        String correlationId = correlationIdSupplier.get();
        chunk.getItems().stream()
                .filter(TransaccionProcesada::anomalia)
                .map(item -> mapper.map(item, correlationId))
                .forEach(event -> jdbcClient.sql("""
                        INSERT INTO anomaly_event_outbox
                            (event_id, correlation_id, event_version, occurred_at,
                             transaction_id, transaction_date, amount, transaction_type,
                             anomaly_reason, status, publish_attempts)
                        VALUES
                            (:eventId, :correlationId, :eventVersion, :occurredAt,
                             :transactionId, :transactionDate, :amount, :transactionType,
                             :anomalyReason, 'PENDING', 0)
                        """)
                        .param("eventId", event.eventId())
                        .param("correlationId", event.correlationId())
                        .param("eventVersion", event.eventVersion())
                        .param("occurredAt", Timestamp.from(event.occurredAt()))
                        .param("transactionId", event.transactionId())
                        .param("transactionDate", event.transactionDate())
                        .param("amount", event.amount())
                        .param("transactionType", event.transactionType())
                        .param("anomalyReason", event.anomalyReason())
                        .update());
    }

    public static String currentJobCorrelationId() {
        var context = StepSynchronizationManager.getContext();
        if (context == null || context.getStepExecution().getJobExecutionId() == null) {
            throw new IllegalStateException("No existe una ejecución batch activa para generar correlationId");
        }
        return "transaccionesDiariasJob:" + context.getStepExecution().getJobExecutionId();
    }
}
