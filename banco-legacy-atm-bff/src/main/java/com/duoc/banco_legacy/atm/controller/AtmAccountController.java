package com.duoc.banco_legacy.atm.controller;

import com.duoc.banco_legacy.atm.dto.AtmBalance;
import com.duoc.banco_legacy.atm.dto.AtmMovement;
import com.duoc.banco_legacy.atm.dto.WithdrawalRequest;
import com.duoc.banco_legacy.atm.dto.WithdrawalResponse;
import com.duoc.banco_legacy.atm.service.AtmWithdrawalService;
import com.duoc.banco_legacy.core.service.LegacyAccountQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/atm/accounts")
public class AtmAccountController {
    private final LegacyAccountQueryService accountService;
    private final AtmWithdrawalService withdrawalService;

    public AtmAccountController(LegacyAccountQueryService accountService, AtmWithdrawalService withdrawalService) {
        this.accountService = accountService;
        this.withdrawalService = withdrawalService;
    }

    @GetMapping("/{accountId}/balance")
    public AtmBalance balance(@PathVariable long accountId) {
        return new AtmBalance(accountId, accountService.getAvailableBalance(accountId));
    }

    @GetMapping("/{accountId}/movements")
    public List<AtmMovement> movements(@PathVariable long accountId) {
        return accountService.getEssentialMovements(accountId, 3).stream()
                .map(item -> new AtmMovement(item.type(), item.amount()))
                .toList();
    }

    @PostMapping("/{accountId}/withdrawals")
    public WithdrawalResponse withdraw(@PathVariable long accountId,
                                       @Valid @RequestBody WithdrawalRequest request) {
        return withdrawalService.simulate(accountId, request.amount());
    }
}
