package com.duoc.banco_legacy_batch.processor;

import com.duoc.banco_legacy_batch.exception.InvalidBatchDataException;
import com.duoc.banco_legacy_batch.model.CuentaInteres;
import com.duoc.banco_legacy_batch.model.InteresProcesado;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

@Component
public class InteresProcessor implements ItemProcessor<CuentaInteres, InteresProcesado> {

    private static final Map<String, BigDecimal> TASAS = Map.of(
            "ahorro", new BigDecimal("0.01"),
            "prestamo", new BigDecimal("0.02")
    );

    @Override
    public InteresProcesado process(CuentaInteres item) {
        if (item.cuentaId() == null || item.nombre() == null || item.nombre().isBlank()
                || item.saldo() == null || item.edad() == null || item.tipo() == null || item.tipo().isBlank()) {
            throw new InvalidBatchDataException("La cuenta contiene campos obligatorios vacíos");
        }
        String tipo = item.tipo().trim().toLowerCase(Locale.ROOT);
        BigDecimal tasa = TASAS.get(tipo);
        if (item.saldo().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidBatchDataException("El saldo no puede ser negativo");
        }
        if (tasa == null) {
            throw new InvalidBatchDataException("Tipo de cuenta no reconocido: " + tipo);
        }
        BigDecimal saldoProcesado = item.saldo()
                .multiply(BigDecimal.ONE.add(tasa))
                .setScale(2, RoundingMode.HALF_UP);
        return new InteresProcesado(item.cuentaId(), item.nombre().trim(), item.saldo(),
                tasa, saldoProcesado, tipo);
    }
}
