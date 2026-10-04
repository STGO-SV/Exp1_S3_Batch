package com.duoc.banco_legacy.customer;
import com.duoc.banco_legacy.customer.registry.*;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.util.*;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.springframework.cloud.circuitbreaker.resilience4j.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AccountRegistryClientTests {
    static final UUID ID=UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String URL="https://banco-legacy-account-service/api/accounts?customerId=00000000-0000-0000-0000-000000000001&limit=20&offset=0";
    MockRestServiceServer server;
    AccountRegistryClient client;
    Resilience4JCircuitBreakerFactory factory;
    @BeforeEach void configure() {
        var builder=RestClient.builder();
        server=MockRestServiceServer.bindTo(builder).build();
        var properties=new Resilience4JConfigurationProperties();
        factory=new Resilience4JCircuitBreakerFactory(CircuitBreakerRegistry.ofDefaults(),
                TimeLimiterRegistry.ofDefaults(),new Resilience4jBulkheadProvider(
                ThreadPoolBulkheadRegistry.ofDefaults(),BulkheadRegistry.ofDefaults(),properties),properties);
        factory.configureDefault(id->new Resilience4JConfigBuilder(id).circuitBreakerConfig(
                CircuitBreakerConfig.custom().slidingWindowSize(3).minimumNumberOfCalls(3)
                        .failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(1))
                        .ignoreExceptions(HttpClientErrorException.class).build()).build());
        client=new AccountRegistryClient(builder,factory,"banco-legacy-account-service");
    }
    @Test void relaysBearerAndPreservesRealResponse() {
        server.expect(requestTo(URL)).andExpect(header("Authorization","Bearer signed-token"))
                .andRespond(withSuccess("[{\"accountId\":905,\"accountType\":\"ahorro\",\"status\":\"OPEN\",\"version\":0,\"customerIds\":[\""+ID+"\"]}]",MediaType.APPLICATION_JSON));
        assertThat(client.accounts(ID,20,0,"Bearer signed-token")).extracting(AccountRegistryClient.Account::accountId).containsExactly(905L);
        server.verify();
    }
    @Test void repeatedFailuresOpenCircuitAndNeverInventResult() {
        server.expect(times(3),requestTo(URL)).andRespond(withServerError());
        for(int i=0;i<4;i++) assertThatThrownBy(()->client.accounts(ID,20,0,"Bearer signed-token"))
                .isInstanceOf(RegistryException.class).satisfies(failure->
                        assertThat(((RegistryException)failure).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(factory.getCircuitBreakerRegistry().circuitBreaker("accountRegistry").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        server.verify();
    }
    @Test void remoteForbiddenIsPreservedAsControlledRejection() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(()->client.accounts(ID,20,0,"Bearer signed-token")).isInstanceOf(RegistryException.class).satisfies(failure->
                assertThat(((RegistryException)failure).status()).isEqualTo(HttpStatus.FORBIDDEN));
        server.verify();
    }
    @Test void emptyResponseIsUnavailableNotSuccess() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NO_CONTENT));
        assertThatThrownBy(()->client.accounts(ID,20,0,"Bearer signed-token")).isInstanceOf(RegistryException.class).satisfies(failure->
                assertThat(((RegistryException)failure).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }
    @Test void actualEmptyListIsAllowed() {
        server.expect(requestTo(URL)).andRespond(withSuccess("[]",MediaType.APPLICATION_JSON));
        assertThat(client.accounts(ID,20,0,"Bearer signed-token")).isEmpty();
        server.verify();
    }
}