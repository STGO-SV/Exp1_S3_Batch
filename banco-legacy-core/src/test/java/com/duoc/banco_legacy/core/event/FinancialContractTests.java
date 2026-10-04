package com.duoc.banco_legacy.core.event;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FinancialContractTests {
 FinancialOperationRequest deposit(String amount){return new FinancialOperationRequest(UUID.randomUUID(),"DEPOSIT",null,1L,new BigDecimal(amount));}
 @Test void canonicalPayloadIgnoresNumericFormattingAndGeneratedOperationId(){
  assertThat(deposit("10").fingerprint()).isEqualTo(deposit("10.00").fingerprint());
  assertThat(deposit("11").fingerprint()).isNotEqualTo(deposit("10").fingerprint());
 }
 @Test void rejectsInvalidStructureAndUnsupportedAmountPrecision(){
  for(String amount:new String[]{"0","-1","0.001","100000000000000000"})
   assertThatThrownBy(()->deposit(amount).validate()).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new FinancialOperationRequest(UUID.randomUUID(),"TRANSFER",1L,1L,BigDecimal.ONE).validate()).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new FinancialOperationRequest(UUID.randomUUID(),"PAYMENT",null,1L,BigDecimal.ONE).validate()).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void eventsRequireRealCompleteReceiptMatchingCanonicalRequest(){
  var request=deposit("10");var result=new FinancialOperationResult(request.operationId(),"DEPOSIT",null,1L,new BigDecimal("10"),"COMPLETED",null,new BigDecimal("10"),Instant.now());
  var event=new FinancialOperationCompletedEvent(UUID.randomUUID(),1,"technical-client",request.fingerprint(),result);
  event.validate();assertThat(event.eventType()).isEqualTo("DepositCompleted");
  assertThatThrownBy(()->new FinancialOperationCompletedEvent(UUID.randomUUID(),1,"technical-client","bad",result).validate()).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->result.validateFor(deposit("10"))).isInstanceOf(IllegalArgumentException.class);
 }
}
