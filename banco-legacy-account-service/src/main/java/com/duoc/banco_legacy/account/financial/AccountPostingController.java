package com.duoc.banco_legacy.account.financial;
import com.duoc.banco_legacy.core.event.*;
import com.duoc.banco_legacy.account.registry.AccountRegistryService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/internal/accounts")
public class AccountPostingController {
 private final AccountPostingService posting; private final AccountRegistryService registry;
 public AccountPostingController(AccountPostingService posting,AccountRegistryService registry) {this.posting=posting;this.registry=registry;}
 @PostMapping("/postings")
 ResponseEntity<FinancialOperationResult> post(@RequestHeader("Idempotency-Key") String key,
   @RequestBody FinancialOperationRequest request,JwtAuthenticationToken token) {
  var posted=posting.post(token.getToken().getSubject(),key,request);
  return ResponseEntity.status(posted.created()?201:200).body(posted.result());
 }
 @GetMapping("/postings/{id}")
 FinancialOperationResult get(@PathVariable UUID id,JwtAuthenticationToken token) {return posting.get(token.getToken().getSubject(),id);}
 @GetMapping("/{id}/operational-balance")
 AccountRegistryService.Account balance(@PathVariable long id) {if(id<=0)throw new IllegalArgumentException();return registry.get(id);}
}
