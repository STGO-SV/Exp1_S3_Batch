package com.duoc.banco_legacy.anomaly.listener;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duoc.banco_legacy.anomaly.repository.ProcessedAnomalyEventRepository;
import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class AnomalousTransactionListenerTests {

    @Test
    void entregaEventoYMetadatosAlRepositorio() {
        var repository = org.mockito.Mockito.mock(ProcessedAnomalyEventRepository.class);
        var listener = new AnomalousTransactionListener(repository, "consumer-1");
        AnomalousTransactionEvent event = event();
        var record = new ConsumerRecord<String, AnomalousTransactionEvent>("topic-a", 2, 19L,
                event.transactionId().toString(), event);
        when(repository.save(event, "topic-a", 2, 19L, "consumer-1")).thenReturn(true);

        listener.consume(record);

        verify(repository).save(event, "topic-a", 2, 19L, "consumer-1");
    }

    @Test
    void propagaErrorRealParaEvitarConfirmarElOffset() {
        var repository = org.mockito.Mockito.mock(ProcessedAnomalyEventRepository.class);
        var listener = new AnomalousTransactionListener(repository, "consumer-1");
        AnomalousTransactionEvent event = event();
        var record = new ConsumerRecord<String, AnomalousTransactionEvent>("topic-a", 0, 3L,
                event.transactionId().toString(), event);
        when(repository.save(event, "topic-a", 0, 3L, "consumer-1"))
                .thenThrow(new DataAccessResourceFailureException("DB no disponible"));

        assertThatThrownBy(() -> listener.consume(record))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    private AnomalousTransactionEvent event() {
        return new AnomalousTransactionEvent(UUID.randomUUID(), "job:99", 1,
                Instant.parse("2026-09-22T15:30:00Z"), 600L,
                LocalDate.parse("2026-09-22"), new BigDecimal("3100.50"),
                "TRANSFER", "AMOUNT_ABOVE_2000");
    }
}
