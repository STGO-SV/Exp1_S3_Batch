package com.duoc.banco_legacy.account;
import com.duoc.banco_legacy.account.financial.*;
import com.duoc.banco_legacy.core.event.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.springframework.kafka.core.KafkaTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialOutboxTests {
    FinancialOutboxStore store;
    KafkaTemplate<String,Object> kafka;
    FinancialOutboxStore.Claim claim;
    FinancialOutboxPublisher publisher;
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        store=mock(FinancialOutboxStore.class);kafka=mock(KafkaTemplate.class);
        var json=new ObjectMapper().findAndRegisterModules();
        var receipt=new FinancialOperationResult(UUID.randomUUID(),"DEPOSIT",null,301L,
                new BigDecimal("10.00"),"COMPLETED",null,new BigDecimal("10.00"),Instant.now());
        var event=new FinancialOperationCompletedEvent(UUID.randomUUID(),1,"operator",new FinancialOperationRequest(receipt.operationId(),"DEPOSIT",null,301L,new BigDecimal("10.00")).fingerprint(),receipt);
        claim=new FinancialOutboxStore.Claim(event.eventId(),json.writeValueAsString(event),"worker",UUID.randomUUID());
        when(store.claim(anyString(),eq(30))).thenReturn(Optional.of(claim),Optional.empty());
        publisher=new FinancialOutboxPublisher(store,json,kafka,"banco.operaciones.completadas.v1","worker",30,1);
    }
    @Test void acknowledgedSendPublishesOnlyAfterAck() {
        when(kafka.send(anyString(),anyString(),any())).thenReturn(CompletableFuture.completedFuture(null));
        when(store.published(claim)).thenReturn(true);
        publisher.publish();
        var order=inOrder(store,kafka);
        order.verify(store).claim(anyString(),eq(30));
        order.verify(kafka).send(anyString(),anyString(),any());
        order.verify(store).published(claim);
        verify(store,never()).retry(any(),anyString());
    }
    @Test void brokerFailureReleasesOwnedClaimForRetryWithoutPublished() {
        when(kafka.send(anyString(),anyString(),any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException()));
        publisher.publish();
        verify(store).retry(eq(claim),anyString());verify(store,never()).published(any());
    }
    @Test void anotherWorkerClaimMeansNoSend() {
        when(store.claim(anyString(),eq(30))).thenReturn(Optional.empty());
        publisher.publish();verifyNoInteractions(kafka);
    }
    @Test void pendingAckDoesNotMarkPublished() throws Exception {
        var ack=new CompletableFuture<org.springframework.kafka.support.SendResult<String,Object>>();
        when(kafka.send(anyString(),anyString(),any())).thenReturn(ack);
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var task=executor.submit(publisher::publish);
            verify(kafka,timeout(2000)).send(anyString(),anyString(),any());
            verify(store,never()).published(any());
            ack.complete(null);task.get(3,java.util.concurrent.TimeUnit.SECONDS);
            verify(store).published(claim);
        }
    }
}
