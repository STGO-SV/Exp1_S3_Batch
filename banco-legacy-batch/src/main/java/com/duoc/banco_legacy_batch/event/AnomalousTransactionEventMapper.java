package com.duoc.banco_legacy_batch.event;

import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import com.duoc.banco_legacy_batch.model.TransaccionProcesada;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class AnomalousTransactionEventMapper {

    public static final int EVENT_VERSION = 1;
    public static final String ANOMALY_REASON = "AMOUNT_ABOVE_2000";

    private final Clock clock;
    private final Supplier<UUID> eventIdSupplier;

    public AnomalousTransactionEventMapper() {
        this(Clock.systemUTC(), UUID::randomUUID);
    }

    AnomalousTransactionEventMapper(Clock clock, Supplier<UUID> eventIdSupplier) {
        this.clock = Objects.requireNonNull(clock);
        this.eventIdSupplier = Objects.requireNonNull(eventIdSupplier);
    }

    public AnomalousTransactionEvent map(TransaccionProcesada item, String correlationId) {
        Objects.requireNonNull(item, "item");
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId es obligatorio");
        }
        return new AnomalousTransactionEvent(
                eventIdSupplier.get(), correlationId, EVENT_VERSION, Instant.now(clock),
                item.id(), item.fecha(), item.monto(), item.tipo(), ANOMALY_REASON);
    }
}
