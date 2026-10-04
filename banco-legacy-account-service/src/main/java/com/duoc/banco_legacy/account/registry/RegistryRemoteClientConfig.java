package com.duoc.banco_legacy.account.registry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.duoc.banco_legacy.core.http.ServiceTlsRequestFactory;
import org.springframework.web.client.RestClient;
@Configuration
public class RegistryRemoteClientConfig {
    @Bean @LoadBalanced
    RestClient.Builder registryRestClientBuilder(
            @Value("${banking.http.connect-timeout:2s}") Duration connect,
            @Value("${banking.http.read-timeout:3s}") Duration read,
            @Value("${banking.http.tls-service-name:}") String tlsService) {
        var factory = new ServiceTlsRequestFactory(tlsService);
        factory.setConnectTimeout(connect); factory.setReadTimeout(read);
        return RestClient.builder().requestFactory(factory);
    }
}