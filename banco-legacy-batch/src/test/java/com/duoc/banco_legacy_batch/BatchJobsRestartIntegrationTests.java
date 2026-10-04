package com.duoc.banco_legacy_batch;

import com.duoc.banco_legacy_batch.model.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eft_restart;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "batch.partition.grid-size=1", "batch.partition.thread-count=1", "batch.chunk-size=1"
})
class BatchJobsRestartIntegrationTests {
    @Autowired JobLauncher launcher;
    @Autowired ApplicationContext context;
    @Autowired JdbcClient jdbc;
    @SpyBean(name = "transaccionJdbcWriter") JdbcBatchItemWriter<TransaccionProcesada> transactions;
    @SpyBean(name = "interesWriter") JdbcBatchItemWriter<InteresProcesado> interest;
    @SpyBean(name = "movimientoAnualWriter") JdbcBatchItemWriter<MovimientoAnualProcesado> statements;

    @ParameterizedTest
    @CsvSource({
            "transaccionesDiariasJob,transaccion_procesada,8",
            "interesesMensualesJob,interes_procesado,7",
            "estadosCuentaAnualesJob,movimiento_anual_procesado,8"
    })
    void rollsBackFailedChunkAndRestartsSameInstanceWithoutDuplicatingCommittedRows(
            String jobName, String table, int expected) throws Exception {
        jdbc.sql("DELETE FROM " + table).update();
        jdbc.sql("DELETE FROM anomaly_event_outbox").update();
        JdbcBatchItemWriter<?> writer = switch (jobName) {
            case "transaccionesDiariasJob" -> transactions;
            case "interesesMensualesJob" -> interest;
            default -> statements;
        };
        var writes = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (writes.incrementAndGet() == 2) {
                throw new IllegalStateException("Simulated critical failure after JDBC write");
            }
            return null;
        }).when(writer).write(any());
        Job job = context.getBean(jobName, Job.class);
        var parameters = new JobParametersBuilder().addString("eftRestart", UUID.randomUUID().toString())
                .toJobParameters();
        JobExecution failed = launcher.run(job, parameters);
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(count(table)).isEqualTo(1); // Second SQL write must have rolled back.
        JobExecution restarted = launcher.run(job, parameters);
        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstance().getInstanceId()).isEqualTo(failed.getJobInstance().getInstanceId());
        assertThat(count(table)).isEqualTo(expected);
        if (jobName.equals("transaccionesDiariasJob")) {
            assertThat(count("anomaly_event_outbox")).isEqualTo(1);
            assertThat(jdbc.sql("SELECT COUNT(DISTINCT transaccion_id) FROM transaccion_procesada")
                    .query(Integer.class).single()).isEqualTo(expected);
        }
        assertThatThrownBy(() -> launcher.run(job, parameters))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    private int count(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
    }
}