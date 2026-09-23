package com.duoc.banco_legacy.anomaly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;
import org.springframework.kafka.listener.CommonErrorHandler;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    CommonErrorHandler anomalyConsumerErrorHandler() {
        return new CommonContainerStoppingErrorHandler();
    }
}
