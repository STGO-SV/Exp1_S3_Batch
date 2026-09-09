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
}
