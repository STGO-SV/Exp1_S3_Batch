package com.duoc.banco_legacy.core.repository;

import com.duoc.banco_legacy.core.model.AccountBalance;
import com.duoc.banco_legacy.core.model.AccountMovement;
import com.duoc.banco_legacy.core.model.ProcessedTransaction;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class LegacyAccountReadRepository {

    private final JdbcClient jdbcClient;

    public LegacyAccountReadRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<AccountBalance> findLatestBalance(long accountId) {
        return jdbcClient.sql("""
                        SELECT cuenta_id, nombre, saldo_original, tasa, saldo_procesado, tipo
                        FROM interes_procesado
                        WHERE cuenta_id = :accountId
                        ORDER BY id DESC
                        LIMIT 1
                        """)
                .param("accountId", accountId)
                .query((rs, row) -> new AccountBalance(
                        rs.getLong("cuenta_id"), rs.getString("nombre"),
                        rs.getBigDecimal("saldo_original"), rs.getBigDecimal("tasa"),
                        rs.getBigDecimal("saldo_procesado"), rs.getString("tipo")))
                .optional();
    }

    public List<AccountMovement> findRecentMovements(long accountId, int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                        SELECT cuenta_id, fecha, tipo_transaccion, monto, descripcion
                        FROM movimiento_anual_procesado
                        WHERE cuenta_id = :accountId
                        ORDER BY fecha DESC, id DESC
                        LIMIT :limit
                        """)
                .param("accountId", accountId)
                .param("limit", limit)
                .query((rs, row) -> new AccountMovement(
                        rs.getLong("cuenta_id"), rs.getDate("fecha").toLocalDate(),
                        rs.getString("tipo_transaccion"), rs.getBigDecimal("monto"),
                        rs.getString("descripcion")))
                .list();
    }

    public List<ProcessedTransaction> findRecentAnomalies(int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                        SELECT transaccion_id, fecha, monto, tipo, anomalia
                        FROM transaccion_procesada
                        WHERE anomalia = TRUE
                        ORDER BY fecha DESC, id DESC
                        LIMIT :limit
                        """)
                .param("limit", limit)
                .query((rs, row) -> new ProcessedTransaction(
                        rs.getLong("transaccion_id"), rs.getDate("fecha").toLocalDate(),
                        rs.getBigDecimal("monto"), rs.getString("tipo"), rs.getBoolean("anomalia")))
                .list();
    }

    public Optional<com.duoc.banco_legacy.core.model.AccountSummary> findSummary(long accountId) {
        return jdbcClient.sql("""
                SELECT saldo_procesado, tipo FROM interes_procesado
                WHERE cuenta_id = :accountId ORDER BY id DESC LIMIT 1
                """).param("accountId", accountId)
                .query((rs, row) -> new com.duoc.banco_legacy.core.model.AccountSummary(
                        rs.getBigDecimal("saldo_procesado"), rs.getString("tipo"))).optional();
    }

    public Optional<java.math.BigDecimal> findAvailableBalance(long accountId) {
        return jdbcClient.sql("""
                SELECT saldo_procesado FROM interes_procesado
                WHERE cuenta_id = :accountId ORDER BY id DESC LIMIT 1
                """).param("accountId", accountId).query(java.math.BigDecimal.class).optional();
    }

    public boolean accountExists(long accountId) {
        return jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM interes_procesado WHERE cuenta_id = :accountId)")
                .param("accountId", accountId).query(Boolean.class).single();
    }

    public List<com.duoc.banco_legacy.core.model.CompactMovement> findCompactMovements(long accountId, int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                SELECT fecha, tipo_transaccion, monto FROM movimiento_anual_procesado
                WHERE cuenta_id = :accountId ORDER BY fecha DESC, id DESC LIMIT :limit
                """).param("accountId", accountId).param("limit", limit)
                .query((rs, row) -> new com.duoc.banco_legacy.core.model.CompactMovement(
                        rs.getDate("fecha").toLocalDate(), rs.getString("tipo_transaccion"), rs.getBigDecimal("monto"))).list();
    }

    public List<com.duoc.banco_legacy.core.model.EssentialMovement> findEssentialMovements(long accountId, int limit) {
        validateLimit(limit);
        return jdbcClient.sql("""
                SELECT tipo_transaccion, monto FROM movimiento_anual_procesado
                WHERE cuenta_id = :accountId ORDER BY fecha DESC, id DESC LIMIT :limit
                """).param("accountId", accountId).param("limit", limit)
                .query((rs, row) -> new com.duoc.banco_legacy.core.model.EssentialMovement(
                        rs.getString("tipo_transaccion"), rs.getBigDecimal("monto"))).list();
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Limit must be 1..100");
    }

}