package com.duoc.banco_legacy.account.registry;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountRegistryService {
    public record Account(long accountId, String accountType, String status, long version, List<UUID> customerIds) {}
    public record Created(Account account, boolean created) {}
    private final JdbcClient jdbc;
    public AccountRegistryService(JdbcClient jdbc) { this.jdbc=jdbc; }
    @Transactional(readOnly=true)
    public Optional<Account> find(long id) {
        return jdbc.sql("SELECT account_id,account_type,status,version FROM eft_account WHERE account_id=:id").param("id",id)
                .query((rs,n)->new Account(rs.getLong("account_id"),rs.getString("account_type"),
                        rs.getString("status"),rs.getLong("version"), holders(id))).optional();
    }
    private List<UUID> holders(long id) {
        return jdbc.sql("SELECT customer_id FROM eft_account_holder WHERE account_id=:id ORDER BY customer_id")
                .param("id",id).query((rs,n)->UUID.fromString(rs.getString("customer_id"))).list();
    }
    @Transactional(readOnly=true)
    public Account get(long id) { return find(id).orElseThrow(()->RegistryException.missing("ACCOUNT_NOT_FOUND")); }
    @Transactional
    public Created open(long id, String type, List<UUID> customerIds) {
        var existing=find(id);
        if(existing.isPresent()) {
            if(!existing.get().accountType().equals(type) ||
                    !new HashSet<>(existing.get().customerIds()).equals(new HashSet<>(customerIds)))
                throw RegistryException.conflict("ACCOUNT_ID_CONFLICT");
            return new Created(existing.get(),false);
        }
        boolean legacy=jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM interes_procesado WHERE cuenta_id=:id)
                    OR EXISTS(SELECT 1 FROM movimiento_anual_procesado WHERE cuenta_id=:id)
                """).param("id",id).query(Boolean.class).single();
        if(legacy) throw RegistryException.conflict("LEGACY_MIGRATION_REQUIRED");
        try {
            jdbc.sql("INSERT INTO eft_account(account_id,account_type,status,version) VALUES (:id,:type,'OPEN',0)")
                    .param("id",id).param("type",type).update();
            for(UUID customerId:customerIds) {
                jdbc.sql("INSERT INTO eft_account_holder(account_id,customer_id) VALUES (:id,:customer)")
                        .param("id",id).param("customer",customerId).update();
            }
        } catch(DuplicateKeyException failure) {
            throw RegistryException.conflict("ACCOUNT_ID_CONFLICT");
        }
        return new Created(get(id),true);
    }
    @Transactional
    public Account update(long id, String type, long version) {
        get(id);
        int count=jdbc.sql("""
                UPDATE eft_account SET account_type=:type,version=version+1
                WHERE account_id=:id AND version=:version AND status='OPEN'
                """).param("id",id).param("type",type).param("version",version).update();
        if(count!=1) throw RegistryException.conflict("ACCOUNT_STATE_CONFLICT");
        return get(id);
    }
    @Transactional
    public Account close(long id,long version) {
        Account current=get(id);
        if(current.status().equals("CLOSED") && (version==current.version() || version==current.version()-1)) return current;
        int count=jdbc.sql("""
                UPDATE eft_account SET status='CLOSED',version=version+1
                WHERE account_id=:id AND version=:version AND status='OPEN'
                """).param("id",id).param("version",version).update();
        if(count!=1) throw RegistryException.conflict("ACCOUNT_STATE_CONFLICT");
        return get(id);
    }
    @Transactional(readOnly=true)
    public List<Account> forCustomer(UUID customerId,int limit,int offset) {
        List<Long> ids=jdbc.sql("""
                SELECT account_id FROM eft_account_holder WHERE customer_id=:customer
                ORDER BY account_id LIMIT :limit OFFSET :offset
                """).param("customer",customerId).param("limit",limit).param("offset",offset).query(Long.class).list();
        return ids.stream().map(this::get).toList();
    }
}