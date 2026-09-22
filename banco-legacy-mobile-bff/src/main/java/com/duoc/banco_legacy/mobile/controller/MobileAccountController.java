package com.duoc.banco_legacy.mobile.controller;

import com.duoc.banco_legacy.mobile.client.AccountServiceClient;
import com.duoc.banco_legacy.mobile.dto.MobileAccountSummary;
import com.duoc.banco_legacy.mobile.dto.MobileMovement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mobile/accounts")
public class MobileAccountController {
    private final AccountServiceClient service;

    public MobileAccountController(AccountServiceClient service) {
        this.service = service;
    }

    @GetMapping("/{accountId}/summary")
    public MobileAccountSummary summary(@PathVariable long accountId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return service.getSummary(accountId, authorization);
    }

    @GetMapping("/{accountId}/movements")
    public List<MobileMovement> movements(@PathVariable long accountId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return service.getMovements(accountId, authorization);
    }
}
