package com.duoc.banco_legacy_batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BatchJobsIntegrationTests {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    @Qualifier("transaccionesDiariasJob")
    private Job transaccionesJob;

    @Autowired
    @Qualifier("interesesMensualesJob")
    private Job interesesJob;

    @Autowired
    @Qualifier("estadosCuentaAnualesJob")
    private Job estadosAnualesJob;

    @Test
    void ejecutaLosTresJobsYPersisteResultadosEsperados() throws Exception {
        JobExecution transacciones = ejecutar(transaccionesJob, 1L);
        JobExecution intereses = ejecutar(interesesJob, 2L);
        JobExecution movimientos = ejecutar(estadosAnualesJob, 3L);

        assertThat(contar("transaccion_procesada")).isEqualTo(8);
        assertThat(contar("interes_procesado")).isEqualTo(7);
        assertThat(contar("movimiento_anual_procesado")).isEqualTo(8);

        Integer anomalias = jdbcClient.sql("SELECT COUNT(*) FROM transaccion_procesada WHERE anomalia")
                .query(Integer.class).single();
        assertThat(anomalias).isEqualTo(1);

        String saldo = jdbcClient.sql("SELECT CAST(saldo_procesado AS VARCHAR) FROM interes_procesado WHERE cuenta_id = 101")
                .query(String.class).single();
        assertThat(new BigDecimal(saldo)).isEqualByComparingTo("5050.00");

        assertThat(managerStep(transacciones).getProcessSkipCount()).isEqualTo(2);
        assertThat(managerStep(intereses).getProcessSkipCount()).isEqualTo(1);
        assertThat(managerStep(movimientos).getProcessSkipCount()).isEqualTo(1);
        assertThat(workerSteps(transacciones)).hasSize(4).allMatch(step -> step.getStatus() == BatchStatus.COMPLETED);
        assertThat(workerSteps(transacciones)).allMatch(step ->
                step.getExecutionContext().getLong("batch.retry.count", 0L) == 0L);
    }

    private JobExecution ejecutar(Job job, long id) throws Exception {
        JobExecution execution = jobLauncher.run(job,
                new JobParametersBuilder().addLong("testId", id).toJobParameters());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        return execution;
    }

    private org.springframework.batch.core.StepExecution managerStep(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .filter(step -> !step.getStepName().contains(":"))
                .findFirst().orElseThrow();
    }

    private java.util.List<org.springframework.batch.core.StepExecution> workerSteps(JobExecution execution) {
        return execution.getStepExecutions().stream()
                .filter(step -> step.getStepName().contains(":"))
                .toList();
    }

    private Integer contar(String tabla) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + tabla).query(Integer.class).single();
    }
}
