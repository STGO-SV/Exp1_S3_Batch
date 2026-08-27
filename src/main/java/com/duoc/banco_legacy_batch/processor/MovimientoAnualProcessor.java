package com.duoc.banco_legacy_batch.processor;

import com.duoc.banco_legacy_batch.exception.InvalidBatchDataException;
import com.duoc.banco_legacy_batch.model.MovimientoAnual;
import com.duoc.banco_legacy_batch.model.MovimientoAnualProcesado;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

@Component
public class MovimientoAnualProcessor implements ItemProcessor<MovimientoAnual, MovimientoAnualProcesado> {

    private static final Set<String> TIPOS_VALIDOS = Set.of("deposito", "retiro", "compra");

    @Override
    public MovimientoAnualProcesado process(MovimientoAnual item) {
        if (item.cuentaId() == null || item.fecha() == null || item.transaccion() == null
                || item.transaccion().isBlank() || item.monto() == null || item.descripcion() == null
                || item.descripcion().isBlank()) {
            throw new InvalidBatchDataException("El movimiento anual contiene campos obligatorios vacíos");
        }
        String tipo = item.transaccion().trim().toLowerCase(Locale.ROOT);
        if (item.monto().compareTo(BigDecimal.ZERO) == 0) {
            throw new InvalidBatchDataException("El monto del movimiento anual no puede ser cero");
        }
        if (!TIPOS_VALIDOS.contains(tipo)) {
            throw new InvalidBatchDataException("Tipo de movimiento anual no reconocido: " + tipo);
        }
        String descripcion = item.descripcion().trim().replaceAll("\\s+", " ");
        return new MovimientoAnualProcesado(item.cuentaId(), item.fecha(), tipo,
                item.monto(), descripcion);
    }
}
