package com.duoc.banco_legacy.account.registry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
@Configuration
public class RegistryRemoteClientConfig {
    @Bean @LoadBalanced
    RestClient.Builder registryRestClientBuilder(
            @Value("${banking.http.connect-timeout:2s}") Duration connect,
            @Value("${banking.http.read-timeout:3s}") Duration read) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect); factory.setReadTimeout(read);
        return RestClient.builder().requestFactory(factory);
    }
}