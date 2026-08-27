package com.duoc.banco_legacy_batch;

import com.duoc.banco_legacy_batch.listener.BatchJobMetricsListener;
import com.duoc.banco_legacy_batch.listener.BatchStepMetricsListener;
import com.duoc.banco_legacy_batch.listener.RetryMetricsListener;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RetryPolicyIntegrationTests {

    @Autowired private JobRepository jobRepository;
    @Autowired private JobLauncher jobLauncher;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private RetryMetricsListener retryListener;
    @Autowired private BatchStepMetricsListener stepListener;
    @Autowired private BatchJobMetricsListener jobListener;

    @Test
    void recuperaLaEscrituraDespuesDeUnErrorTransitorio() throws Exception {
        AtomicInteger intentos = new AtomicInteger();
        Step step = crearStep("retryRecoveryStep", intentos, 1);
        JobExecution execution = ejecutar("retryRecoveryJob", step);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(intentos).hasValue(2);
        assertThat(unicoStep(execution).getWriteCount()).isEqualTo(1);
        assertThat(retries(execution)).isEqualTo(1);
    }

    @Test
    void fallaAlSuperarElLimiteDeRetry() throws Exception {
        AtomicInteger intentos = new AtomicInteger();
        Step step = crearStep("retryExhaustedStep", intentos, Integer.MAX_VALUE);
        JobExecution execution = ejecutar("retryExhaustedJob", step);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(intentos).hasValue(3);
        assertThat(unicoStep(execution).getWriteCount()).isZero();
        assertThat(retries(execution)).isEqualTo(3);
    }

    private Step crearStep(String baseName, AtomicInteger intentos, int fallosAntesDeExito) {
        String name = baseName + "-" + UUID.randomUUID();
        return new StepBuilder(name, jobRepository)
                .<Integer, Integer>chunk(1, transactionManager)
                .reader(new ListItemReader<>(List.of(1)))
                .writer(chunk -> {
                    if (intentos.getAndIncrement() < fallosAntesDeExito) {
                        throw new TransientDataAccessResourceException("Fallo transitorio simulado");
                    }
                })
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .listener(retryListener)
                .listener(stepListener)
                .build();
    }

    private JobExecution ejecutar(String baseName, Step step) throws Exception {
        Job job = new JobBuilder(baseName + "-" + UUID.randomUUID(), jobRepository)
                .listener(jobListener)
                .start(step)
                .build();
        return jobLauncher.run(job, new JobParametersBuilder()
                .addString("run", UUID.randomUUID().toString())
                .toJobParameters());
    }

    private long retries(JobExecution execution) {
        return unicoStep(execution).getExecutionContext().getLong(RetryMetricsListener.RETRY_COUNT_KEY, 0L);
    }

    private StepExecution unicoStep(JobExecution execution) {
        assertThat(execution.getStepExecutions()).hasSize(1);
        return execution.getStepExecutions().iterator().next();
    }
}
