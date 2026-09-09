package com.duoc.banco_legacy.core.model;

import java.math.BigDecimal;

public record AccountBalance(Long accountId, String holderName, BigDecimal originalBalance,
                             BigDecimal rate, BigDecimal processedBalance, String accountType) {
}
