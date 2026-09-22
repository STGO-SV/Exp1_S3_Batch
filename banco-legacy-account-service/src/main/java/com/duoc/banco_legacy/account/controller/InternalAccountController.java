package com.duoc.banco_legacy.account.controller;

import com.duoc.banco_legacy.account.dto.AccountMovementResponse;
import com.duoc.banco_legacy.account.dto.AccountSummaryResponse;
import com.duoc.banco_legacy.account.dto.WebDashboardResponse;
import com.duoc.banco_legacy.account.dto.AtmBalanceResponse;
import com.duoc.banco_legacy.account.dto.AtmMovementResponse;
import com.duoc.banco_legacy.core.service.LegacyAccountQueryService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {
    private final LegacyAccountQueryService service;
    private final int mobileMovementsLimit;
    private final int webMovementsLimit;
    private final int webAnomaliesLimit;
    private final int atmMovementsLimit;

    public InternalAccountController(LegacyAccountQueryService service,
            @Value("${banking.limits.mobile-movements:5}") int mobileMovementsLimit,
            @Value("${banking.limits.web-movements:20}") int webMovementsLimit,
            @Value("${banking.limits.web-anomalies:10}") int webAnomaliesLimit,
            @Value("${banking.limits.atm-movements:3}") int atmMovementsLimit) {
        this.service = service;
        this.mobileMovementsLimit = mobileMovementsLimit;
        this.webMovementsLimit = webMovementsLimit;
        this.webAnomaliesLimit = webAnomaliesLimit;
        this.atmMovementsLimit = atmMovementsLimit;
    }

    @GetMapping("/{accountId}/summary")
    public AccountSummaryResponse summary(@PathVariable long accountId) {
        var summary = service.getSummary(accountId);
        return new AccountSummaryResponse(accountId, summary.balance(), summary.accountType());
    }

    @GetMapping("/{accountId}/movements")
    public List<AccountMovementResponse> movements(@PathVariable long accountId) {
        return service.getCompactMovements(accountId, mobileMovementsLimit).stream()
                .map(item -> new AccountMovementResponse(item.date(), item.type(), item.amount()))
                .toList();
    }

    @GetMapping("/{accountId}/web-dashboard")
    public WebDashboardResponse webDashboard(@PathVariable long accountId) {
        var balance = service.getBalance(accountId);
        var movements = service.getMovementsForKnownAccount(accountId, webMovementsLimit).stream()
                .map(item -> new WebDashboardResponse.MovementDetail(
                        item.date(), item.type(), item.amount(), item.description())).toList();
        var anomalies = service.getRecentAnomalies(webAnomaliesLimit).stream()
                .map(item -> new WebDashboardResponse.AnomalyDetail(
                        item.transactionId(), item.date(), item.type(), item.amount())).toList();
        return new WebDashboardResponse(accountId, balance.holderName(), balance.accountType(),
                balance.originalBalance(), balance.rate(), balance.processedBalance(), movements, anomalies);
    }

    @GetMapping("/{accountId}/atm-balance")
    public AtmBalanceResponse atmBalance(@PathVariable long accountId) {
        return new AtmBalanceResponse(accountId, service.getAvailableBalance(accountId));
    }

    @GetMapping("/{accountId}/atm-movements")
    public List<AtmMovementResponse> atmMovements(@PathVariable long accountId) {
        return service.getEssentialMovements(accountId, atmMovementsLimit).stream()
                .map(item -> new AtmMovementResponse(item.type(), item.amount())).toList();
    }
}
