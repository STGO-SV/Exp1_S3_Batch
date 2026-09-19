package com.duoc.banco_legacy.atm.service;

import com.duoc.banco_legacy.atm.dto.WithdrawalResponse;
import com.duoc.banco_legacy.core.exception.AccountNotFoundException;
import com.duoc.banco_legacy.core.repository.LegacyAccountWithdrawalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AtmWithdrawalService {
    private final LegacyAccountWithdrawalRepository repository;

    public AtmWithdrawalService(LegacyAccountWithdrawalRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WithdrawalResponse withdraw(long accountId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.precision() - amount.scale() > 17) {
            throw new WithdrawalRejectedException("Monto inválido: use un valor positivo con hasta dos decimales");
        }
        var lockedBalance = repository.lockLatestBalance(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        var balanceBefore = lockedBalance.balance();
        if (amount.compareTo(balanceBefore) > 0) {
            throw new WithdrawalRejectedException("Saldo insuficiente para el retiro solicitado");
        }
        var balanceAfter = balanceBefore.subtract(amount);
        repository.updateBalance(lockedBalance.rowId(), balanceAfter);
        repository.insertWithdrawalMovement(accountId, amount);
        return new WithdrawalResponse(accountId, amount, balanceBefore, balanceAfter,
                "COMPLETED", "Retiro completado");
    }
}
