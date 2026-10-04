package com.duoc.banco_legacy.account.financial;

import com.duoc.banco_legacy.core.event.FinancialOperationCompletedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="banking.kafka.enabled",havingValue="true",matchIfMissing=true)
public class FinancialOutboxPublisher {
    private static final Logger LOG=LoggerFactory.getLogger(FinancialOutboxPublisher.class);
    private final FinancialOutboxStore store;
    private final ObjectMapper json;
    private final KafkaTemplate<String,Object> kafka;
    private final String topic,owner;
    private final int leaseSeconds,batchSize;

    public FinancialOutboxPublisher(FinancialOutboxStore store,ObjectMapper json,KafkaTemplate<String,Object> kafka,
            @Value("${banking.kafka.financial-topic:banco.operaciones.completadas.v1}") String topic,
            @Value("${banking.kafka.outbox-owner:${spring.application.name:account}:${HOSTNAME:local}}") String owner,
            @Value("${banking.kafka.outbox-lease-seconds:30}") int leaseSeconds,
            @Value("${banking.kafka.outbox-batch-size:20}") int batchSize) {
        if(leaseSeconds<15 || batchSize<1 || batchSize>100)throw new IllegalArgumentException("Invalid outbox lease/batch");
        this.store=store;this.json=json;this.kafka=kafka;this.topic=topic;
        this.owner=owner+":"+UUID.randomUUID();this.leaseSeconds=leaseSeconds;this.batchSize=batchSize;
    }
    @Scheduled(fixedDelayString="${banking.kafka.outbox-delay-ms:2000}")
    public void publish() {
        for(int i=0;i<batchSize && !Thread.currentThread().isInterrupted();i++) {
            var next=store.claim(owner,leaseSeconds);
            if(next.isEmpty())return;
            var claim=next.get();
            try {
                var event=json.readValue(claim.payload(),FinancialOperationCompletedEvent.class);
                event.validate();
                kafka.send(topic,event.result().operationId().toString(),event).get(5,TimeUnit.SECONDS);
                if(store.published(claim))
                    LOG.info("Financial outbox published eventId={} owner={}",event.eventId(),owner);
                else LOG.warn("Financial outbox lease lost after ack eventId={} owner={}",event.eventId(),owner);
            } catch(Exception failure) {
                store.retry(claim,failure.getClass().getSimpleName());
                if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
            }
        }
    }
}
