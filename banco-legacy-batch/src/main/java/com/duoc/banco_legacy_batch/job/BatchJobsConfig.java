package com.duoc.banco_legacy_batch.job;

import com.duoc.banco_legacy_batch.exception.InvalidBatchDataException;
import com.duoc.banco_legacy_batch.listener.BatchJobMetricsListener;
import com.duoc.banco_legacy_batch.listener.BatchSkipLoggingListener;
import com.duoc.banco_legacy_batch.listener.BatchStepMetricsListener;
import com.duoc.banco_legacy_batch.listener.RetryMetricsListener;
import com.duoc.banco_legacy_batch.model.CuentaInteres;
import com.duoc.banco_legacy_batch.model.InteresProcesado;
import com.duoc.banco_legacy_batch.model.MovimientoAnual;
import com.duoc.banco_legacy_batch.model.MovimientoAnualProcesado;
import com.duoc.banco_legacy_batch.model.Transaccion;
import com.duoc.banco_legacy_batch.model.TransaccionProcesada;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchJobsConfig {

    @Bean
    public Step transaccionesWorkerStep(JobRepository repository, PlatformTransactionManager transactionManager,
                                        ItemStreamReader<Transaccion> transaccionReader,
                                        ItemProcessor<Transaccion, TransaccionProcesada> transaccionProcessor,
                                        @Qualifier("transaccionWriter") ItemWriter<TransaccionProcesada> transaccionWriter,
                                        BatchStepMetricsListener stepListener,
                                        BatchSkipLoggingListener skipListener,
                                        RetryMetricsListener retryListener,
                                        @Value("${batch.chunk-size:100}") int chunkSize,
                                        @Value("${batch.skip-limit:1000}") int skipLimit) {
        return new StepBuilder("transaccionesWorkerStep", repository)
                .<Transaccion, TransaccionProcesada>chunk(chunkSize, transactionManager)
                .reader(transaccionReader).processor(transaccionProcessor).writer(transaccionWriter)
                .faultTolerant()
                .skip(FlatFileParseException.class).skip(InvalidBatchDataException.class).skipLimit(skipLimit)
                .retry(TransientDataAccessException.class).retryLimit(3)
                .listener(skipListener).listener(retryListener).listener(stepListener)
                .build();
    }

    @Bean
    public Step interesesWorkerStep(JobRepository repository, PlatformTransactionManager transactionManager,
                                    ItemStreamReader<CuentaInteres> interesReader,
                                    ItemProcessor<CuentaInteres, InteresProcesado> interesProcessor,
                                    JdbcBatchItemWriter<InteresProcesado> interesWriter,
                                    BatchStepMetricsListener stepListener,
                                    BatchSkipLoggingListener skipListener,
                                    RetryMetricsListener retryListener,
                                    @Value("${batch.chunk-size:100}") int chunkSize,
                                    @Value("${batch.skip-limit:1000}") int skipLimit) {
        return new StepBuilder("interesesWorkerStep", repository)
                .<CuentaInteres, InteresProcesado>chunk(chunkSize, transactionManager)
                .reader(interesReader).processor(interesProcessor).writer(interesWriter)
                .faultTolerant()
                .skip(FlatFileParseException.class).skip(InvalidBatchDataException.class).skipLimit(skipLimit)
                .retry(TransientDataAccessException.class).retryLimit(3)
                .listener(skipListener).listener(retryListener).listener(stepListener)
                .build();
    }

    @Bean
    public Step estadoAnualWorkerStep(JobRepository repository, PlatformTransactionManager transactionManager,
                                      ItemStreamReader<MovimientoAnual> movimientoAnualReader,
                                      ItemProcessor<MovimientoAnual, MovimientoAnualProcesado> movimientoAnualProcessor,
                                      JdbcBatchItemWriter<MovimientoAnualProcesado> movimientoAnualWriter,
                                      BatchStepMetricsListener stepListener,
                                      BatchSkipLoggingListener skipListener,
                                      RetryMetricsListener retryListener,
                                      @Value("${batch.chunk-size:100}") int chunkSize,
                                      @Value("${batch.skip-limit:1000}") int skipLimit) {
        return new StepBuilder("estadoAnualWorkerStep", repository)
                .<MovimientoAnual, MovimientoAnualProcesado>chunk(chunkSize, transactionManager)
                .reader(movimientoAnualReader).processor(movimientoAnualProcessor).writer(movimientoAnualWriter)
                .faultTolerant()
                .skip(FlatFileParseException.class).skip(InvalidBatchDataException.class).skipLimit(skipLimit)
                .retry(TransientDataAccessException.class).retryLimit(3)
                .listener(skipListener).listener(retryListener).listener(stepListener)
                .build();
    }

    @Bean
    public Step transaccionesStep(JobRepository repository,
                                  @Qualifier("transaccionesWorkerStep") Step workerStep,
                                  @Qualifier("transaccionPartitioner") Partitioner partitioner,
                                  @Qualifier("partitionTaskExecutor") TaskExecutor taskExecutor,
                                  BatchStepMetricsListener stepListener,
                                  @Value("${batch.partition.grid-size:4}") int gridSize) {
        return managerStep("transaccionesStep", "transaccionesWorkerStep", repository,
                workerStep, partitioner, taskExecutor, stepListener, gridSize);
    }

    @Bean
    public Step interesesStep(JobRepository repository,
                              @Qualifier("interesesWorkerStep") Step workerStep,
                              @Qualifier("interesPartitioner") Partitioner partitioner,
                              @Qualifier("partitionTaskExecutor") TaskExecutor taskExecutor,
                              BatchStepMetricsListener stepListener,
                              @Value("${batch.partition.grid-size:4}") int gridSize) {
        return managerStep("interesesStep", "interesesWorkerStep", repository,
                workerStep, partitioner, taskExecutor, stepListener, gridSize);
    }

    @Bean
    public Step estadoAnualStep(JobRepository repository,
                                @Qualifier("estadoAnualWorkerStep") Step workerStep,
                                @Qualifier("movimientoAnualPartitioner") Partitioner partitioner,
                                @Qualifier("partitionTaskExecutor") TaskExecutor taskExecutor,
                                BatchStepMetricsListener stepListener,
                                @Value("${batch.partition.grid-size:4}") int gridSize) {
        return managerStep("estadoAnualStep", "estadoAnualWorkerStep", repository,
                workerStep, partitioner, taskExecutor, stepListener, gridSize);
    }

    private Step managerStep(String managerName, String workerName, JobRepository repository,
                             Step workerStep, Partitioner partitioner, TaskExecutor taskExecutor,
                             BatchStepMetricsListener listener, int gridSize) {
        return new StepBuilder(managerName, repository)
                .partitioner(workerName, partitioner).step(workerStep)
                .gridSize(gridSize).taskExecutor(taskExecutor).listener(listener).build();
    }

    @Bean
    public Job transaccionesDiariasJob(JobRepository repository,
                                       @Qualifier("transaccionesStep") Step managerStep,
                                       BatchJobMetricsListener jobListener) {
        return new JobBuilder("transaccionesDiariasJob", repository)
                .listener(jobListener).start(managerStep).build();
    }

    @Bean
    public Job interesesMensualesJob(JobRepository repository,
                                     @Qualifier("interesesStep") Step managerStep,
                                     BatchJobMetricsListener jobListener) {
        return new JobBuilder("interesesMensualesJob", repository)
                .listener(jobListener).start(managerStep).build();
    }

    @Bean
    public Job estadosCuentaAnualesJob(JobRepository repository,
                                       @Qualifier("estadoAnualStep") Step managerStep,
                                       BatchJobMetricsListener jobListener) {
        return new JobBuilder("estadosCuentaAnualesJob", repository)
                .listener(jobListener).start(managerStep).build();
    }
}
