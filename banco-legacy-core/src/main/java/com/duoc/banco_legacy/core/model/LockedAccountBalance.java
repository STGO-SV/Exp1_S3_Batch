package com.duoc.banco_legacy.core.model;

import java.math.BigDecimal;

public record LockedAccountBalance(long rowId, BigDecimal balance) {
}
