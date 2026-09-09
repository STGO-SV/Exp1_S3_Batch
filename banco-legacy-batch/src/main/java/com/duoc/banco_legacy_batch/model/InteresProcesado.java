package com.duoc.banco_legacy_batch.model;

import java.math.BigDecimal;

public record InteresProcesado(Long cuentaId, String nombre, BigDecimal saldoOriginal,
                              BigDecimal tasa, BigDecimal saldoProcesado, String tipo) {
}
