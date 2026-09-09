package com.duoc.banco_legacy.core.service;

import com.duoc.banco_legacy.core.exception.AccountNotFoundException;
import com.duoc.banco_legacy.core.model.AccountBalance;
import com.duoc.banco_legacy.core.model.AccountMovement;
import com.duoc.banco_legacy.core.model.ProcessedTransaction;
import com.duoc.banco_legacy.core.repository.LegacyAccountReadRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LegacyAccountQueryService {

    private final LegacyAccountReadRepository repository;

    public LegacyAccountQueryService(LegacyAccountReadRepository repository) {
        this.repository = repository;
    }

    public AccountBalance getBalance(long accountId) {
        return repository.findLatestBalance(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public List<AccountMovement> getRecentMovements(long accountId, int limit) {
        getBalance(accountId);
        return repository.findRecentMovements(accountId, limit);
    }

    public List<ProcessedTransaction> getRecentAnomalies(int limit) {
        return repository.findRecentAnomalies(limit);
    }
}
