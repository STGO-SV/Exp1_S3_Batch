package com.duoc.banco_legacy.atm.client;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.duoc.banco_legacy.core.exception.AccountNotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4jBulkheadProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RemoteAccountServiceClientTests {
    private MockRestServiceServer server;
    private RemoteAccountServiceClient client;
    private Resilience4JCircuitBreakerFactory factory;
    private static final String BALANCE = "https://banco-legacy-account-service/internal/accounts/101/atm-balance";
    private static final String MOVEMENTS = "https://banco-legacy-account-service/internal/accounts/101/atm-movements";

    @BeforeEach void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new Resilience4JConfigurationProperties();
        factory = new Resilience4JCircuitBreakerFactory(CircuitBreakerRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(), new Resilience4jBulkheadProvider(
                ThreadPoolBulkheadRegistry.ofDefaults(), BulkheadRegistry.ofDefaults(), properties), properties);
        factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom().slidingWindowSize(3)
                        .minimumNumberOfCalls(3).failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(1)).build()).build());
        client = new RemoteAccountServiceClient(builder, factory, "banco-legacy-account-service");
    }

    @Test void relaysBearerForBothGetOperations() {
        server.expect(requestTo(BALANCE)).andExpect(header("Authorization", "Bearer signed-token"))
                .andRespond(withSuccess("{\"accountId\":101,\"availableBalance\":910}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(MOVEMENTS)).andExpect(header("Authorization", "Bearer signed-token"))
                .andRespond(withSuccess("[{\"type\":\"retiro\",\"amount\":100}]", MediaType.APPLICATION_JSON));
        assertThat(client.getBalance(101, "Bearer signed-token").availableBalance()).isEqualByComparingTo("910");
        assertThat(client.getMovements(101, "Bearer signed-token")).hasSize(1);
        server.verify();
    }

    @Test void preserves404() {
        server.expect(requestTo(BALANCE)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.getBalance(101, "Bearer signed-token"))
                .isInstanceOf(AccountNotFoundException.class);
        server.verify();
    }

    @Test void repeatedFailuresOpenCircuitAndFallbackWithoutData() {
        server.expect(times(3), requestTo(BALANCE)).andRespond(withServerError());
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> client.getBalance(101, "Bearer signed-token"))
                    .isInstanceOf(AccountServiceUnavailableException.class);
        }
        assertThat(factory.getCircuitBreakerRegistry().circuitBreaker("accountService").getState().name())
                .isEqualTo("OPEN");
        server.verify();
    }
}
