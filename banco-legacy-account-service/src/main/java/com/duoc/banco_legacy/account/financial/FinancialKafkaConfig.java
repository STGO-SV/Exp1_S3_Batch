package com.duoc.banco_legacy.account.financial;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;
@Configuration @EnableScheduling
@ConditionalOnProperty(name="banking.kafka.enabled",havingValue="true",matchIfMissing=true)
public class FinancialKafkaConfig {
 @Bean NewTopic financialTopic(@org.springframework.beans.factory.annotation.Value("${banking.kafka.financial-topic:banco.operaciones.completadas.v1}")String topic){
  return TopicBuilder.name(topic).partitions(3).replicas(1).build();
 }
}
