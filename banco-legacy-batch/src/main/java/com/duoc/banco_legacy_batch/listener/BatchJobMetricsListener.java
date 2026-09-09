package com.duoc.banco_legacy_batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;

@Component
public class BatchJobMetricsListener implements JobExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchJobMetricsListener.class);
    private final int gridSize;
    private final int threadCount;
    private final int chunkSize;

    public BatchJobMetricsListener(@Value("${batch.partition.grid-size:4}") int gridSize,
                                   @Value("${batch.partition.thread-count:4}") int threadCount,
                                   @Value("${batch.chunk-size:100}") int chunkSize) {
        this.gridSize = gridSize;
        this.threadCount = threadCount;
        this.chunkSize = chunkSize;
    }

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
        AggregatedMetrics metrics = aggregate(jobExecution.getStepExecutions());
        LOGGER.info("event=job_end job={} executionId={} status={} durationMs={} gridSize={} threads={} "
                        + "chunkSize={} partitions={} inputCount={} readCount={} filterCount={} "
                        + "readSkipCount={} processSkipCount={} writeSkipCount={} writeCount={} "
                        + "totalSkipCount={} retryCount={} failedPartitions={} failures={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getId(), jobExecution.getStatus(),
                durationMs, gridSize, threadCount, chunkSize, metrics.partitions(), metrics.inputCount(),
                metrics.readCount(), metrics.filterCount(), metrics.readSkipCount(), metrics.processSkipCount(),
                metrics.writeSkipCount(), metrics.writeCount(), metrics.totalSkipCount(), metrics.retryCount(),
                metrics.failedPartitions(), jobExecution.getAllFailureExceptions().size());
    }

    static AggregatedMetrics aggregate(Collection<StepExecution> stepExecutions) {
        var workers = stepExecutions.stream()
                .filter(step -> step.getStepName().contains(":"))
                .toList();
        long read = workers.stream().mapToLong(step -> step.getReadCount()).sum();
        long readSkipped = workers.stream().mapToLong(step -> step.getReadSkipCount()).sum();
        long processSkipped = workers.stream().mapToLong(step -> step.getProcessSkipCount()).sum();
        long writeSkipped = workers.stream().mapToLong(step -> step.getWriteSkipCount()).sum();
        long filtered = workers.stream().mapToLong(step -> step.getFilterCount()).sum();
        long written = workers.stream().mapToLong(step -> step.getWriteCount()).sum();
        long retries = workers.stream().mapToLong(step ->
                step.getExecutionContext().getLong(RetryMetricsListener.RETRY_COUNT_KEY, 0L)).sum();
        long failedPartitions = workers.stream().filter(step -> step.getStatus() == BatchStatus.FAILED).count();
        return new AggregatedMetrics(workers.size(), read + readSkipped, read, filtered, readSkipped,
                processSkipped, writeSkipped, written, readSkipped + processSkipped + writeSkipped,
                retries, failedPartitions);
    }

    record AggregatedMetrics(long partitions, long inputCount, long readCount, long filterCount,
                             long readSkipCount, long processSkipCount, long writeSkipCount,
                             long writeCount, long totalSkipCount, long retryCount, long failedPartitions) {
    }
}
