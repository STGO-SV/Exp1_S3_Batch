package com.duoc.banco_legacy_batch.reader;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;

import java.util.function.ToLongFunction;

public class PartitionRangeItemReader<T> implements ItemStreamReader<T> {

    private final ItemStreamReader<T> delegate;
    private final ToLongFunction<T> keyExtractor;
    private final long minValue;
    private final long maxValue;

    public PartitionRangeItemReader(ItemStreamReader<T> delegate, ToLongFunction<T> keyExtractor,
                                    long minValue, long maxValue) {
        this.delegate = delegate;
        this.keyExtractor = keyExtractor;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    @Override
    public T read() throws Exception {
        T item;
        while ((item = delegate.read()) != null) {
            long value = keyExtractor.applyAsLong(item);
            if (value >= minValue && value <= maxValue) {
                return item;
            }
        }
        return null;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        delegate.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        delegate.update(executionContext);
    }

    @Override
    public void close() throws ItemStreamException {
        delegate.close();
    }
}
