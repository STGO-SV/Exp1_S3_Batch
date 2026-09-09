package com.duoc.banco_legacy_batch.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransaccionProcesada(Long id, LocalDate fecha, BigDecimal monto, String tipo, boolean anomalia) {
}
