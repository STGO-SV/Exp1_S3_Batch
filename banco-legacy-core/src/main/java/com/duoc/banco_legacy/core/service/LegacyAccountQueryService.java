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
        requireAccount(accountId);
        return repository.findRecentMovements(accountId, limit);
    }

    public List<ProcessedTransaction> getRecentAnomalies(int limit) {
        return repository.findRecentAnomalies(limit);
    }

    /** El llamador ya obtuvo el saldo de esta cuenta en la misma solicitud (dashboard Web). */
    public List<AccountMovement> getMovementsForKnownAccount(long accountId, int limit) {
        return repository.findRecentMovements(accountId, limit);
    }

    public com.duoc.banco_legacy.core.model.AccountSummary getSummary(long accountId) {
        return repository.findSummary(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public java.math.BigDecimal getAvailableBalance(long accountId) {
        return repository.findAvailableBalance(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public List<com.duoc.banco_legacy.core.model.CompactMovement> getCompactMovements(long accountId, int limit) {
        requireAccount(accountId);
        return repository.findCompactMovements(accountId, limit);
    }

    public List<com.duoc.banco_legacy.core.model.EssentialMovement> getEssentialMovements(long accountId, int limit) {
        requireAccount(accountId);
        return repository.findEssentialMovements(accountId, limit);
    }

    private void requireAccount(long accountId) {
        if (!repository.accountExists(accountId)) throw new AccountNotFoundException(accountId);
    }

}
