package com.duoc.banco_legacy.payment.domain;
import com.duoc.banco_legacy.core.event.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
@Service
public class PaymentStore {
 public record Operation(UUID operationId,String actor,String idempotencyKey,FinancialOperationRequest request,
  String status,Instant createdAt,FinancialOperationResult result,String failureCode,Integer failureStatus) {}
 public record Prepared(Operation operation,boolean created) {}
 private final JdbcClient jdbc; private final ObjectMapper json; private final TransactionTemplate tx;
 public PaymentStore(JdbcClient jdbc,ObjectMapper json,PlatformTransactionManager manager) {
  this.jdbc=jdbc;this.json=json;this.tx=new TransactionTemplate(manager);
 }
 public Prepared prepare(String actor,String key,FinancialOperationRequest request) {
  request.validate();
  if(actor==null || actor.isBlank() || actor.length()>120 || key==null || !key.matches("[A-Za-z0-9._:-]{1,120}"))
   throw new IllegalArgumentException("INVALID_REQUEST");
  var existing=findKey(actor,key);
  if(existing.isPresent()) return replay(existing.get(),request);
  try {
   tx.executeWithoutResult(ignored -> jdbc.sql("INSERT INTO eft_payment_operation(operation_id,actor,idempotency_key,request_hash,request,status,created_at) VALUES (:id,:actor,:key,:hash,:request,'PENDING',:created)")
    .param("id",request.operationId()).param("actor",actor).param("key",key).param("hash",request.fingerprint())
    .param("request",encode(request)).param("created",Timestamp.from(Instant.now())).update());
   return new Prepared(get(actor,request.operationId()),true);
  } catch(DuplicateKeyException duplicate) {
   return replay(findKey(actor,key).orElseThrow(()->new PaymentException(409,"OPERATION_ID_CONFLICT")),request);
  }
 }
 private Prepared replay(Operation operation,FinancialOperationRequest request) {
  if(!operation.request().fingerprint().equals(request.fingerprint()))throw new PaymentException(409,"IDEMPOTENCY_CONFLICT");
  return new Prepared(operation,false);
 }
 private Optional<Operation> findKey(String actor,String key) {
  return jdbc.sql("SELECT * FROM eft_payment_operation WHERE actor=:actor AND idempotency_key=:key")
   .param("actor",actor).param("key",key).query(this::map).optional();
 }
 public Operation get(String actor,UUID id) {
  return jdbc.sql("SELECT * FROM eft_payment_operation WHERE actor=:actor AND operation_id=:id").param("actor",actor).param("id",id)
   .query(this::map).optional().orElseThrow(()->new PaymentException(404,"OPERATION_NOT_FOUND"));
 }
 private Operation map(java.sql.ResultSet rs,int n) throws java.sql.SQLException {
  return new Operation(UUID.fromString(rs.getString("operation_id")),rs.getString("actor"),rs.getString("idempotency_key"),
   decode(rs.getString("request"),FinancialOperationRequest.class),rs.getString("status"),rs.getTimestamp("created_at").toInstant(),
   rs.getString("receipt")==null?null:decode(rs.getString("receipt"),FinancialOperationResult.class),rs.getString("failure_code"),
   (Integer)rs.getObject("failure_status"));
 }
 public Operation complete(Operation operation,FinancialOperationResult result) {
  result.validateFor(operation.request());
  tx.executeWithoutResult(ignored -> {
   var current=jdbc.sql("SELECT * FROM eft_payment_operation WHERE operation_id=:id FOR UPDATE").param("id",operation.operationId()).query(this::map).single();
   if("FAILED".equals(current.status()))throw new IllegalArgumentException("CONTRADICTORY_RECEIPT");
   if("COMPLETED".equals(current.status())) {
    if(!encode(current.result()).equals(encode(result)))throw new IllegalArgumentException("CONTRADICTORY_RECEIPT");
    return;
   }
   jdbc.sql("UPDATE eft_payment_operation SET status='COMPLETED',receipt=:receipt,failure_code=NULL,failure_status=NULL WHERE operation_id=:id")
    .param("id",operation.operationId()).param("receipt",encode(result)).update();
  });
  return get(operation.actor(),operation.operationId());
 }
 public void fail(Operation operation,PaymentException failure) {
  jdbc.sql("UPDATE eft_payment_operation SET status='FAILED',failure_code=:code,failure_status=:status WHERE operation_id=:id AND status='PENDING'")
   .param("id",operation.operationId()).param("code",failure.code()).param("status",failure.status()).update();
 }
 public void acceptEvent(FinancialOperationCompletedEvent event) {
  event.validate();
  try {
   tx.executeWithoutResult(ignored -> {
    if(jdbc.sql("SELECT COUNT(*) FROM eft_payment_event_audit WHERE event_id=:id").param("id",event.eventId()).query(Long.class).single()>0)return;
    var operation=jdbc.sql("SELECT * FROM eft_payment_operation WHERE operation_id=:id FOR UPDATE")
     .param("id",event.result().operationId()).query(this::map).optional();
    if(operation.isPresent()) {
     if(!operation.get().actor().equals(event.actor()) || !operation.get().request().fingerprint().equals(event.requestHash()))
      throw new IllegalArgumentException("INVALID_CORRELATION");
     complete(operation.get(),event.result());
    }
    jdbc.sql("INSERT INTO eft_payment_event_audit(event_id,operation_id,payload,received_at) VALUES (:event,:operation,:payload,:received)")
     .param("event",event.eventId()).param("operation",event.result().operationId()).param("payload",encode(event)).param("received",Timestamp.from(Instant.now())).update();
   });
  } catch(DuplicateKeyException duplicate) {
   // Only the audit event key can collide; the first transaction already applied the projection.
   if(jdbc.sql("SELECT COUNT(*) FROM eft_payment_event_audit WHERE event_id=:id").param("id",event.eventId()).query(Long.class).single()==0)throw duplicate;
  }
 }
 private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
 private <T>T decode(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception ex){throw new IllegalStateException(ex);}}
}
