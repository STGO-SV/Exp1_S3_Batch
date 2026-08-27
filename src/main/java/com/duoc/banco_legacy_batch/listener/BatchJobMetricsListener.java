package com.duoc.banco_legacy_batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BatchJobMetricsListener implements JobExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchJobMetricsListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        LOGGER.info("event=job_start job={} executionId={} status={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId(), jobExecution.getStatus());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        long durationMs = jobExecution.getStartTime() == null || jobExecution.getEndTime() == null
                ? -1
                : Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis();
        LOGGER.info("event=job_end job={} executionId={} status={} durationMs={} failures={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId(), jobExecution.getStatus(),
                durationMs, jobExecution.getAllFailureExceptions().size());
    }
}
