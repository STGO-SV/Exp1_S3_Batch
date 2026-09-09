package com.duoc.banco_legacy_batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:banco_batch_week3;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "batch.partition.grid-size=4",
        "batch.partition.thread-count=4",
        "batch.chunk-size=100",
        "batch.skip-limit=1000",
        "logging.level.com.duoc.banco_legacy_batch.listener.BatchSkipLoggingListener=ERROR"
})
class Week3PartitionedJobsIntegrationTests {

    @DynamicPropertySource
    static void configureDataset(DynamicPropertyRegistry registry) {
        registry.add("batch.input-directory", TestDatasetPaths::week3);
    }

    @Autowired private JobLauncher jobLauncher;
    @Autowired private JdbcClient jdbcClient;
    @Autowired @Qualifier("transaccionesDiariasJob") private Job transaccionesJob;
    @Autowired @Qualifier("interesesMensualesJob") private Job interesesJob;
    @Autowired @Qualifier("estadosCuentaAnualesJob") private Job estadosAnualesJob;

    @Test
    void procesaSemana3EnParaleloSinDuplicadosYConReconciliacionCompleta() throws Exception {
        JobExecution transacciones = ejecutar(transaccionesJob);
        JobExecution intereses = ejecutar(interesesJob);
        JobExecution movimientos = ejecutar(estadosAnualesJob);

        verificar(transacciones, 1000, 401, 599);
        verificar(intereses, 1000, 282, 718);
        verificar(movimientos, 1000, 642, 358);

        assertThat(contar("transaccion_procesada")).isEqualTo(401);
        assertThat(contar("interes_procesado")).isEqualTo(282);
        assertThat(contar("movimiento_anual_procesado")).isEqualTo(642);
        assertThat(jdbcClient.sql("SELECT COUNT(DISTINCT transaccion_id) FROM transaccion_procesada")
                .query(Integer.class).single()).isEqualTo(401);
    }

    private JobExecution ejecutar(Job job) throws Exception {
        JobExecution execution = jobLauncher.run(job, new JobParametersBuilder()
                .addString("week3Run", UUID.randomUUID().toString()).toJobParameters());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        return execution;
    }

    private void verificar(JobExecution execution, long read, long written, long skipped) {
        List<StepExecution> workers = execution.getStepExecutions().stream()
                .filter(step -> step.getStepName().contains(":"))
                .toList();
        assertThat(workers).hasSize(4).allMatch(step -> step.getStatus() == BatchStatus.COMPLETED);
        assertThat(workers).extracting(step -> step.getExecutionContext().getString("batch.thread.name"))
                .allMatch(name -> name.startsWith("batch-partition-"));
        long readCount = workers.stream().mapToLong(StepExecution::getReadCount).sum();
        long readSkips = workers.stream().mapToLong(StepExecution::getReadSkipCount).sum();
        assertThat(readCount + readSkips).isEqualTo(read);
        assertThat(workers.stream().mapToLong(StepExecution::getWriteCount).sum()).isEqualTo(written);
        assertThat(workers.stream().mapToLong(step -> step.getReadSkipCount()
                + step.getProcessSkipCount() + step.getWriteSkipCount()).sum()).isEqualTo(skipped);
        assertThat(read).isEqualTo(written + skipped);
    }

    private Integer contar(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
    }
}
