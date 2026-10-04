package com.duoc.banco_legacy.account.registry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/accounts")
public class AccountRegistryController {
    public record OpenRequest(@NotNull @Pattern(regexp="ahorro|prestamo") String accountType,
            @NotEmpty List<@NotNull UUID> customerIds) {}
    public record MaintenanceRequest(@NotNull @Pattern(regexp="ahorro|prestamo") String accountType,
            @NotNull @PositiveOrZero Long version) {}
    public record ClosureRequest(@NotNull @PositiveOrZero Long version) {}
    private final AccountRegistryService registry;
    private final CustomerRegistryClient customers;
    public AccountRegistryController(AccountRegistryService registry,CustomerRegistryClient customers) {
        this.registry=registry;this.customers=customers;
    }
    private void validId(long id) {
        if(id<=0) throw new RegistryException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Identificador de cuenta inválido");
    }
    @PutMapping("/{id}")
    public ResponseEntity<AccountRegistryService.Account> open(@PathVariable long id,
            @Valid @RequestBody OpenRequest request,@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        validId(id);
        if(new HashSet<>(request.customerIds()).size()!=request.customerIds().size())
            throw new RegistryException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Titulares duplicados");
        // Validation occurs before the local transaction; replay does not depend on remote availability.
        if(registry.find(id).isEmpty()) for(UUID customer:request.customerIds()) customers.require(customer,authorization);
        var result=registry.open(id,request.accountType(),request.customerIds());
        return result.created()?ResponseEntity.created(URI.create("/api/accounts/"+id)).body(result.account())
                :ResponseEntity.ok(result.account());
    }
    @GetMapping("/{id}")
    public AccountRegistryService.Account get(@PathVariable long id) {validId(id);return registry.get(id);}
    @PatchMapping("/{id}")
    public AccountRegistryService.Account update(@PathVariable long id,@Valid @RequestBody MaintenanceRequest request) {
        validId(id);return registry.update(id,request.accountType(),request.version());
    }
    @PostMapping("/{id}/closure")
    public AccountRegistryService.Account close(@PathVariable long id,@Valid @RequestBody ClosureRequest request) {
        validId(id);return registry.close(id,request.version());
    }
    @GetMapping
    public List<AccountRegistryService.Account> forCustomer(@RequestParam UUID customerId,
            @RequestParam(defaultValue="20") int limit,@RequestParam(defaultValue="0") int offset) {
        if(limit<1 || limit>100 || offset<0)
            throw new RegistryException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","Paginación inválida");
        return registry.forCustomer(customerId,limit,offset);
    }
}