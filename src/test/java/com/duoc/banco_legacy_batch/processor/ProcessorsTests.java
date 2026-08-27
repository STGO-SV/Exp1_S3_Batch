package com.duoc.banco_legacy_batch.processor;

import com.duoc.banco_legacy_batch.model.CuentaInteres;
import com.duoc.banco_legacy_batch.model.InteresProcesado;
import com.duoc.banco_legacy_batch.model.Transaccion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessorsTests {

    @Test
    void transaccionProcessorFiltraMontosNoPositivos() throws Exception {
        TransaccionProcessor processor = new TransaccionProcessor();

        assertThat(processor.process(new Transaccion(1L, LocalDate.now(),
                BigDecimal.ZERO, "debito"))).isNull();
    }

    @Test
    void interesProcessorAplicaTasaAcademicaDeAhorro() throws Exception {
        InteresProcessor processor = new InteresProcessor();

        InteresProcesado resultado = processor.process(
                new CuentaInteres(101L, "Nombre", new BigDecimal("5000"), 30, " AHORRO "));

        assertThat(resultado).isNotNull();
        assertThat(resultado.saldoProcesado()).isEqualByComparingTo("5050.00");
        assertThat(resultado.tipo()).isEqualTo("ahorro");
    }
}
