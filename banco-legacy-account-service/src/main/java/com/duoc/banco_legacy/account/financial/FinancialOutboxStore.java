package com.duoc.banco_legacy.account.financial;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class FinancialOutboxStore {
    public record Claim(UUID eventId, String payload, String owner, UUID token) {}
    private final JdbcClient jdbc;
    public FinancialOutboxStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    // One atomic statement commits before Kafka I/O. Other workers skip locked candidates.
    public Optional<Claim> claim(String owner, int leaseSeconds) {
        UUID token = UUID.randomUUID();
        return jdbc.sql("""
                WITH candidate AS (
                    SELECT event_id FROM eft_financial_outbox
                    WHERE (status='PENDING' AND (retry_at IS NULL OR retry_at <= CURRENT_TIMESTAMP))
                       OR (status='PROCESSING' AND lease_until <= CURRENT_TIMESTAMP)
                    ORDER BY event_id LIMIT 1 FOR UPDATE SKIP LOCKED
                )
                UPDATE eft_financial_outbox o
                SET status='PROCESSING', claim_owner=:owner, claim_token=:token,
                    claimed_at=CURRENT_TIMESTAMP,
                    lease_until=CURRENT_TIMESTAMP + (:lease * INTERVAL '1 second'),
                    retry_at=NULL, attempts=attempts+1
                FROM candidate c WHERE o.event_id=c.event_id
                RETURNING o.event_id,o.payload
                """).param("owner", owner).param("token", token).param("lease", leaseSeconds)
                .query((rs,n) -> new Claim(UUID.fromString(rs.getString(1)),rs.getString(2),owner,token))
                .optional();
    }
    public boolean published(Claim claim) {
        return owned("""
                UPDATE eft_financial_outbox SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP,
                    lease_until=NULL,last_error=NULL
                WHERE event_id=:id AND status='PROCESSING' AND claim_owner=:owner
                    AND claim_token=:token AND lease_until > CURRENT_TIMESTAMP
                """, claim).update() == 1;
    }
    public boolean retry(Claim claim, String error) {
        return owned("""
                UPDATE eft_financial_outbox SET status='PENDING',lease_until=NULL,
                    retry_at=CURRENT_TIMESTAMP + INTERVAL '2 seconds',last_error=:error
                WHERE event_id=:id AND status='PROCESSING' AND claim_owner=:owner
                    AND claim_token=:token AND lease_until > CURRENT_TIMESTAMP
                """, claim).param("error",error).update() == 1;
    }
    private JdbcClient.StatementSpec owned(String sql, Claim claim) {
        return jdbc.sql(sql).param("id",claim.eventId()).param("owner",claim.owner()).param("token",claim.token());
    }
}
