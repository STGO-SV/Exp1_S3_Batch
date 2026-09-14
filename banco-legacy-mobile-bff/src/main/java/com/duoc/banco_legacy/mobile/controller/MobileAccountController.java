package com.duoc.banco_legacy.mobile.controller;

import com.duoc.banco_legacy.core.service.LegacyAccountQueryService;
import com.duoc.banco_legacy.mobile.dto.MobileAccountSummary;
import com.duoc.banco_legacy.mobile.dto.MobileMovement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mobile/accounts")
public class MobileAccountController {
    private final LegacyAccountQueryService service;

    public MobileAccountController(LegacyAccountQueryService service) {
        this.service = service;
    }

    @GetMapping("/{accountId}/summary")
    public MobileAccountSummary summary(@PathVariable long accountId) {
        var balance = service.getSummary(accountId);
        return new MobileAccountSummary(accountId, balance.balance(), balance.accountType());
    }

    @GetMapping("/{accountId}/movements")
    public List<MobileMovement> movements(@PathVariable long accountId) {
        return service.getCompactMovements(accountId, 5).stream()
                .map(item -> new MobileMovement(item.date(), item.type(), item.amount()))
                .toList();
    }
}
