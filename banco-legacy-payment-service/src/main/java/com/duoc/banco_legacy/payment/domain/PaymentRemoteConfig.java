package com.duoc.banco_legacy.payment.domain;
import org.springframework.context.annotation.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestClient;
import com.duoc.banco_legacy.core.http.ServiceTlsRequestFactory;
import org.springframework.beans.factory.annotation.Value;
@Configuration
public class PaymentRemoteConfig {
 @Bean @LoadBalanced RestClient.Builder paymentRestClient(@Value("${banking.http.tls-service-name:}") String tlsService) {
  var factory=new ServiceTlsRequestFactory(tlsService);factory.setConnectTimeout(2000);factory.setReadTimeout(3000);
  return RestClient.builder().requestFactory(factory);
 }
}
