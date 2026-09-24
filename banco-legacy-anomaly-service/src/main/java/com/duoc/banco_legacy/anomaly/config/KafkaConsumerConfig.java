package com.duoc.banco_legacy.anomaly.config;

import com.duoc.banco_legacy.core.event.AnomalousTransactionEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    ProducerFactory<String, Object> anomalyRecoveryProducerFactory(KafkaProperties properties) {
        Map<String, Object> producerProperties = new LinkedHashMap<>(properties.buildProducerProperties(null));
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        DelegatingByTypeSerializer valueSerializer = new DelegatingByTypeSerializer(Map.of(
                byte[].class, new ByteArraySerializer(),
                AnomalousTransactionEvent.class, new JsonSerializer<>()));
        return new DefaultKafkaProducerFactory<>(producerProperties, new StringSerializer(), valueSerializer);
    }

    @Bean
    KafkaTemplate<String, Object> anomalyRecoveryKafkaTemplate(
            ProducerFactory<String, Object> anomalyRecoveryProducerFactory) {
        return new KafkaTemplate<>(anomalyRecoveryProducerFactory);
    }

    @Bean
    CommonErrorHandler anomalyConsumerErrorHandler(
            KafkaTemplate<String, Object> anomalyRecoveryKafkaTemplate,
            @Value("${banking.kafka.anomaly-dlt-topic}") String dltTopic) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                anomalyRecoveryKafkaTemplate,
                (record, exception) -> new TopicPartition(dltTopic, record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(java.time.Duration.ofSeconds(10));

        // FixedBackOff maxAttempts counts retries after the first delivery: 2 retries = 3 total attempts.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 2L));
        errorHandler.addNotRetryableExceptions(DeserializationException.class);
        errorHandler.setCommitRecovered(true);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) ->
                log.warn("event=anomaly_processing_failed topic={} partition={} offset={} attempt={} error={}",
                        record.topic(), record.partition(), record.offset(), deliveryAttempt,
                        exception.getClass().getSimpleName()));
        return errorHandler;
    }
}
