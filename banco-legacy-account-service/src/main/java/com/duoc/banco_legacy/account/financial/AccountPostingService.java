package com.duoc.banco_legacy.account.financial;
import com.duoc.banco_legacy.core.event.*;
import com.duoc.banco_legacy.account.registry.RegistryException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
@Service
public class AccountPostingService {
 public record Posted(FinancialOperationResult result,boolean created) {}
 private record Balance(String status,BigDecimal amount) {}
 private final JdbcClient jdbc; private final ObjectMapper json; private final TransactionTemplate tx;
 public AccountPostingService(JdbcClient jdbc,ObjectMapper json,PlatformTransactionManager manager) {
  this.jdbc=jdbc;this.json=json;this.tx=new TransactionTemplate(manager);
 }
 public Posted post(String actor,String key,FinancialOperationRequest request) {
  request.validate();
  if(actor==null || actor.isBlank() || actor.length()>120 || key==null || !key.matches("[A-Za-z0-9._:-]{1,120}"))
   throw new IllegalArgumentException("INVALID_REQUEST");
  try {
   return tx.execute(ignored -> {
    // Claim the key before changing balances; concurrent duplicates wait on the unique constraint.
    jdbc.sql("INSERT INTO eft_account_posting(operation_id,actor,idempotency_key,request_hash,receipt) VALUES (:id,:actor,:key,:hash,'')")
     .param("id",request.operationId()).param("actor",actor).param("key",key).param("hash",request.fingerprint()).update();
    var ids=new TreeSet<Long>();
    if(request.sourceAccountId()!=null) ids.add(request.sourceAccountId());
    if(request.targetAccountId()!=null) ids.add(request.targetAccountId());
    Map<Long,BigDecimal> balances=new HashMap<>();
    for(long id:ids) {
     Balance balance=jdbc.sql("SELECT a.status,b.balance FROM eft_account a JOIN eft_account_balance b ON a.account_id=b.account_id WHERE a.account_id=:id FOR UPDATE")
      .param("id",id).query((rs,n)->new Balance(rs.getString(1),rs.getBigDecimal(2))).optional()
      .orElseThrow(()->RegistryException.missing("ACCOUNT_NOT_FOUND"));
     if(!"OPEN".equals(balance.status())) throw RegistryException.conflict("ACCOUNT_CLOSED");
     balances.put(id,balance.amount());
    }
    BigDecimal source=null,target=null;
    if(request.sourceAccountId()!=null) {
     source=balances.get(request.sourceAccountId()).subtract(request.amount());
     if(source.signum()<0) throw RegistryException.conflict("INSUFFICIENT_BALANCE");
     balances.put(request.sourceAccountId(),source);
    }
    if(request.targetAccountId()!=null) {
     target=balances.get(request.targetAccountId()).add(request.amount());
     if(target.precision()-target.scale()>17) throw RegistryException.conflict("BALANCE_CAPACITY_EXCEEDED");
     balances.put(request.targetAccountId(),target);
    }
    for(long id:ids) {
     jdbc.sql("UPDATE eft_account_balance SET balance=:balance WHERE account_id=:id").param("id",id).param("balance",balances.get(id)).update();
     jdbc.sql("UPDATE eft_account SET version=version+1 WHERE account_id=:id").param("id",id).update();
    }
    var result=new FinancialOperationResult(request.operationId(),request.type(),request.sourceAccountId(),request.targetAccountId(),
     request.amount().setScale(2),"COMPLETED",source,target,Instant.now());
    jdbc.sql("UPDATE eft_account_posting SET receipt=:receipt WHERE operation_id=:id")
     .param("receipt",serialize(result)).param("id",request.operationId()).update();
    var event=new FinancialOperationCompletedEvent(UUID.randomUUID(),1,actor,request.fingerprint(),result);
    jdbc.sql("INSERT INTO eft_financial_outbox(event_id,operation_id,payload) VALUES (:id,:operation,:payload)")
     .param("id",event.eventId()).param("operation",request.operationId()).param("payload",serialize(event)).update();
    return new Posted(result,true);
   });
  } catch(DuplicateKeyException duplicate) {
   var rows=jdbc.sql("SELECT request_hash,receipt FROM eft_account_posting WHERE actor=:actor AND idempotency_key=:key")
    .param("actor",actor).param("key",key).query((rs,n)->Map.entry(rs.getString(1),rs.getString(2))).list();
   if(rows.isEmpty()) throw RegistryException.conflict("OPERATION_ID_CONFLICT");
   if(!rows.getFirst().getKey().equals(request.fingerprint())) throw RegistryException.conflict("IDEMPOTENCY_CONFLICT");
   return new Posted(parse(rows.getFirst().getValue()),false);
  }
 }
 public FinancialOperationResult get(String actor,UUID id) {
  return jdbc.sql("SELECT receipt FROM eft_account_posting WHERE operation_id=:id AND actor=:actor")
   .param("id",id).param("actor",actor).query(String.class).optional().map(this::parse)
   .orElseThrow(()->RegistryException.missing("OPERATION_NOT_FOUND"));
 }
 private String serialize(Object value) {
  try { return json.writeValueAsString(value); } catch(Exception failure) { throw new IllegalStateException(failure); }
 }
 private FinancialOperationResult parse(String value) {
  try { return json.readValue(value,FinancialOperationResult.class); } catch(Exception failure) { throw new IllegalStateException(failure); }
 }
}
