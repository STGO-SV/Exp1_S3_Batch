package com.duoc.banco_legacy.core.http;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceTlsRequestFactoryTests {
    @Test void acceptsOnlyConfiguredServiceSanAndRejectsRoutingAddressOrDifferentService() throws Exception {
        var cert=mock(X509Certificate.class);
        when(cert.getSubjectX500Principal()).thenReturn(new X500Principal("CN=Banco Legacy Local"));
        when(cert.getSubjectAlternativeNames()).thenReturn(List.of(List.of(2,"account-service")));
        assertThat(ServiceTlsRequestFactory.matches("account-service",cert)).isTrue();
        assertThat(ServiceTlsRequestFactory.matches("customer-service",cert)).isFalse();
        assertThat(ServiceTlsRequestFactory.matches("172.22.0.10",cert)).isFalse();
    }
}
