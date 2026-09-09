package com.duoc.banco_legacy_batch;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PartitionBenchmarkTests {

    private static final int[] CONFIGURATIONS = {1, 2, 4, 8};
    private static final int ITERATIONS = 3;
    private static final int CHUNK_SIZE = 100;

    @Test
    void comparaConfiguracionesYGeneraCsvReproducible() throws Exception {
        List<Result> results = new ArrayList<>();
        for (int size : CONFIGURATIONS) {
            results.add(measure(size));
        }
        assertThat(results).allMatch(result -> result.status().equals("COMPLETED"));
        assertThat(results).allMatch(result -> result.readCount() == 3000L);
        writeCsv(results);
        results.forEach(result -> System.out.printf(
                "BENCHMARK grid=%d threads=%d chunk=%d medianMs=%d read=%d write=%d skip=%d status=%s%n",
                result.gridSize(), result.threads(), result.chunkSize(), result.medianMs(),
                result.readCount(), result.writeCount(), result.skipCount(), result.status()));
    }

    private Result measure(int size) throws Exception {
        String database = "benchmark_" + size + "_" + UUID.randomUUID().toString().replace("-", "");
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(BancoLegacyBatchApplication.class)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:" + database + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.batch.jdbc.initialize-schema=always",
                        "--spring.sql.init.mode=always",
                        "--batch.run-on-startup=false",
                        "--batch.input-directory=" + TestDatasetPaths.week3(),
                        "--batch.partition.grid-size=" + size,
                        "--batch.partition.thread-count=" + size,
                        "--batch.chunk-size=" + CHUNK_SIZE,
                        "--batch.skip-limit=1000",
                        "--logging.level.root=ERROR")) {
            JobLauncher launcher = context.getBean(JobLauncher.class);
            List<Job> jobs = List.of(
                    context.getBean("transaccionesDiariasJob", Job.class),
                    context.getBean("interesesMensualesJob", Job.class),
                    context.getBean("estadosCuentaAnualesJob", Job.class));
            List<Long> durations = new ArrayList<>();
            long read = 0;
            long written = 0;
            long skipped = 0;
            String status = "COMPLETED";
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                long start = System.nanoTime();
                for (Job job : jobs) {
                    JobExecution execution = launcher.run(job, new JobParametersBuilder()
                            .addString("benchmarkRun", UUID.randomUUID().toString()).toJobParameters());
                    status = execution.getStatus().name();
                    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
                    if (iteration == ITERATIONS - 1) {
                        var manager = execution.getStepExecutions().stream()
                                .filter(step -> !step.getStepName().contains(":"))
                                .findFirst().orElseThrow();
                        read += manager.getReadCount() + manager.getReadSkipCount();
                        written += manager.getWriteCount();
                        skipped += manager.getReadSkipCount() + manager.getProcessSkipCount()
                                + manager.getWriteSkipCount();
                    }
                }
                durations.add((System.nanoTime() - start) / 1_000_000);
            }
            durations.sort(Comparator.naturalOrder());
            return new Result(size, size, CHUNK_SIZE, durations.get(ITERATIONS / 2),
                    read, written, skipped, status);
        }
    }

    private void writeCsv(List<Result> results) throws Exception {
        Path output = Path.of("target", "benchmark-results.csv");
        StringBuilder csv = new StringBuilder("gridSize,threads,chunkSize,medianMs,readCount,writeCount,skipCount,status\n");
        for (Result result : results) {
            csv.append(result.gridSize()).append(',').append(result.threads()).append(',')
                    .append(result.chunkSize()).append(',').append(result.medianMs()).append(',')
                    .append(result.readCount()).append(',').append(result.writeCount()).append(',')
                    .append(result.skipCount()).append(',').append(result.status()).append('\n');
        }
        Files.createDirectories(output.getParent());
        Files.writeString(output, csv);
    }

    private record Result(int gridSize, int threads, int chunkSize, long medianMs,
                          long readCount, long writeCount, long skipCount, String status) {
    }
}
