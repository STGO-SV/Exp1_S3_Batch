package com.duoc.banco_legacy.account.registry;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
@Component
public class CustomerRegistryClient {
    public record Customer(UUID customerId,String name,long version) {}
    private final RestClient client;
    private final CircuitBreakerFactory<?,?> circuits;
    private final String base;
    public CustomerRegistryClient(RestClient.Builder builder,CircuitBreakerFactory<?,?> circuits,
            @Value("${banking.customer-service-name:banco-legacy-customer-service}") String name) {
        this.client=builder.build();this.circuits=circuits;this.base="https://"+name;
    }
    public void require(UUID customerId,String authorization) {
        Customer result=circuits.create("customerRegistry").run(
                ()->client.get().uri(base+"/api/customers/{id}",customerId).header("Authorization",authorization)
                        .retrieve().body(Customer.class), failure->{throw translate(failure);});
        if(result==null || !customerId.equals(result.customerId()))
            throw new RegistryException(HttpStatus.SERVICE_UNAVAILABLE,"CUSTOMER_UNAVAILABLE","Respuesta de cliente inválida");
    }
    private RegistryException translate(Throwable failure) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()) {
            if(cause instanceof HttpClientErrorException error) {
                if(error.getStatusCode().value()==404) return RegistryException.missing("CUSTOMER_NOT_FOUND");
                if(error.getStatusCode().value()==401 || error.getStatusCode().value()==403)
                    return new RegistryException(HttpStatus.FORBIDDEN,"DEPENDENCY_FORBIDDEN","Permiso insuficiente para consultar clientes");
            }
        }
        return new RegistryException(HttpStatus.SERVICE_UNAVAILABLE,"CUSTOMER_UNAVAILABLE","Clientes no disponibles");
    }
}