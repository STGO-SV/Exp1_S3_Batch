package com.duoc.banco_legacy.core.repository;

import com.duoc.banco_legacy.core.model.LockedAccountBalance;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.dao.IncorrectUpdateSemanticsDataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LegacyAccountWithdrawalRepository {

    private final JdbcClient jdbcClient;

    public LegacyAccountWithdrawalRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<LockedAccountBalance> lockLatestBalance(long accountId) {
        return jdbcClient.sql("""
                SELECT id, saldo_procesado
                FROM interes_procesado
                WHERE cuenta_id = :accountId
                ORDER BY id DESC
                LIMIT 1
                FOR UPDATE
                """)
                .param("accountId", accountId)
                .query((rs, row) -> new LockedAccountBalance(
                        rs.getLong("id"), rs.getBigDecimal("saldo_procesado")))
                .optional();
    }

    public void updateBalance(long rowId, BigDecimal balanceAfter) {
        int rowsAffected = jdbcClient.sql("""
                UPDATE interes_procesado
                SET saldo_procesado = :balanceAfter
                WHERE id = :rowId
                """)
                .param("balanceAfter", balanceAfter)
                .param("rowId", rowId)
                .update();
        requireOneRow("actualizar el saldo bloqueado", rowsAffected);
    }

    public void insertWithdrawalMovement(long accountId, BigDecimal amount) {
        int rowsAffected = jdbcClient.sql("""
                INSERT INTO movimiento_anual_procesado
                    (cuenta_id, fecha, tipo_transaccion, monto, descripcion)
                VALUES
                    (:accountId, CURRENT_DATE, 'retiro', :amount, 'Retiro ATM')
                """)
                .param("accountId", accountId)
                .param("amount", amount)
                .update();
        requireOneRow("registrar el movimiento del retiro", rowsAffected);
    }

    private void requireOneRow(String operation, int rowsAffected) {
        if (rowsAffected != 1) {
            throw new IncorrectUpdateSemanticsDataAccessException(
                    "Se esperaba afectar una fila al " + operation
                            + "; filas afectadas: " + rowsAffected);
        }
    }
}
