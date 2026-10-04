package com.duoc.banco_legacy.payment.domain;
import com.duoc.banco_legacy.core.event.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
 public record Deposit(Long accountId,BigDecimal amount){}
 public record Transfer(Long sourceAccountId,Long targetAccountId,BigDecimal amount){}
 public record Payment(Long sourceAccountId,BigDecimal amount){}
 public record Response(UUID operationId,String type,Long sourceAccountId,Long targetAccountId,BigDecimal amount,
  String status,java.time.Instant createdAt,FinancialOperationResult result,String failureCode){}
 private final PaymentStore store;private final AccountPostingClient client;
 public PaymentController(PaymentStore store,AccountPostingClient client){this.store=store;this.client=client;}
 @PostMapping("/deposits")
 ResponseEntity<Response> deposit(@RequestHeader("Idempotency-Key")String key,@RequestBody Deposit body,JwtAuthenticationToken token){
  return execute(key,new FinancialOperationRequest(UUID.randomUUID(),"DEPOSIT",null,body.accountId(),body.amount()),token);
 }
 @PostMapping("/transfers")
 ResponseEntity<Response> transfer(@RequestHeader("Idempotency-Key")String key,@RequestBody Transfer body,JwtAuthenticationToken token){
  return execute(key,new FinancialOperationRequest(UUID.randomUUID(),"TRANSFER",body.sourceAccountId(),body.targetAccountId(),body.amount()),token);
 }
 @PostMapping
 ResponseEntity<Response> payment(@RequestHeader("Idempotency-Key")String key,@RequestBody Payment body,JwtAuthenticationToken token){
  return execute(key,new FinancialOperationRequest(UUID.randomUUID(),"PAYMENT",body.sourceAccountId(),null,body.amount()),token);
 }
 @GetMapping("/operations/{id}")
 Response get(@PathVariable UUID id,JwtAuthenticationToken token){return response(store.get(token.getToken().getSubject(),id));}
 private ResponseEntity<Response> execute(String key,FinancialOperationRequest request,JwtAuthenticationToken token) {
  var prepared=store.prepare(token.getToken().getSubject(),key,request);var operation=prepared.operation();
  if("FAILED".equals(operation.status()))throw new PaymentException(operation.failureStatus(),operation.failureCode());
  if(!"COMPLETED".equals(operation.status())) {
   try {
    var receipt=client.post("Bearer "+token.getToken().getTokenValue(),key,operation.request());
    operation=store.complete(operation,receipt);
   } catch(PaymentException failure) {
    if(failure.status()==400 || failure.status()==404 || failure.status()==409)store.fail(operation,failure);
    throw failure;
   }
  }
  return ResponseEntity.status(prepared.created()?201:200).body(response(operation));
 }
 private Response response(PaymentStore.Operation op) {
  var request=op.request();return new Response(op.operationId(),request.type(),request.sourceAccountId(),request.targetAccountId(),
   request.amount(),op.status(),op.createdAt(),op.result(),op.failureCode());
 }
}
