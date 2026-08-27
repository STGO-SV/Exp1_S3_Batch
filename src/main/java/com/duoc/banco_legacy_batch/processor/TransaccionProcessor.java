package com.duoc.banco_legacy_batch.processor;

import com.duoc.banco_legacy_batch.model.Transaccion;
import com.duoc.banco_legacy_batch.model.TransaccionProcesada;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

@Component
public class TransaccionProcessor implements ItemProcessor<Transaccion, TransaccionProcesada> {

    private static final Set<String> TIPOS_VALIDOS = Set.of("debito", "credito");
    private static final BigDecimal UMBRAL_ANOMALIA = new BigDecimal("2000.00");

    @Override
    public TransaccionProcesada process(Transaccion item) {
        String tipo = item.tipo().trim().toLowerCase(Locale.ROOT);
        if (item.monto().compareTo(BigDecimal.ZERO) <= 0 || !TIPOS_VALIDOS.contains(tipo)) {
            return null;
        }
        boolean anomalia = item.monto().compareTo(UMBRAL_ANOMALIA) > 0;
        return new TransaccionProcesada(item.id(), item.fecha(), item.monto(), tipo, anomalia);
    }
}
