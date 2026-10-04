package com.duoc.banco_legacy.payment.domain;
import org.springframework.context.annotation.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
@Configuration
public class PaymentRemoteConfig {
 @Bean @LoadBalanced RestClient.Builder paymentRestClient() {
  var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(2000);factory.setReadTimeout(3000);
  return RestClient.builder().requestFactory(factory);
 }
}
