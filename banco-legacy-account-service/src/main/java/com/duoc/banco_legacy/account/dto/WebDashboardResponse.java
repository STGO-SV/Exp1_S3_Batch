package com.duoc.banco_legacy.account.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WebDashboardResponse(long accountId, String holderName, String accountType,
        BigDecimal originalBalance, BigDecimal appliedRate, BigDecimal processedBalance,
        List<MovementDetail> movements, List<AnomalyDetail> recentAnomalies) {
    public record MovementDetail(LocalDate date, String type, BigDecimal amount, String description) {}
    public record AnomalyDetail(long transactionId, LocalDate date, String type, BigDecimal amount) {}
}
