package com.duoc.banco_legacy_batch;
import com.duoc.banco_legacy_batch.config.PartitioningConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class PartitionExecutorConfigurationTests {
    @ParameterizedTest
    @CsvSource({"0,4", "-1,4", "4,0", "4,-1"})
    void rejectsInvalidParallelismBeforeLaunchingJobs(int threads, int grid) {
        assertThatThrownBy(() -> new PartitioningConfig().partitionTaskExecutor(threads, grid))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positivos");
    }
}