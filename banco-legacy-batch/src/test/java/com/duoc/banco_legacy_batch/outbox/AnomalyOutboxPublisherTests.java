package com.duoc.banco_legacy_batch.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class AnomalyOutboxPublisherTests {

    private AnomalyOutboxRepository repository;
    private KafkaTemplate<String, AnomalousTransactionEvent> kafkaTemplate;
    private AnomalyOutboxPublisher publisher;
    private AnomalousTransactionEvent event;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(AnomalyOutboxRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        var properties = new AnomalyKafkaProperties();
        properties.setAnomalyTopic("banco.transacciones.anomalas.v1");
        properties.setPublishRetries(3);
        properties.setPublishBackoff(0);
        publisher = new AnomalyOutboxPublisher(repository, kafkaTemplate, properties);
        event = new AnomalousTransactionEvent(
                UUID.randomUUID(), "transaccionesDiariasJob:101", 1,
                Instant.parse("2026-09-22T22:00:00Z"), 4001L,
                LocalDate.of(2026, 9, 22), new BigDecimal("3100.25"),
                "credito", "AMOUNT_ABOVE_2000");
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmacionExitosaPublicaTopicoKeyYEventoLuegoMarcaPublished() {
        var sendResult = mock(SendResult.class);
        var metadata = mock(RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn("banco.transacciones.anomalas.v1");
        when(metadata.partition()).thenReturn(2);
        when(metadata.offset()).thenReturn(17L);
        when(repository.findPending()).thenReturn(List.of(event));
        when(kafkaTemplate.send("banco.transacciones.anomalas.v1", "4001", event))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        publisher.publishPending();

        verify(kafkaTemplate).send("banco.transacciones.anomalas.v1", "4001", event);
        verify(repository).markPublished(event.eventId());
        verify(repository, never()).recordFailure(any(), any());
    }

    @Test
    void falloKafkaMantienePendingYRegistraCadaIntentoSinMarcarPublished() {
        when(repository.findPending()).thenReturn(List.of(event));
        var failed = new CompletableFuture<SendResult<String, AnomalousTransactionEvent>>();
        failed.completeExceptionally(new IllegalStateException("Kafka caído"));
        when(kafkaTemplate.send("banco.transacciones.anomalas.v1", "4001", event))
                .thenReturn(failed);

        publisher.publishPending();

        verify(kafkaTemplate, org.mockito.Mockito.times(3))
                .send("banco.transacciones.anomalas.v1", "4001", event);
        verify(repository, org.mockito.Mockito.times(3)).recordFailure(any(), any());
        verify(repository, never()).markPublished(any());
    }

    @Test
    void publishedNoSeVuelveAEnviarPorqueNoEsRecuperadoComoPending() {
        when(repository.findPending()).thenReturn(List.of());

        publisher.publishPending();

        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(repository, never()).markPublished(any());
        verify(repository, never()).recordFailure(any(), any());
    }
}
