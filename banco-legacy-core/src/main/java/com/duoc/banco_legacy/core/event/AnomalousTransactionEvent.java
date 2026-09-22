package com.duoc.banco_legacy.core.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AnomalousTransactionEvent(
        UUID eventId,
        String correlationId,
        int eventVersion,
        Instant occurredAt,
        Long transactionId,
        LocalDate transactionDate,
        BigDecimal amount,
        String transactionType,
        String anomalyReason) {
}
