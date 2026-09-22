package com.duoc.banco_legacy.mobile.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4jBulkheadProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RemoteAccountServiceClientTests {
    private MockRestServiceServer server;
    private RemoteAccountServiceClient client;
    private Resilience4JCircuitBreakerFactory factory;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new Resilience4JConfigurationProperties();
        factory = new Resilience4JCircuitBreakerFactory(
                CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(),
                new Resilience4jBulkheadProvider(ThreadPoolBulkheadRegistry.ofDefaults(),
                        BulkheadRegistry.ofDefaults(), properties), properties);
        factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(3).minimumNumberOfCalls(3).failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofMillis(50))
                        .permittedNumberOfCallsInHalfOpenState(1)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(1)).build())
                .build());
        client = new RemoteAccountServiceClient(builder, factory, "banco-legacy-account-service");
    }

    @Test
    void callsLogicalServiceNameAndPropagatesBearer() {
        server.expect(requestTo("https://banco-legacy-account-service/internal/accounts/101/summary"))
                .andExpect(header("Authorization", "Bearer signed-token"))
                .andRespond(withSuccess("{\"accountId\":101,\"balance\":1010,\"accountType\":\"ahorro\"}", MediaType.APPLICATION_JSON));

        var response = client.getSummary(101, "Bearer signed-token");

        assertThat(response.balance()).isEqualByComparingTo("1010");
        server.verify();
    }

    @Test
    void repeatedRemoteFailuresOpenCircuitAndUseFallback() {
        server.expect(times(3), requestTo("https://banco-legacy-account-service/internal/accounts/101/summary"))
                .andRespond(withServerError());

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> client.getSummary(101, "Bearer signed-token"))
                    .isInstanceOf(AccountServiceUnavailableException.class);
        }

        assertThat(factory.getCircuitBreakerRegistry().circuitBreaker("accountService").getState().name())
                .isEqualTo("OPEN");
        assertThatThrownBy(() -> client.getSummary(101, "Bearer signed-token"))
                .isInstanceOf(AccountServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void successfulProbeClosesCircuitAfterAccountServiceRecovers() throws InterruptedException {
        server.expect(times(3), requestTo("https://banco-legacy-account-service/internal/accounts/101/summary"))
                .andRespond(withServerError());
        server.expect(requestTo("https://banco-legacy-account-service/internal/accounts/101/summary"))
                .andRespond(withSuccess("{\"accountId\":101,\"balance\":1010,\"accountType\":\"ahorro\"}",
                        MediaType.APPLICATION_JSON));

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> client.getSummary(101, "Bearer signed-token"))
                    .isInstanceOf(AccountServiceUnavailableException.class);
        }
        var circuitBreaker = factory.getCircuitBreakerRegistry().circuitBreaker("accountService");
        assertThat(circuitBreaker.getState().name()).isEqualTo("OPEN");

        Thread.sleep(100);
        assertThat(client.getSummary(101, "Bearer signed-token").balance()).isEqualByComparingTo("1010");
        assertThat(circuitBreaker.getState().name()).isEqualTo("CLOSED");
        server.verify();
    }
}
