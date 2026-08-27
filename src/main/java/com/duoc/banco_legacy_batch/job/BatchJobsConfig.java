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
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchJobsConfig {

    private static final int CHUNK_SIZE = 10;

    @Bean
    public Step transaccionesStep(JobRepository repository, PlatformTransactionManager transactionManager,
                                  FlatFileItemReader<Transaccion> transaccionReader,
                                  ItemProcessor<Transaccion, TransaccionProcesada> transaccionProcessor,
                                  JdbcBatchItemWriter<TransaccionProcesada> transaccionWriter,
                                  BatchStepMetricsListener stepListener,
                                  BatchSkipLoggingListener skipListener,
                                  RetryMetricsListener retryListener) {
        return new StepBuilder("transaccionesStep", repository)
                .<Transaccion, TransaccionProcesada>chunk(CHUNK_SIZE, transactionManager)
                .reader(transaccionReader)
                .processor(transaccionProcessor)
                .writer(transaccionWriter)
                .faultTolerant()
                .skip(FlatFileParseException.class)
                .skip(InvalidBatchDataException.class)
                .skipLimit(10)
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .listener(skipListener)
                .listener(retryListener)
                .listener(stepListener)
                .build();
    }

    @Bean
    public Step interesesStep(JobRepository repository, PlatformTransactionManager transactionManager,
                              FlatFileItemReader<CuentaInteres> interesReader,
                              ItemProcessor<CuentaInteres, InteresProcesado> interesProcessor,
                              JdbcBatchItemWriter<InteresProcesado> interesWriter,
                              BatchStepMetricsListener stepListener,
                              BatchSkipLoggingListener skipListener,
                              RetryMetricsListener retryListener) {
        return new StepBuilder("interesesStep", repository)
                .<CuentaInteres, InteresProcesado>chunk(CHUNK_SIZE, transactionManager)
                .reader(interesReader)
                .processor(interesProcessor)
                .writer(interesWriter)
                .faultTolerant()
                .skip(FlatFileParseException.class)
                .skip(InvalidBatchDataException.class)
                .skipLimit(10)
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .listener(skipListener)
                .listener(retryListener)
                .listener(stepListener)
                .build();
    }

    @Bean
    public Step estadoAnualStep(JobRepository repository, PlatformTransactionManager transactionManager,
                                FlatFileItemReader<MovimientoAnual> movimientoAnualReader,
                                ItemProcessor<MovimientoAnual, MovimientoAnualProcesado> movimientoAnualProcessor,
                                JdbcBatchItemWriter<MovimientoAnualProcesado> movimientoAnualWriter,
                                BatchStepMetricsListener stepListener,
                                BatchSkipLoggingListener skipListener,
                                RetryMetricsListener retryListener) {
        return new StepBuilder("estadoAnualStep", repository)
                .<MovimientoAnual, MovimientoAnualProcesado>chunk(CHUNK_SIZE, transactionManager)
                .reader(movimientoAnualReader)
                .processor(movimientoAnualProcessor)
                .writer(movimientoAnualWriter)
                .faultTolerant()
                .skip(FlatFileParseException.class)
                .skip(InvalidBatchDataException.class)
                .skipLimit(10)
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .listener(skipListener)
                .listener(retryListener)
                .listener(stepListener)
                .build();
    }

    @Bean
    public Job transaccionesDiariasJob(JobRepository repository, Step transaccionesStep,
                                       BatchJobMetricsListener jobListener) {
        return new JobBuilder("transaccionesDiariasJob", repository)
                .listener(jobListener).start(transaccionesStep).build();
    }

    @Bean
    public Job interesesMensualesJob(JobRepository repository, Step interesesStep,
                                     BatchJobMetricsListener jobListener) {
        return new JobBuilder("interesesMensualesJob", repository)
                .listener(jobListener).start(interesesStep).build();
    }

    @Bean
    public Job estadosCuentaAnualesJob(JobRepository repository, Step estadoAnualStep,
                                       BatchJobMetricsListener jobListener) {
        return new JobBuilder("estadosCuentaAnualesJob", repository)
                .listener(jobListener).start(estadoAnualStep).build();
    }
}
