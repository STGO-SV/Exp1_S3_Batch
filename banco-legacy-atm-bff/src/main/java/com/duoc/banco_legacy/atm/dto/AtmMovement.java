package com.duoc.banco_legacy.atm.dto;

import java.math.BigDecimal;

public record AtmMovement(String type, BigDecimal amount) {
}
