package com.duoc.banco_legacy_batch.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimientoAnualProcesado(Long cuentaId, LocalDate fecha, String transaccion,
                                       BigDecimal monto, String descripcion) {
}
