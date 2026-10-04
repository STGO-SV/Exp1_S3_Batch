package com.duoc.banco_legacy.account;

import com.duoc.banco_legacy.account.financial.FinancialOutboxStore;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="EFT_POSTGRES_TEST_URL",matches=".+")
class FinancialOutboxPostgresTests {
    DriverManagerDataSource ds;
    JdbcTemplate jdbc;
    FinancialOutboxStore store;
    String schema;
    @BeforeEach void setup() throws Exception {
        schema="eft6_test_"+UUID.randomUUID().toString().replace("-","");
        String base=System.getenv("EFT_POSTGRES_TEST_URL");
        var admin=new DriverManagerDataSource(base,System.getenv("EFT_POSTGRES_TEST_USER"),System.getenv("EFT_POSTGRES_TEST_PASSWORD"));
        new JdbcTemplate(admin).execute("CREATE SCHEMA "+schema);
        ds=new DriverManagerDataSource(base+(base.contains("?")?"&":"?")+"currentSchema="+schema,
                System.getenv("EFT_POSTGRES_TEST_USER"),System.getenv("EFT_POSTGRES_TEST_PASSWORD"));
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("""
          CREATE TABLE eft_financial_outbox(event_id UUID PRIMARY KEY,operation_id UUID UNIQUE,
            payload TEXT NOT NULL,status VARCHAR(20) DEFAULT 'PENDING' CHECK(status IN ('PENDING','PUBLISHED')),
            attempts INTEGER DEFAULT 0,published_at TIMESTAMPTZ,last_error VARCHAR(120))
          """);
        migrate();store=new FinancialOutboxStore(JdbcClient.create(ds));
    }
    void migrate() throws Exception {
        String sql=new String(getClass().getResourceAsStream("/schema-eft-outbox-lease.sql").readAllBytes(),StandardCharsets.UTF_8);
        // JDBC Statement supports PostgreSQL dollar-quoted DO blocks without script delimiter parsing.
        try(Connection connection=ds.getConnection();var stmt=connection.createStatement()) {stmt.execute(sql);}
    }
    @AfterEach void cleanup() {
        if(jdbc!=null && schema.matches("eft6_test_[a-f0-9]{32}"))jdbc.execute("DROP SCHEMA "+schema+" CASCADE");
    }
    UUID event() {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO eft_financial_outbox(event_id,operation_id,payload) VALUES (?,?,?)",id,UUID.randomUUID(),"{}");
        return id;
    }
    @Test void simultaneousWorkersNeverClaimSameEvent() throws Exception {
        UUID id=event();
        try(var workers=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var a=workers.submit(()->{gate.await();return store.claim("A",30);});
            var b=workers.submit(()->{gate.await();return store.claim("B",30);});
            gate.countDown();
            var claims=java.util.stream.Stream.of(a.get(),b.get()).flatMap(Optional::stream).toList();
            assertThat(claims).hasSize(1);assertThat(claims.getFirst().eventId()).isEqualTo(id);
        }
    }
    @Test void lockedCandidateDoesNotBlockAnotherEvent() throws Exception {
        var ids=new TreeSet<UUID>();ids.add(event());ids.add(event());
        // Use DB ordering because PostgreSQL UUID comparison differs from Java signed ordering.
        UUID first=jdbc.queryForObject("SELECT event_id FROM eft_financial_outbox ORDER BY event_id LIMIT 1",UUID.class);
        try(Connection tx=ds.getConnection()) {
            tx.setAutoCommit(false);
            try(var stmt=tx.prepareStatement("SELECT event_id FROM eft_financial_outbox WHERE event_id=? FOR UPDATE")) {
                stmt.setObject(1,first);stmt.executeQuery().close();
                try(var workers=Executors.newSingleThreadExecutor()) {
                    var claim=workers.submit(()->store.claim("B",30)).get(3,TimeUnit.SECONDS).orElseThrow();
                    assertThat(claim.eventId()).isNotEqualTo(first);
                }
            } finally {tx.rollback();}
        }
    }
    @Test void abandonedExpiredClaimRecoveredAndStaleOwnerCannotAcknowledge() {
        event();var old=store.claim("A",30).orElseThrow();
        jdbc.update("UPDATE eft_financial_outbox SET lease_until=CURRENT_TIMESTAMP-INTERVAL '1 second'");
        var recovered=store.claim("B",30).orElseThrow();
        assertThat(recovered.eventId()).isEqualTo(old.eventId());
        assertThat(recovered.token()).isNotEqualTo(old.token());
        assertThat(store.published(old)).isFalse();assertThat(store.retry(old,"stale")).isFalse();
        assertThat(store.published(recovered)).isTrue();
        assertThat(store.claim("A",30)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT attempts FROM eft_financial_outbox",Integer.class)).isEqualTo(2);
    }
    @Test void failureSchedulesRetryAndSuccessfulAckKeepsOneEventId() {
        UUID id=event();var first=store.claim("A",30).orElseThrow();
        assertThat(store.retry(first,"KafkaFailure")).isTrue();
        assertThat(store.claim("B",30)).isEmpty();
        jdbc.update("UPDATE eft_financial_outbox SET retry_at=CURRENT_TIMESTAMP-INTERVAL '1 second'");
        var second=store.claim("B",30).orElseThrow();assertThat(second.eventId()).isEqualTo(id);
        assertThat(store.published(second)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_financial_outbox WHERE status='PUBLISHED' AND published_at IS NOT NULL",Integer.class)).isEqualTo(1);
    }
    @Test void additiveMigrationCanBeRepeatedWithoutChangingExistingRows() throws Exception {
        UUID id=event();jdbc.update("UPDATE eft_financial_outbox SET status='PUBLISHED',attempts=7,published_at=CURRENT_TIMESTAMP");
        var before=jdbc.queryForMap("SELECT event_id,payload,status,attempts,published_at FROM eft_financial_outbox WHERE event_id=?",id);
        migrate();migrate();
        assertThat(jdbc.queryForMap("SELECT event_id,payload,status,attempts,published_at FROM eft_financial_outbox WHERE event_id=?",id)).isEqualTo(before);
    }
    @Test void missingAckOrForeignOwnerNeverMarksPublished() {
        event();var actual=store.claim("A",30).orElseThrow();
        var foreign=new FinancialOutboxStore.Claim(actual.eventId(),actual.payload(),"B",UUID.randomUUID());
        assertThat(store.published(foreign)).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM eft_financial_outbox",String.class)).isEqualTo("PROCESSING");
        assertThat(jdbc.queryForObject("SELECT published_at FROM eft_financial_outbox",java.sql.Timestamp.class)).isNull();
    }
}
