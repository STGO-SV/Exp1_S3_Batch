package com.duoc.banco_legacy.atm.controller;

import com.duoc.banco_legacy.atm.dto.AtmBalance;
import com.duoc.banco_legacy.atm.dto.AtmMovement;
import com.duoc.banco_legacy.atm.dto.WithdrawalRequest;
import com.duoc.banco_legacy.atm.dto.WithdrawalResponse;
import com.duoc.banco_legacy.atm.service.AtmWithdrawalService;
import com.duoc.banco_legacy.atm.client.AccountServiceClient;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpHeaders;

import java.util.List;

@RestController
@RequestMapping("/api/atm/accounts")
public class AtmAccountController {
    private final AccountServiceClient accountService;
    private final AtmWithdrawalService withdrawalService;

    public AtmAccountController(AccountServiceClient accountService, AtmWithdrawalService withdrawalService) {
        this.accountService = accountService;
        this.withdrawalService = withdrawalService;
    }

    @GetMapping("/{accountId}/balance")
    public AtmBalance balance(@PathVariable long accountId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return accountService.getBalance(accountId, authorization);
    }

    @GetMapping("/{accountId}/movements")
    public List<AtmMovement> movements(@PathVariable long accountId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return accountService.getMovements(accountId, authorization);
    }

    @PostMapping("/{accountId}/withdrawals")
    public WithdrawalResponse withdraw(@PathVariable long accountId,
                                       @Valid @RequestBody WithdrawalRequest request) {
        return withdrawalService.withdraw(accountId, request.amount());
    }
}
