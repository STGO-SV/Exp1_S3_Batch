package com.duoc.banco_legacy.anomaly.listener;

import com.duoc.banco_legacy.anomaly.repository.ProcessedAnomalyEventRepository;
import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AnomalousTransactionListener {

    private static final Logger log = LoggerFactory.getLogger(AnomalousTransactionListener.class);

    private final ProcessedAnomalyEventRepository repository;
    private final String consumerInstance;

    public AnomalousTransactionListener(
            ProcessedAnomalyEventRepository repository,
            @Value("${banking.kafka.consumer-instance:${random.uuid}}") String consumerInstance) {
        this.repository = repository;
        this.consumerInstance = consumerInstance;
    }

    @KafkaListener(topics = "${banking.kafka.anomaly-topic}")
    public void consume(ConsumerRecord<String, AnomalousTransactionEvent> record) {
        AnomalousTransactionEvent event = record.value();
        boolean inserted = repository.save(event, record.topic(), record.partition(),
                record.offset(), consumerInstance);

        log.info("event=anomaly_consumed outcome={} eventId={} transactionId={} topic={} partition={} offset={} consumerInstance={}",
                inserted ? "PROCESSED" : "DUPLICATE", event.eventId(), event.transactionId(),
                record.topic(), record.partition(), record.offset(), consumerInstance);
    }
}
