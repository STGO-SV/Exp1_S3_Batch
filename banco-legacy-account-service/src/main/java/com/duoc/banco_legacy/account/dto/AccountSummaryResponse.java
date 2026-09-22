package com.duoc.banco_legacy.account.dto;

import java.math.BigDecimal;

public record AccountSummaryResponse(long accountId, BigDecimal balance, String accountType) {}
