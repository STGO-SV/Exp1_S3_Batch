package com.duoc.banco_legacy.core.model;
import java.math.BigDecimal;
import java.time.LocalDate;
public record CompactMovement(LocalDate date, String type, BigDecimal amount) {}
