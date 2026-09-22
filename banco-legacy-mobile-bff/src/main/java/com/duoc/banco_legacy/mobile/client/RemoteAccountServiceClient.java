package com.duoc.banco_legacy.mobile.client;

import com.duoc.banco_legacy.mobile.dto.MobileAccountSummary;
import com.duoc.banco_legacy.mobile.dto.MobileMovement;
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
            @Value("${banking.account-service-name:banco-legacy-account-service}") String serviceName) {
        this.client = builder.build();
        this.circuitBreakers = circuitBreakers;
        this.baseUrl = "https://" + serviceName;
    }

    @Override
    public MobileAccountSummary getSummary(long accountId, String authorization) {
        return circuitBreakers.create("accountService").run(
                () -> client.get().uri(baseUrl + "/internal/accounts/{id}/summary", accountId)
                        .header("Authorization", authorization).retrieve()
                        .onStatus(status -> status.value() == 404, (request, response) -> {
                            throw new AccountNotFoundException(accountId);
                        }).body(MobileAccountSummary.class),
                failure -> fallback(accountId, failure));
    }

    @Override
    public List<MobileMovement> getMovements(long accountId, String authorization) {
        MobileMovement[] response = circuitBreakers.create("accountService").run(
                () -> client.get().uri(baseUrl + "/internal/accounts/{id}/movements", accountId)
                        .header("Authorization", authorization).retrieve()
                        .onStatus(status -> status.value() == 404, (request, remoteResponse) -> {
                            throw new AccountNotFoundException(accountId);
                        }).body(MobileMovement[].class),
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
