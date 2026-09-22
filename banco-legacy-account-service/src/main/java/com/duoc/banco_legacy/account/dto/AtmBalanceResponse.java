package com.duoc.banco_legacy.account.dto;

import java.math.BigDecimal;

public record AtmBalanceResponse(long accountId, BigDecimal availableBalance) {}
