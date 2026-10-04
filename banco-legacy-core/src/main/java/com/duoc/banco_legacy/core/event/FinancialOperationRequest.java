package com.duoc.banco_legacy.core.event;
import java.math.BigDecimal;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public record FinancialOperationRequest(UUID operationId, String type, Long sourceAccountId,
        Long targetAccountId, BigDecimal amount) {
    public void validate() {
        if(operationId==null || type==null || amount==null || amount.signum()<=0
                || amount.stripTrailingZeros().scale()>2 || amount.precision()-amount.scale()>17)
            throw new IllegalArgumentException("INVALID_REQUEST");
        boolean valid=switch(type) {
            case "DEPOSIT" -> sourceAccountId==null && targetAccountId!=null;
            case "PAYMENT" -> sourceAccountId!=null && targetAccountId==null;
            case "TRANSFER" -> sourceAccountId!=null && targetAccountId!=null && !sourceAccountId.equals(targetAccountId);
            default -> false;
        };
        if(!valid || (sourceAccountId!=null && sourceAccountId<=0) || (targetAccountId!=null && targetAccountId<=0))
            throw new IllegalArgumentException("INVALID_REQUEST");
    }
    public String fingerprint() {
        validate();
        try {
            String canonical=type+"|"+sourceAccountId+"|"+targetAccountId+"|"+amount.stripTrailingZeros().toPlainString();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
