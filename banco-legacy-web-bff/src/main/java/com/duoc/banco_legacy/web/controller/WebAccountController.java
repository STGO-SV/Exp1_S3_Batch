package com.duoc.banco_legacy.web.controller;

import com.duoc.banco_legacy.core.service.LegacyAccountQueryService;
import com.duoc.banco_legacy.web.dto.WebAccountDashboard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/web/accounts")
public class WebAccountController {
    private final LegacyAccountQueryService service;

    public WebAccountController(LegacyAccountQueryService service) {
        this.service = service;
    }

    @GetMapping("/{accountId}/dashboard")
    public WebAccountDashboard dashboard(@PathVariable long accountId) {
        var balance = service.getBalance(accountId);
        var movements = service.getRecentMovements(accountId, 20).stream()
                .map(item -> new WebAccountDashboard.MovementDetail(
                        item.date(), item.type(), item.amount(), item.description()))
                .toList();
        var anomalies = service.getRecentAnomalies(10).stream()
                .map(item -> new WebAccountDashboard.AnomalyDetail(
                        item.transactionId(), item.date(), item.type(), item.amount()))
                .toList();
        return new WebAccountDashboard(accountId, balance.holderName(), balance.accountType(),
                balance.originalBalance(), balance.rate(), balance.processedBalance(), movements, anomalies);
    }
}
