package com.duoc.banco_legacy.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

class MobileConfigClientIntegrationTests {
    @Test
    void obtainsLogicalAccountServiceNameFromConfigServer() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            var response = """
                    {"name":"banco-legacy-mobile-bff","profiles":["default"],"label":null,"version":null,
                    "state":null,"propertySources":[{"name":"test","source":{
                    "banking.account-service-name":"account-service-from-config"}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();

        try (var context = new SpringApplicationBuilder(ConfigClientTestApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.application.name=banco-legacy-mobile-bff",
                        "--spring.config.import=configserver:http://localhost:" + server.getAddress().getPort(),
                        "--spring.cloud.config.enabled=true",
                        "--spring.cloud.discovery.enabled=false",
                        "--spring.main.banner-mode=off")) {
            assertThat(context.getEnvironment().getProperty("banking.account-service-name"))
                    .isEqualTo("account-service-from-config");
            assertThat(requests).hasPositiveValue();
        } finally {
            server.stop(0);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ConfigurationPropertiesScan
    static class ConfigClientTestApplication {
    }
}
