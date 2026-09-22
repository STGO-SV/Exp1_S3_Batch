package com.duoc.banco_legacy.web.controller;

import com.duoc.banco_legacy.web.client.AccountServiceClient;
import com.duoc.banco_legacy.web.dto.WebAccountDashboard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpHeaders;

@RestController
@RequestMapping("/api/web/accounts")
public class WebAccountController {
    private final AccountServiceClient service;

    public WebAccountController(AccountServiceClient service) {
        this.service = service;
    }

    @GetMapping("/{accountId}/dashboard")
    public WebAccountDashboard dashboard(@PathVariable long accountId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return service.getDashboard(accountId, authorization);
    }
}
