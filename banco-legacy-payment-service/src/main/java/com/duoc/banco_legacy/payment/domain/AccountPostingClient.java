package com.duoc.banco_legacy.payment.domain;
import com.duoc.banco_legacy.core.event.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.http.HttpHeaders;
@Component
public class AccountPostingClient {
 private final RestClient client; private final CircuitBreakerFactory<?,?> breakers;
 public AccountPostingClient(RestClient.Builder builder,CircuitBreakerFactory<?,?> breakers,
   @Value("${banking.account-service-name:banco-legacy-account-service}")String service) {
  this.client=builder.baseUrl("https://"+service).build();this.breakers=breakers;
 }
 public FinancialOperationResult post(String authorization,String key,FinancialOperationRequest request) {
  return breakers.create("accountPosting").run(()->{
   try {
    var result=client.post().uri("/internal/accounts/postings").header(HttpHeaders.AUTHORIZATION,authorization)
     .header("Idempotency-Key",key).body(request).retrieve().body(FinancialOperationResult.class);
    if(result==null)throw new IllegalStateException("EMPTY_RECEIPT");
    result.validateFor(request);return result;
   } catch(HttpClientErrorException failure) { throw failure; }
  },failure->{
   Throwable cause=failure;
   while(cause.getCause()!=null && !(cause instanceof HttpClientErrorException))cause=cause.getCause();
   if(cause instanceof HttpClientErrorException response) {
    int status=response.getStatusCode().value();
    if(status==400 || status==404 || status==409) {
     String code="ACCOUNT_REJECTED";
     try {
      var parsed=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponseBodyAsString());
      if(parsed.has("code") && parsed.get("code").asText().matches("[A-Z_]{1,120}"))code=parsed.get("code").asText();
     } catch(Exception ignored){}
     throw new PaymentException(status,code);
    }
    if(status==401 || status==403)throw new PaymentException(403,"DEPENDENCY_FORBIDDEN");
   }
   throw new PaymentException(503,"ACCOUNT_UNAVAILABLE");
  });
 }
}
