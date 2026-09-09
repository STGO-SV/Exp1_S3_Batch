package com.duoc.banco_legacy.mobile.dto;

import java.math.BigDecimal;

public record MobileAccountSummary(long accountId, BigDecimal balance, String accountType) {
}
