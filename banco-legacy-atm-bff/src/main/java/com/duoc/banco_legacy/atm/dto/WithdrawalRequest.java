package com.duoc.banco_legacy.atm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WithdrawalRequest(@NotNull @DecimalMin(value = "0.01") BigDecimal amount) {
}
