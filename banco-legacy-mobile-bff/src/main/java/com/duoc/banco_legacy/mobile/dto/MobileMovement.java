package com.duoc.banco_legacy.mobile.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MobileMovement(LocalDate date, String type, BigDecimal amount) {
}
