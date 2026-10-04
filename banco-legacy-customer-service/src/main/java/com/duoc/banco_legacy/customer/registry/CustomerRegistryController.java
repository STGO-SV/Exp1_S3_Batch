package com.duoc.banco_legacy.customer.registry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/customers")
public class CustomerRegistryController {
    public record CreateRequest(@NotBlank @Size(max=120) String name) {}
    public record UpdateRequest(@NotBlank @Size(max=120) String name,@NotNull @PositiveOrZero Long version) {}
    private final CustomerRegistryService registry;
    private final AccountRegistryClient accounts;
    public CustomerRegistryController(CustomerRegistryService registry,AccountRegistryClient accounts) {
        this.registry=registry;this.accounts=accounts;
    }
    @PutMapping("/{id}")
    public ResponseEntity<CustomerRegistryService.Customer> create(@PathVariable UUID id,@Valid @RequestBody CreateRequest request) {
        var result=registry.create(id,request.name());
        return result.created()?ResponseEntity.created(URI.create("/api/customers/"+id)).body(result.customer())
                :ResponseEntity.ok(result.customer());
    }
    @GetMapping("/{id}")
    public CustomerRegistryService.Customer get(@PathVariable UUID id) {return registry.get(id);}
    @PatchMapping("/{id}")
    public CustomerRegistryService.Customer update(@PathVariable UUID id,@Valid @RequestBody UpdateRequest request) {
        return registry.update(id,request.name(),request.version());
    }
    @GetMapping("/{id}/accounts")
    public List<AccountRegistryClient.Account> accounts(@PathVariable UUID id,
            @RequestParam(defaultValue="20") int limit,@RequestParam(defaultValue="0") int offset,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if(limit<1 || limit>100 || offset<0)
            throw new RegistryException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Paginación inválida");
        registry.get(id);
        return accounts.accounts(id,limit,offset,authorization);
    }
}