package com.duoc.banco_legacy.atm.service;

import com.duoc.banco_legacy.atm.dto.WithdrawalResponse;
import com.duoc.banco_legacy.core.service.LegacyAccountQueryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AtmWithdrawalService {
    private final LegacyAccountQueryService accountService;

    public AtmWithdrawalService(LegacyAccountQueryService accountService) {
        this.accountService = accountService;
    }

    public WithdrawalResponse simulate(long accountId, BigDecimal amount) {
        var balance = accountService.getBalance(accountId).processedBalance();
        if (amount.compareTo(balance) > 0) {
            throw new WithdrawalRejectedException("Saldo insuficiente para el retiro solicitado");
        }
        return new WithdrawalResponse(accountId, amount, balance, balance.subtract(amount),
                "SIMULATED", "Simulación académica: el saldo legacy no fue modificado");
    }
}
