package com.duoc.banco_legacy_batch.reader;

import com.duoc.banco_legacy_batch.model.CuentaInteres;
import com.duoc.banco_legacy_batch.model.MovimientoAnual;
import com.duoc.banco_legacy_batch.model.Transaccion;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

@Configuration
public class CsvReaderConfig {

    @Bean
    public FlatFileItemReader<Transaccion> transaccionReader(
            @Value("${batch.input-directory}") String inputDirectory) {
        return new FlatFileItemReaderBuilder<Transaccion>()
                .name("transaccionReader")
                .resource(new FileSystemResource(Path.of(inputDirectory, "transacciones.csv")))
                .linesToSkip(1)
                .delimited().names("id", "fecha", "monto", "tipo")
                .fieldSetMapper(fields -> new Transaccion(
                        fields.readLong("id"),
                        LocalDate.parse(fields.readString("fecha")),
                        fields.readBigDecimal("monto"),
                        fields.readString("tipo")))
                .build();
    }

    @Bean
    public FlatFileItemReader<CuentaInteres> interesReader(
            @Value("${batch.input-directory}") String inputDirectory) {
        return new FlatFileItemReaderBuilder<CuentaInteres>()
                .name("interesReader")
                .resource(new FileSystemResource(Path.of(inputDirectory, "intereses.csv")))
                .linesToSkip(1)
                .delimited().names("cuenta_id", "nombre", "saldo", "edad", "tipo")
                .fieldSetMapper(fields -> new CuentaInteres(
                        fields.readLong("cuenta_id"),
                        fields.readString("nombre"),
                        fields.readBigDecimal("saldo"),
                        fields.readInt("edad"),
                        fields.readString("tipo")))
                .build();
    }

    @Bean
    public FlatFileItemReader<MovimientoAnual> movimientoAnualReader(
            @Value("${batch.input-directory}") String inputDirectory) {
        return new FlatFileItemReaderBuilder<MovimientoAnual>()
                .name("movimientoAnualReader")
                .resource(new FileSystemResource(Path.of(inputDirectory, "cuentas_anuales.csv")))
                .linesToSkip(1)
                .delimited().names("cuenta_id", "fecha", "transaccion", "monto", "descripcion")
                .fieldSetMapper(fields -> new MovimientoAnual(
                        fields.readLong("cuenta_id"),
                        LocalDate.parse(fields.readString("fecha")),
                        fields.readString("transaccion"),
                        fields.readBigDecimal("monto"),
                        fields.readString("descripcion")))
                .build();
    }
}
