package com.duoc.banco_legacy.customer.registry;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
@Component
public class AccountRegistryClient {
    public record Account(long accountId,String accountType,String status,long version,List<UUID> customerIds) {}
    private final RestClient client;
    private final CircuitBreakerFactory<?,?> circuits;
    private final String base;
    public AccountRegistryClient(RestClient.Builder builder,CircuitBreakerFactory<?,?> circuits,
            @Value("${banking.account-service-name:banco-legacy-account-service}") String name) {
        this.client=builder.build();this.circuits=circuits;this.base="https://"+name;
    }
    public List<Account> accounts(UUID customerId,int limit,int offset,String authorization) {
        List<Account> result=circuits.create("accountRegistry").run(
                ()->client.get().uri(base+"/api/accounts?customerId={customer}&limit={limit}&offset={offset}",customerId,limit,offset)
                        .header("Authorization",authorization).retrieve().body(new ParameterizedTypeReference<List<Account>>() {}),
                failure->{throw translate(failure);});
        if(result==null) throw new RegistryException(HttpStatus.SERVICE_UNAVAILABLE,"ACCOUNT_UNAVAILABLE","Respuesta de cuentas inválida");
        return result;
    }
    private RegistryException translate(Throwable failure) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()) {
            if(cause instanceof HttpClientErrorException error &&
                    (error.getStatusCode().value()==401 || error.getStatusCode().value()==403))
                return new RegistryException(HttpStatus.FORBIDDEN,"DEPENDENCY_FORBIDDEN","Permiso insuficiente para consultar cuentas");
        }
        return new RegistryException(HttpStatus.SERVICE_UNAVAILABLE,"ACCOUNT_UNAVAILABLE","Cuentas no disponibles");
    }
}