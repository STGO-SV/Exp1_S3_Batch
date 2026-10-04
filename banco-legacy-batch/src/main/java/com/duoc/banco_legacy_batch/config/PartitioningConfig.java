package com.duoc.banco_legacy_batch.config;

import com.duoc.banco_legacy_batch.partition.CsvColumnRangePartitioner;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.file.Path;

@Configuration
public class PartitioningConfig {

    @Bean
    public TaskExecutor partitionTaskExecutor(@Value("${batch.partition.thread-count:4}") int threadCount,
                                              @Value("${batch.partition.grid-size:4}") int gridSize) {
        if (threadCount < 1 || gridSize < 1) {
            throw new IllegalArgumentException("batch.partition.thread-count y grid-size deben ser positivos");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadCount);
        executor.setMaxPoolSize(threadCount);
        executor.setQueueCapacity(Math.max(gridSize - threadCount, 0));
        executor.setThreadNamePrefix("batch-partition-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

    @Bean
    @Qualifier("transaccionPartitioner")
    public Partitioner transaccionPartitioner(@Value("${batch.input-directory}") String inputDirectory) {
        return new CsvColumnRangePartitioner(Path.of(inputDirectory, "transacciones.csv"), "id");
    }

    @Bean
    @Qualifier("interesPartitioner")
    public Partitioner interesPartitioner(@Value("${batch.input-directory}") String inputDirectory) {
        return new CsvColumnRangePartitioner(Path.of(inputDirectory, "intereses.csv"), "cuenta_id");
    }

    @Bean
    @Qualifier("movimientoAnualPartitioner")
    public Partitioner movimientoAnualPartitioner(@Value("${batch.input-directory}") String inputDirectory) {
        return new CsvColumnRangePartitioner(Path.of(inputDirectory, "cuentas_anuales.csv"), "cuenta_id");
    }
}
