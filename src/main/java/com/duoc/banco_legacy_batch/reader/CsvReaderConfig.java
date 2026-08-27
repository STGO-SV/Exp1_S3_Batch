package com.duoc.banco_legacy_batch.reader;

import com.duoc.banco_legacy_batch.model.CuentaInteres;
import com.duoc.banco_legacy_batch.model.MovimientoAnual;
import com.duoc.banco_legacy_batch.model.Transaccion;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

@Configuration
public class CsvReaderConfig {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu/MM/dd").withResolverStyle(ResolverStyle.STRICT)
    );

    @Bean
    @StepScope
    public PartitionRangeItemReader<Transaccion> transaccionReader(
            @Value("${batch.input-directory}") String inputDirectory,
            @Value("#{stepExecutionContext['minValue']}") long minValue,
            @Value("#{stepExecutionContext['maxValue']}") long maxValue) {
        var delegate = new FlatFileItemReaderBuilder<Transaccion>()
                .name("transaccionReader-" + minValue + "-" + maxValue)
                .resource(new FileSystemResource(Path.of(inputDirectory, "transacciones.csv")))
                .linesToSkip(1)
                .delimited().names("id", "fecha", "monto", "tipo")
                .fieldSetMapper(fields -> {
                    long id = fields.readLong("id");
                    if (id < minValue || id > maxValue) {
                        return new Transaccion(id, null, null, null);
                    }
                    return new Transaccion(id, parseDate(fields.readString("fecha")),
                            fields.readBigDecimal("monto"), fields.readString("tipo"));
                })
                .build();
        return new PartitionRangeItemReader<>(delegate, Transaccion::id, minValue, maxValue);
    }

    @Bean
    @StepScope
    public PartitionRangeItemReader<CuentaInteres> interesReader(
            @Value("${batch.input-directory}") String inputDirectory,
            @Value("#{stepExecutionContext['minValue']}") long minValue,
            @Value("#{stepExecutionContext['maxValue']}") long maxValue) {
        var delegate = new FlatFileItemReaderBuilder<CuentaInteres>()
                .name("interesReader-" + minValue + "-" + maxValue)
                .resource(new FileSystemResource(Path.of(inputDirectory, "intereses.csv")))
                .linesToSkip(1)
                .delimited().names("cuenta_id", "nombre", "saldo", "edad", "tipo")
                .fieldSetMapper(fields -> {
                    long cuentaId = fields.readLong("cuenta_id");
                    if (cuentaId < minValue || cuentaId > maxValue) {
                        return new CuentaInteres(cuentaId, null, null, null, null);
                    }
                    return new CuentaInteres(cuentaId, fields.readString("nombre"),
                            fields.readBigDecimal("saldo"), fields.readInt("edad"), fields.readString("tipo"));
                })
                .build();
        return new PartitionRangeItemReader<>(delegate, CuentaInteres::cuentaId, minValue, maxValue);
    }

    @Bean
    @StepScope
    public PartitionRangeItemReader<MovimientoAnual> movimientoAnualReader(
            @Value("${batch.input-directory}") String inputDirectory,
            @Value("#{stepExecutionContext['minValue']}") long minValue,
            @Value("#{stepExecutionContext['maxValue']}") long maxValue) {
        var delegate = new FlatFileItemReaderBuilder<MovimientoAnual>()
                .name("movimientoAnualReader-" + minValue + "-" + maxValue)
                .resource(new FileSystemResource(Path.of(inputDirectory, "cuentas_anuales.csv")))
                .linesToSkip(1)
                .delimited().names("cuenta_id", "fecha", "transaccion", "monto", "descripcion")
                .fieldSetMapper(fields -> {
                    long cuentaId = fields.readLong("cuenta_id");
                    if (cuentaId < minValue || cuentaId > maxValue) {
                        return new MovimientoAnual(cuentaId, null, null, null, null);
                    }
                    return new MovimientoAnual(cuentaId, parseDate(fields.readString("fecha")),
                            fields.readString("transaccion"), fields.readBigDecimal("monto"),
                            fields.readString("descripcion"));
                })
                .build();
        return new PartitionRangeItemReader<>(delegate, MovimientoAnual::cuentaId, minValue, maxValue);
    }

    private static LocalDate parseDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // Se prueba el siguiente formato admitido por los datasets académicos.
            }
        }
        throw new DateTimeParseException("Formato o fecha inválida", value, 0);
    }
}
