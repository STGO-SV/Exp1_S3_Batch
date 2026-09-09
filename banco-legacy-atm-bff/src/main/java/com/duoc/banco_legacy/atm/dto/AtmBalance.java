package com.duoc.banco_legacy.atm.dto;

import java.math.BigDecimal;

public record AtmBalance(long accountId, BigDecimal availableBalance) {
}
