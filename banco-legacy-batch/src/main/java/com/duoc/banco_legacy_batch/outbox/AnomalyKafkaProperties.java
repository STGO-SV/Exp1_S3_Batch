package com.duoc.banco_legacy_batch.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "banking.kafka")
public class AnomalyKafkaProperties {

    private String anomalyTopic = "banco.transacciones.anomalas.v1";
    private int publishRetries = 3;
    private long publishBackoff = 1000;

    public String getAnomalyTopic() {
        return anomalyTopic;
    }

    public void setAnomalyTopic(String anomalyTopic) {
        this.anomalyTopic = anomalyTopic;
    }

    public int getPublishRetries() {
        return publishRetries;
    }

    public void setPublishRetries(int publishRetries) {
        this.publishRetries = publishRetries;
    }

    public long getPublishBackoff() {
        return publishBackoff;
    }

    public void setPublishBackoff(long publishBackoff) {
        this.publishBackoff = publishBackoff;
    }
}
