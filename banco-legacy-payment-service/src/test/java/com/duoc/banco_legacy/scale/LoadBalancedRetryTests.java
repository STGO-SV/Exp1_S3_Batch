package com.duoc.banco_legacy.scale;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=LoadBalancedRetryTests.Config.class,
 webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
 "spring.cloud.config.enabled=false","eureka.client.enabled=false",
 "spring.cloud.loadbalancer.cache.enabled=false",
 "spring.cloud.loadbalancer.retry.enabled=true",
 "spring.cloud.loadbalancer.retry.retry-on-all-operations=true",
 "spring.cloud.loadbalancer.retry.max-retries-on-next-service-instance=1",
 "spring.cloud.loadbalancer.retry.max-retries-on-same-service-instance=0",
 "spring.cloud.loadbalancer.retry.retryable-status-codes=503"})
class LoadBalancedRetryTests {
    static final AtomicInteger failedCalls=new AtomicInteger();
    static final AtomicInteger successfulCalls=new AtomicInteger();
    static final AtomicReference<String> key=new AtomicReference<>(),body=new AtomicReference<>();
    static final HttpServer failed=server(true),healthy=server(false);
    @Configuration @EnableAutoConfiguration
    static class Config {
        @Bean @LoadBalanced RestClient.Builder client() { return RestClient.builder(); }
    }
    static HttpServer server(boolean fail) {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/post",exchange -> {
                String payload=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                if(fail)failedCalls.incrementAndGet();
                else {
                    successfulCalls.incrementAndGet();
                    key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));body.set(payload);
                }
                byte[] response=(fail?"unavailable":"ok").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(fail?503:200,response.length);
                exchange.getResponseBody().write(response);exchange.close();
            });
            server.start();return server;
        } catch(Exception failure) {throw new IllegalStateException(failure);}
    }
    @DynamicPropertySource static void instances(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.replica-test[0].uri",
            ()->"http://127.0.0.1:"+failed.getAddress().getPort());
        registry.add("spring.cloud.discovery.client.simple.instances.replica-test[1].uri",
            ()->"http://127.0.0.1:"+healthy.getAddress().getPort());
    }
    @Autowired RestClient.Builder client;
    @AfterAll static void stop() { failed.stop(0);healthy.stop(0); }
    @Test void restClientRetriesDifferentReplicaAndPreservesFinancialIdempotencyKeyAndBody() {
        var rest=client.build();
        for(int i=0;i<8;i++)
            assertThat(rest.post().uri("http://replica-test/post").header("Idempotency-Key","same-financial-key")
                .body(Map.of("amount",1)).retrieve().body(String.class)).isEqualTo("ok");
        assertThat(failedCalls.get()).isPositive();
        assertThat(successfulCalls.get()).isEqualTo(8);
        assertThat(key.get()).isEqualTo("same-financial-key");
        assertThat(body.get()).contains("\"amount\":1");
    }
}
