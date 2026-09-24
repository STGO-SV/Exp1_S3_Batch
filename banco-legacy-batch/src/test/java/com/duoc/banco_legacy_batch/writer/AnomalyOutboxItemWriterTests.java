package com.duoc.banco_legacy_batch.writer;

import static org.assertj.core.api.Assertions.assertThat;

import com.duoc.banco_legacy_batch.event.AnomalousTransactionEventMapper;
import com.duoc.banco_legacy_batch.model.TransaccionProcesada;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AnomalyOutboxItemWriterTests {

    @Autowired JdbcClient jdbcClient;
    @Autowired AnomalousTransactionEventMapper mapper;

    @Test
    void persisteSoloLaAnomaliaConEstadoInicialCorrecto() throws Exception {
        var writer = new AnomalyOutboxItemWriter(jdbcClient, mapper, "transaccionesDiariasJob:41");
        writer.write(new Chunk<>(List.of(
                new TransaccionProcesada(1001L, LocalDate.of(2026, 9, 20),
                        new BigDecimal("1999.99"), "debito", false),
                new TransaccionProcesada(1002L, LocalDate.of(2026, 9, 21),
                        new BigDecimal("9876543210.12"), "credito", true))));

        List<Map<String, Object>> rows = jdbcClient.sql("""
                SELECT event_id, correlation_id, event_version, occurred_at, transaction_id,
                       transaction_date, amount, transaction_type, anomaly_reason,
                       status, publish_attempts, published_at, last_error
                FROM anomaly_event_outbox
                WHERE transaction_id IN (1001, 1002)
                """).query().listOfRows();

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.get("event_id")).isNotNull();
        assertThat(row.get("correlation_id")).isEqualTo("transaccionesDiariasJob:41");
        assertThat(((Number) row.get("event_version")).intValue()).isEqualTo(1);
        assertThat(row.get("occurred_at")).isNotNull();
        assertThat(((Number) row.get("transaction_id")).longValue()).isEqualTo(1002L);
        assertThat(row.get("transaction_date").toString()).isEqualTo("2026-09-21");
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("9876543210.12");
        assertThat(row.get("transaction_type")).isEqualTo("credito");
        assertThat(row.get("anomaly_reason")).isEqualTo("AMOUNT_ABOVE_2000");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("publish_attempts")).intValue()).isZero();
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("last_error")).isNull();
    }
}
