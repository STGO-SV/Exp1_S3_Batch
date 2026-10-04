package com.duoc.banco_legacy.payment.domain;
import com.duoc.banco_legacy.core.event.FinancialOperationCompletedEvent;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
@Component
@ConditionalOnProperty(name="banking.kafka.enabled",havingValue="true",matchIfMissing=true)
public class FinancialEventConsumer {
 private final PaymentStore store;
 public FinancialEventConsumer(PaymentStore store){this.store=store;}
 @KafkaListener(topics="${banking.kafka.financial-topic:banco.operaciones.completadas.v1}",groupId="financial-payment-audit")
 public void accept(FinancialOperationCompletedEvent event){store.acceptEvent(event);}
}
