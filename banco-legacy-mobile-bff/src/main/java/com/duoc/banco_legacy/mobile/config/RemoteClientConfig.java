package com.duoc.banco_legacy.mobile.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.duoc.banco_legacy.core.http.ServiceTlsRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RemoteClientConfig {
    @Bean
    @LoadBalanced
    RestClient.Builder accountRestClientBuilder(
            @Value("${banking.http.connect-timeout:2s}") Duration connectTimeout,
            @Value("${banking.http.read-timeout:3s}") Duration readTimeout,
            @Value("${banking.http.tls-service-name:}") String tlsService) {
        var requestFactory = new ServiceTlsRequestFactory(tlsService);
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
