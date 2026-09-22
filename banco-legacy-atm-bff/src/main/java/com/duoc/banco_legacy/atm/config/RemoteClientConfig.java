package com.duoc.banco_legacy.atm.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RemoteClientConfig {
    @Bean
    @LoadBalanced
    RestClient.Builder accountRestClientBuilder(
            @Value("${banking.http.connect-timeout:2s}") Duration connectTimeout,
            @Value("${banking.http.read-timeout:3s}") Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory);
    }
}
