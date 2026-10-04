package com.duoc.banco_legacy.core.event;
import java.util.UUID;
public record FinancialOperationCompletedEvent(UUID eventId,int version,String actor,String requestHash,
        FinancialOperationResult result) {
    public String eventType() {
        return switch(result.type()) { case "DEPOSIT" -> "DepositCompleted"; case "TRANSFER" -> "TransferCompleted";
            case "PAYMENT" -> "PaymentCompleted"; default -> throw new IllegalArgumentException("INVALID_EVENT"); };
    }
    public void validate() {
        if(eventId==null || version!=1 || actor==null || actor.isBlank() || actor.length()>120 || result==null)
            throw new IllegalArgumentException("INVALID_EVENT");
        var request=new FinancialOperationRequest(result.operationId(),result.type(),result.sourceAccountId(),result.targetAccountId(),result.amount());
        result.validateFor(request);
        if(!request.fingerprint().equals(requestHash)) throw new IllegalArgumentException("INVALID_EVENT");
    }
}
