package com.duoc.banco_legacy.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.cloud.config.server.native.search-locations=classpath:/test-config")
class ConfigServerApplicationTests {
    @Autowired Environment environment;
    @Autowired TestRestTemplate rest;

    @Test
    void startsWithNativeRepository() {
        assertThat(environment.getProperty("spring.profiles.active")).isEqualTo("native");
    }

    @Test
    void servesMobileConfigurationFromNativeRepository() {
        var response = rest.getForEntity("/banco-legacy-mobile-bff/default", Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsKey("propertySources");
    }
}
