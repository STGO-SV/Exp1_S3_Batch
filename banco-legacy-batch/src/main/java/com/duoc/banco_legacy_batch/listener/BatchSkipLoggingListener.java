package com.duoc.banco_legacy_batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;

@Component
public class BatchSkipLoggingListener implements SkipListener<Object, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchSkipLoggingListener.class);

    @Override
    public void onSkipInRead(Throwable throwable) {
        LOGGER.warn("event=item_skipped stage=read item=unavailable reason={} exception={}",
                throwable.getMessage(), throwable.getClass().getSimpleName());
    }

    @Override
    public void onSkipInProcess(Object item, Throwable throwable) {
        LOGGER.warn("event=item_skipped stage=process item={} reason={} exception={}",
                item, throwable.getMessage(), throwable.getClass().getSimpleName());
    }

    @Override
    public void onSkipInWrite(Object item, Throwable throwable) {
        LOGGER.warn("event=item_skipped stage=write item={} reason={} exception={}",
                item, throwable.getMessage(), throwable.getClass().getSimpleName());
    }
}
