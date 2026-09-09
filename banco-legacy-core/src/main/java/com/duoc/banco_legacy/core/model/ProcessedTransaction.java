package com.duoc.banco_legacy.core.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProcessedTransaction(Long transactionId, LocalDate date, BigDecimal amount,
                                   String type, boolean anomaly) {
}
