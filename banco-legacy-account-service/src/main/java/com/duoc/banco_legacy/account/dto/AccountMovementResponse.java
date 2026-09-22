package com.duoc.banco_legacy.account.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountMovementResponse(LocalDate date, String type, BigDecimal amount) {}
