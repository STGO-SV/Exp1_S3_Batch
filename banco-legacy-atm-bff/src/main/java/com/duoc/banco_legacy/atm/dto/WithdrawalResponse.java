package com.duoc.banco_legacy.atm.dto;

import java.math.BigDecimal;

public record WithdrawalResponse(long accountId, BigDecimal requestedAmount,
                                 BigDecimal balanceBefore, BigDecimal balanceAfter,
                                 String status, String message) {
}
