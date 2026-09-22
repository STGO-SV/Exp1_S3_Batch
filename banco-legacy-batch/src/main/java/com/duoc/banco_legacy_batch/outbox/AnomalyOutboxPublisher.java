package com.duoc.banco_legacy_batch.outbox;

import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "banking.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class AnomalyOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(AnomalyOutboxPublisher.class);

    private final AnomalyOutboxRepository repository;
    private final KafkaTemplate<String, AnomalousTransactionEvent> kafkaTemplate;
    private final AnomalyKafkaProperties properties;
    private final AtomicBoolean polling = new AtomicBoolean();

    public AnomalyOutboxPublisher(AnomalyOutboxRepository repository,
                                  KafkaTemplate<String, AnomalousTransactionEvent> kafkaTemplate,
                                  AnomalyKafkaProperties properties) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${banking.kafka.outbox.fixed-delay:2000}")
    public void publishPending() {
        if (!polling.compareAndSet(false, true)) {
            log.warn("event=outbox_poll_skipped reason=already_running");
            return;
        }
        try {
            repository.findPending().forEach(this::publishWithRetry);
        } finally {
            polling.set(false);
        }
    }

    private void publishWithRetry(AnomalousTransactionEvent event) {
        String key = event.transactionId().toString();
        for (int attempt = 1; attempt <= properties.getPublishRetries(); attempt++) {
            try {
                RecordMetadata metadata = kafkaTemplate
                        .send(properties.getAnomalyTopic(), key, event)
                        .get().getRecordMetadata();
                repository.markPublished(event.eventId());
                log.info("event=anomaly_published topic={} partition={} offset={} eventId={} transactionId={}",
                        metadata.topic(), metadata.partition(), metadata.offset(),
                        event.eventId(), event.transactionId());
                return;
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                repository.recordFailure(event.eventId(), failure);
                log.error("event=anomaly_publish_failed eventId={} transactionId={} attempt={}",
                        event.eventId(), event.transactionId(), attempt, failure);
                return;
            } catch (Exception failure) {
                repository.recordFailure(event.eventId(), failure);
                log.error("event=anomaly_publish_failed eventId={} transactionId={} attempt={}",
                        event.eventId(), event.transactionId(), attempt, failure);
                if (attempt < properties.getPublishRetries() && !waitBeforeRetry()) {
                    return;
                }
            }
        }
    }

    private boolean waitBeforeRetry() {
        try {
            TimeUnit.MILLISECONDS.sleep(properties.getPublishBackoff());
            return true;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
