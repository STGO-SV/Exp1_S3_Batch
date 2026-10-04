package com.duoc.banco_legacy.core.http;

import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLException;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/** Authenticate logical service DNS while connecting to the replica IP selected by discovery. */
public class ServiceTlsRequestFactory extends HttpComponentsClientHttpRequestFactory {
    private final String serviceDnsName;
    public ServiceTlsRequestFactory(String serviceDnsName) {
        this.serviceDnsName=serviceDnsName;
        setReadTimeout(Duration.ofSeconds(3));
    }
    public static boolean matches(String dnsName, X509Certificate certificate) {
        try { new DefaultHostnameVerifier().verify(dnsName,certificate);return true; }
        catch(SSLException failure) { return false; }
    }
    public void setReadTimeout(Duration timeout) {
        var verifier=new DefaultHostnameVerifier();
        var sockets=SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(SSLContexts.createSystemDefault())
                .setHostnameVerifier((address,session) ->
                        verifier.verify(serviceDnsName.isBlank()?address:serviceDnsName,session))
                .build();
        var connections=PoolingHttpClientConnectionManagerBuilder.create().setSSLSocketFactory(sockets).build();
        setHttpClient(HttpClients.custom().useSystemProperties().setConnectionManager(connections)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis())).build()).build());
    }
    public void setReadTimeout(int timeout) { setReadTimeout(Duration.ofMillis(timeout)); }
}
