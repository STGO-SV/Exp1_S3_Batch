package com.duoc.banco_legacy_batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.stereotype.Component;

@Component
public class RetryMetricsListener implements RetryListener {

    public static final String RETRY_COUNT_KEY = "batch.retry.count";
    private static final Logger LOGGER = LoggerFactory.getLogger(RetryMetricsListener.class);

    @Override
    public <T, E extends Throwable> void onError(RetryContext context,
                                                 RetryCallback<T, E> callback,
                                                 Throwable throwable) {
        if (!(throwable instanceof TransientDataAccessException)) {
            return;
        }
        StepContext stepContext = StepSynchronizationManager.getContext();
        String stepName = "unknown";
        if (stepContext != null) {
            StepExecution stepExecution = stepContext.getStepExecution();
            stepName = stepExecution.getStepName();
            long retries = stepExecution.getExecutionContext().getLong(RETRY_COUNT_KEY, 0L) + 1;
            stepExecution.getExecutionContext().putLong(RETRY_COUNT_KEY, retries);
        }
        LOGGER.warn("event=item_retry step={} attempt={} exception={} reason={}",
                stepName, context.getRetryCount(), throwable.getClass().getSimpleName(), throwable.getMessage());
    }
}
