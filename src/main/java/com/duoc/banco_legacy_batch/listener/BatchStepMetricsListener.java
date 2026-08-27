package com.duoc.banco_legacy_batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class BatchStepMetricsListener implements StepExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchStepMetricsListener.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        LOGGER.info("event=step_start step={} executionId={} status={}",
                stepExecution.getStepName(), stepExecution.getId(), stepExecution.getStatus());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        long durationMs = stepExecution.getStartTime() == null
                ? -1
                : Duration.between(stepExecution.getStartTime(),
                        stepExecution.getEndTime() == null ? LocalDateTime.now() : stepExecution.getEndTime()).toMillis();
        long retryCount = stepExecution.getExecutionContext().getLong(RetryMetricsListener.RETRY_COUNT_KEY, 0L);
        LOGGER.info("event=step_end step={} executionId={} status={} readCount={} writeCount={} "
                        + "filterCount={} readSkipCount={} processSkipCount={} writeSkipCount={} "
                        + "retryCount={} failures={} durationMs={}",
                stepExecution.getStepName(), stepExecution.getId(), stepExecution.getStatus(),
                stepExecution.getReadCount(), stepExecution.getWriteCount(), stepExecution.getFilterCount(),
                stepExecution.getReadSkipCount(), stepExecution.getProcessSkipCount(),
                stepExecution.getWriteSkipCount(), retryCount, stepExecution.getFailureExceptions().size(), durationMs);
        return stepExecution.getExitStatus();
    }
}
