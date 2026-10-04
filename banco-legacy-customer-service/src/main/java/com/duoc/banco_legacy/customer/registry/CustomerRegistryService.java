package com.duoc.banco_legacy.customer.registry;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerRegistryService {
    public record Customer(UUID customerId, String name, long version) {}
    public record Created(Customer customer, boolean created) {}
    private final JdbcClient jdbc;
    public CustomerRegistryService(JdbcClient jdbc) { this.jdbc = jdbc; }
    @Transactional(readOnly = true)
    public Customer get(UUID id) {
        return find(id).orElseThrow(() -> RegistryException.missing("CUSTOMER_NOT_FOUND"));
    }
    private java.util.Optional<Customer> find(UUID id) {
        return jdbc.sql("SELECT customer_id,name,version FROM eft_customer WHERE customer_id=:id").param("id",id)
                .query((rs,n) -> new Customer(UUID.fromString(rs.getString("customer_id")),
                        rs.getString("name"), rs.getLong("version"))).optional();
    }
    @Transactional
    public Created create(UUID id, String suppliedName) {
        String name = suppliedName.trim();
        var existing = find(id);
        if (existing.isPresent()) {
            if (!existing.get().name().equals(name)) throw RegistryException.conflict("CUSTOMER_ID_CONFLICT");
            return new Created(existing.get(), false);
        }
        try {
            jdbc.sql("INSERT INTO eft_customer(customer_id,name,version) VALUES (:id,:name,0)")
                    .param("id",id).param("name",name).update();
        } catch (DuplicateKeyException failure) {
            throw RegistryException.conflict("CUSTOMER_ID_CONFLICT");
        }
        return new Created(get(id), true);
    }
    @Transactional
    public Customer update(UUID id, String name, long version) {
        get(id);
        int count = jdbc.sql("UPDATE eft_customer SET name=:name,version=version+1 WHERE customer_id=:id AND version=:version")
                .param("id",id).param("name",name.trim()).param("version",version).update();
        if(count != 1) throw RegistryException.conflict("VERSION_CONFLICT");
        return get(id);
    }
}