package com.duoc.banco_legacy_batch.model;

import java.math.BigDecimal;

public record CuentaInteres(Long cuentaId, String nombre, BigDecimal saldo, Integer edad, String tipo) {
}
