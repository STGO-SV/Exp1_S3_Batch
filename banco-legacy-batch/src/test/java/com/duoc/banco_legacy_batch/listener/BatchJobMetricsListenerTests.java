package com.duoc.banco_legacy_batch.listener;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BatchJobMetricsListenerTests {

    @Test
    void agregaSoloWorkersSinDuplicarLosConteosDelManager() {
        StepExecution manager = step("managerStep", 999, 999, 999, 999, 999, 999, 999, BatchStatus.COMPLETED);
        StepExecution worker1 = step("managerStep:partition0", 45, 2, 3, 4, 5, 31, 1, BatchStatus.COMPLETED);
        StepExecution worker2 = step("managerStep:partition1", 55, 3, 4, 5, 6, 37, 2, BatchStatus.FAILED);

        BatchJobMetricsListener.AggregatedMetrics metrics = BatchJobMetricsListener.aggregate(
                List.of(manager, worker1, worker2));

        assertThat(metrics.partitions()).isEqualTo(2);
        assertThat(metrics.inputCount()).isEqualTo(105);
        assertThat(metrics.readCount()).isEqualTo(100);
        assertThat(metrics.filterCount()).isEqualTo(11);
        assertThat(metrics.readSkipCount()).isEqualTo(5);
        assertThat(metrics.processSkipCount()).isEqualTo(7);
        assertThat(metrics.writeSkipCount()).isEqualTo(9);
        assertThat(metrics.writeCount()).isEqualTo(68);
        assertThat(metrics.totalSkipCount()).isEqualTo(21);
        assertThat(metrics.retryCount()).isEqualTo(3);
        assertThat(metrics.failedPartitions()).isEqualTo(1);
    }

    private StepExecution step(String name, long read, long readSkip, long processSkip, long writeSkip,
                               long filtered, long written, long retries, BatchStatus status) {
        StepExecution step = new StepExecution(name, new JobExecution(1L));
        step.setReadCount(read);
        step.setReadSkipCount(readSkip);
        step.setProcessSkipCount(processSkip);
        step.setWriteSkipCount(writeSkip);
        step.setFilterCount(filtered);
        step.setWriteCount(written);
        step.getExecutionContext().putLong(RetryMetricsListener.RETRY_COUNT_KEY, retries);
        step.setStatus(status);
        return step;
    }
}
