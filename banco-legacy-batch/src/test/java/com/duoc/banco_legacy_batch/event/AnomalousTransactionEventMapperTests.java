package com.duoc.banco_legacy_batch.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.duoc.banco_legacy_batch.model.TransaccionProcesada;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnomalousTransactionEventMapperTests {

    @Test
    void copiaElContratoCompletoConMetadatosDeEvento() {
        UUID eventId = UUID.fromString("66dc9999-19c8-45b7-b213-b762682ef40b");
        Instant occurredAt = Instant.parse("2026-09-22T21:30:00Z");
        var mapper = new AnomalousTransactionEventMapper(
                Clock.fixed(occurredAt, ZoneOffset.UTC), () -> eventId);
        var transaction = new TransaccionProcesada(
                901L, LocalDate.of(2026, 9, 22), new BigDecimal("2345.67"), "debito", true);

        var event = mapper.map(transaction, "transaccionesDiariasJob:77");

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.correlationId()).isEqualTo("transaccionesDiariasJob:77");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
        assertThat(event.transactionId()).isEqualTo(901L);
        assertThat(event.transactionDate()).isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(event.amount()).isEqualByComparingTo("2345.67");
        assertThat(event.transactionType()).isEqualTo("debito");
        assertThat(event.anomalyReason()).isEqualTo("AMOUNT_ABOVE_2000");
    }

    @Test
    void generaUuidYTimestampValidosEnUsoNormal() {
        Instant before = Instant.now();
        var event = new AnomalousTransactionEventMapper().map(
                new TransaccionProcesada(1L, LocalDate.now(), new BigDecimal("2000.01"), "credito", true),
                "transaccionesDiariasJob:1");

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isBetween(before, Instant.now());
        assertThat(event.correlationId()).isNotBlank();
    }
}
