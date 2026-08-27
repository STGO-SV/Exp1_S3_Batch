package com.duoc.banco_legacy_batch.writer;

import com.duoc.banco_legacy_batch.model.InteresProcesado;
import com.duoc.banco_legacy_batch.model.MovimientoAnualProcesado;
import com.duoc.banco_legacy_batch.model.TransaccionProcesada;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import javax.sql.DataSource;

@Configuration
public class JdbcWriterConfig {

    @Bean
    public JdbcBatchItemWriter<TransaccionProcesada> transaccionWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<TransaccionProcesada>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO transaccion_procesada
                            (transaccion_id, fecha, monto, tipo, anomalia)
                        VALUES (:transaccionId, :fecha, :monto, :tipo, :anomalia)
                        """)
                .itemSqlParameterSourceProvider(item -> new MapSqlParameterSource()
                        .addValue("transaccionId", item.id())
                        .addValue("fecha", item.fecha())
                        .addValue("monto", item.monto())
                        .addValue("tipo", item.tipo())
                        .addValue("anomalia", item.anomalia()))
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<InteresProcesado> interesWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<InteresProcesado>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO interes_procesado
                            (cuenta_id, nombre, saldo_original, tasa, saldo_procesado, tipo)
                        VALUES (:cuentaId, :nombre, :saldoOriginal, :tasa, :saldoProcesado, :tipo)
                        """)
                .itemSqlParameterSourceProvider(item -> new MapSqlParameterSource()
                        .addValue("cuentaId", item.cuentaId())
                        .addValue("nombre", item.nombre())
                        .addValue("saldoOriginal", item.saldoOriginal())
                        .addValue("tasa", item.tasa())
                        .addValue("saldoProcesado", item.saldoProcesado())
                        .addValue("tipo", item.tipo()))
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<MovimientoAnualProcesado> movimientoAnualWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<MovimientoAnualProcesado>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO movimiento_anual_procesado
                            (cuenta_id, fecha, tipo_transaccion, monto, descripcion)
                        VALUES (:cuentaId, :fecha, :tipo, :monto, :descripcion)
                        """)
                .itemSqlParameterSourceProvider(item -> new MapSqlParameterSource()
                        .addValue("cuentaId", item.cuentaId())
                        .addValue("fecha", item.fecha())
                        .addValue("tipo", item.transaccion())
                        .addValue("monto", item.monto())
                        .addValue("descripcion", item.descripcion()))
                .build();
    }
}
