package com.duoc.banco_legacy.account;
import com.duoc.banco_legacy.account.registry.*;
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

class CustomerRegistryClientTests {
    static final UUID ID=UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String URL="https://banco-legacy-customer-service/api/customers/00000000-0000-0000-0000-000000000001";
    MockRestServiceServer server;
    CustomerRegistryClient client;
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
                        .failureRateThreshold(50).permittedNumberOfCallsInHalfOpenState(1).waitDurationInOpenState(Duration.ofSeconds(1))
                        .ignoreExceptions(HttpClientErrorException.class).build()).build());
        client=new CustomerRegistryClient(builder,factory,"banco-legacy-customer-service");
    }
    @Test void relaysBearerAndPreservesRealResponse() {
        server.expect(requestTo(URL)).andExpect(header("Authorization","Bearer signed-token"))
                .andRespond(withSuccess("{\"customerId\":\""+ID+"\",\"name\":\"Test\",\"version\":0}",MediaType.APPLICATION_JSON));
        client.require(ID,"Bearer signed-token");
        server.verify();
    }
    @Test void repeatedFailuresOpenCircuitAndNeverInventResult() {
        server.expect(times(3),requestTo(URL)).andRespond(withServerError());
        for(int i=0;i<4;i++) assertThatThrownBy(()->client.require(ID,"Bearer signed-token"))
                .isInstanceOf(RegistryException.class).satisfies(failure->
                        assertThat(((RegistryException)failure).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(factory.getCircuitBreakerRegistry().circuitBreaker("customerRegistry").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        server.verify();
    }
    @Test void remoteForbiddenIsPreservedAsControlledRejection() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(()->client.require(ID,"Bearer signed-token")).isInstanceOf(RegistryException.class).satisfies(failure->
                assertThat(((RegistryException)failure).status()).isEqualTo(HttpStatus.FORBIDDEN));
        server.verify();
    }
    @Test void emptyResponseIsUnavailableNotSuccess() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NO_CONTENT));
        assertThatThrownBy(()->client.require(ID,"Bearer signed-token")).isInstanceOf(RegistryException.class).satisfies(failure->
                assertThat(((RegistryException)failure).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }
    @Test void unknownCustomerDoesNotCountAsAvailabilityFailure() {
        server.expect(times(4),requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        for(int i=0;i<4;i++) assertThatThrownBy(()->client.require(ID,"Bearer signed-token"))
                .isInstanceOf(RegistryException.class).satisfies(failure->
                        assertThat(((RegistryException)failure).code()).isEqualTo("CUSTOMER_NOT_FOUND"));
        assertThat(factory.getCircuitBreakerRegistry().circuitBreaker("customerRegistry").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }


    @Test void customerRecoversAfterCircuitProbe() {
        server.expect(times(2),requestTo(URL)).andRespond(withSuccess("{\"customerId\":\""+ID+"\",\"name\":\"Test\",\"version\":0}",MediaType.APPLICATION_JSON));
        client.require(ID,"Bearer signed-token");
        var circuit=factory.getCircuitBreakerRegistry().circuitBreaker("customerRegistry");
        circuit.transitionToOpenState();circuit.transitionToHalfOpenState();
        client.require(ID,"Bearer signed-token");
        assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);server.verify();
    }
}
