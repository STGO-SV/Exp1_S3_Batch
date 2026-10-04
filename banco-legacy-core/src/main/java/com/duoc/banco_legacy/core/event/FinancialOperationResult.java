package com.duoc.banco_legacy.core.event;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record FinancialOperationResult(UUID operationId,String type,Long sourceAccountId,Long targetAccountId,
        BigDecimal amount,String status,BigDecimal sourceBalanceAfter,BigDecimal targetBalanceAfter,Instant completedAt) {
    public void validateFor(FinancialOperationRequest request) {
        request.validate();
        if(!request.operationId().equals(operationId) || !request.type().equals(type)
                || !java.util.Objects.equals(request.sourceAccountId(),sourceAccountId)
                || !java.util.Objects.equals(request.targetAccountId(),targetAccountId)
                || amount==null || request.amount().compareTo(amount)!=0 || !"COMPLETED".equals(status) || completedAt==null
                || (sourceAccountId==null ? sourceBalanceAfter!=null : sourceBalanceAfter==null || sourceBalanceAfter.signum()<0)
                || (targetAccountId==null ? targetBalanceAfter!=null : targetBalanceAfter==null || targetBalanceAfter.signum()<0))
            throw new IllegalArgumentException("INVALID_RECEIPT");
    }
}
