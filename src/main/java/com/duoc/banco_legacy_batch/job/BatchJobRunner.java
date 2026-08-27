package com.duoc.banco_legacy_batch.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "batch.run-on-startup", havingValue = "true", matchIfMissing = true)
public class BatchJobRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchJobRunner.class);

    private final JobLauncher jobLauncher;
    private final List<Job> jobs;

    public BatchJobRunner(JobLauncher jobLauncher,
                          @Qualifier("transaccionesDiariasJob") Job transaccionesJob,
                          @Qualifier("interesesMensualesJob") Job interesesJob,
                          @Qualifier("estadosCuentaAnualesJob") Job estadosAnualesJob) {
        this.jobLauncher = jobLauncher;
        this.jobs = List.of(transaccionesJob, interesesJob, estadosAnualesJob);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        long ejecucion = System.currentTimeMillis();
        for (Job job : jobs) {
            JobExecution execution = jobLauncher.run(job,
                    new JobParametersBuilder().addLong("ejecucion", ejecucion).toJobParameters());
            LOGGER.info("Job {} terminó con estado {}", job.getName(), execution.getStatus());
            if (execution.getStatus() != BatchStatus.COMPLETED) {
                throw new IllegalStateException("Falló el job " + job.getName());
            }
        }
    }
}
