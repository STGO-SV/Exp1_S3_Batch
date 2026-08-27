package com.duoc.banco_legacy_batch.processor;

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
        String tipo = item.transaccion().trim().toLowerCase(Locale.ROOT);
        if (item.monto().compareTo(BigDecimal.ZERO) == 0 || !TIPOS_VALIDOS.contains(tipo)) {
            return null;
        }
        String descripcion = item.descripcion().trim().replaceAll("\\s+", " ");
        return new MovimientoAnualProcesado(item.cuentaId(), item.fecha(), tipo,
                item.monto(), descripcion);
    }
}
