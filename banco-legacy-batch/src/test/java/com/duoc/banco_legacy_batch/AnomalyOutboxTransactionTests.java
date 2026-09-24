package com.duoc.banco_legacy_batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.duoc.banco_legacy_batch.event.AnomalousTransactionEventMapper;
import com.duoc.banco_legacy_batch.model.TransaccionProcesada;
import com.duoc.banco_legacy_batch.writer.AnomalyOutboxItemWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
class AnomalyOutboxTransactionTests {

    @Autowired JobLauncher jobLauncher;
    @Autowired JobRepository jobRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcClient jdbcClient;
    @Autowired AnomalousTransactionEventMapper mapper;
    @Autowired @Qualifier("transaccionJdbcWriter")
    JdbcBatchItemWriter<TransaccionProcesada> jdbcWriter;

    @Test
    void revierteLaTransaccionNormalYElOutboxSiFallaLaSegundaEscritura() throws Exception {
        long transactionId = 88001L;
        var item = new TransaccionProcesada(transactionId, LocalDate.of(2026, 9, 22),
                new BigDecimal("2500.55"), "debito", true);
        var realOutboxWriter = new AnomalyOutboxItemWriter(
                jdbcClient, mapper, "transaccionesDiariasJob:atomicity-test");
        ItemWriter<TransaccionProcesada> failingOutboxWriter = chunk -> {
            realOutboxWriter.write(chunk);
            throw new IllegalStateException("Fallo deliberado después de escribir outbox");
        };
        var composite = new CompositeItemWriter<TransaccionProcesada>();
        composite.setDelegates(List.of(jdbcWriter, failingOutboxWriter));

        Step step = new StepBuilder("atomicity-step-" + UUID.randomUUID(), jobRepository)
                .<TransaccionProcesada, TransaccionProcesada>chunk(1, transactionManager)
                .reader(new ListItemReader<>(List.of(item)))
                .writer(composite)
                .build();
        Job job = new JobBuilder("atomicity-job-" + UUID.randomUUID(), jobRepository)
                .start(step).build();

        JobExecution execution = jobLauncher.run(job, new org.springframework.batch.core.JobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(count("transaccion_procesada", "transaccion_id", transactionId)).isZero();
        assertThat(count("anomaly_event_outbox", "transaction_id", transactionId)).isZero();
    }

    private int count(String table, String column, long transactionId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = :transactionId")
                .param("transactionId", transactionId).query(Integer.class).single();
    }
}
