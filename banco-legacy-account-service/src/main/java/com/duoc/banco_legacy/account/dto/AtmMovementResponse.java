package com.duoc.banco_legacy.account.dto;

import java.math.BigDecimal;

public record AtmMovementResponse(String type, BigDecimal amount) {}
