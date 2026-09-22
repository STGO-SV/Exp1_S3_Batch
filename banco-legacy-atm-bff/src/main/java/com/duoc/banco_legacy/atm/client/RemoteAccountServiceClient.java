package com.duoc.banco_legacy.atm.client;

import com.duoc.banco_legacy.atm.dto.AtmBalance;
import com.duoc.banco_legacy.atm.dto.AtmMovement;
import com.duoc.banco_legacy.core.exception.AccountNotFoundException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RemoteAccountServiceClient implements AccountServiceClient {
    private final RestClient client;
    private final CircuitBreakerFactory<?, ?> circuitBreakers;
    private final String baseUrl;

    public RemoteAccountServiceClient(RestClient.Builder builder, CircuitBreakerFactory<?, ?> circuitBreakers,
            @Value("${banking.account-service-name}") String serviceName) {
        this.client = builder.build();
        this.circuitBreakers = circuitBreakers;
        this.baseUrl = "https://" + serviceName;
    }

    @Override
    public AtmBalance getBalance(long accountId, String authorization) {
        return circuitBreakers.create("accountService").run(
                () -> client.get().uri(baseUrl + "/internal/accounts/{id}/atm-balance", accountId)
                        .header("Authorization", authorization).retrieve()
                        .onStatus(status -> status.value() == 404, (request, response) -> {
                            throw new AccountNotFoundException(accountId);
                        }).body(AtmBalance.class),
                failure -> fallback(accountId, failure));
    }

    @Override
    public List<AtmMovement> getMovements(long accountId, String authorization) {
        AtmMovement[] response = circuitBreakers.create("accountService").run(
                () -> client.get().uri(baseUrl + "/internal/accounts/{id}/atm-movements", accountId)
                        .header("Authorization", authorization).retrieve()
                        .onStatus(status -> status.value() == 404, (request, remoteResponse) -> {
                            throw new AccountNotFoundException(accountId);
                        }).body(AtmMovement[].class),
                failure -> fallback(accountId, failure));
        return response == null ? List.of() : Arrays.asList(response);
    }

    private <T> T fallback(long accountId, Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof AccountNotFoundException notFound) throw notFound;
            if (cause instanceof org.springframework.web.client.HttpClientErrorException clientError
                    && clientError.getStatusCode().value() == 404) throw new AccountNotFoundException(accountId);
        }
        throw new AccountServiceUnavailableException(failure);
    }
}
