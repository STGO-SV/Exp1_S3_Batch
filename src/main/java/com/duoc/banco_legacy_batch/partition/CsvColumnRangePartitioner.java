package com.duoc.banco_legacy_batch.partition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class CsvColumnRangePartitioner implements Partitioner {

    public static final String MIN_VALUE = "minValue";
    public static final String MAX_VALUE = "maxValue";
    private static final Logger LOGGER = LoggerFactory.getLogger(CsvColumnRangePartitioner.class);

    private final Path csvPath;
    private final String columnName;

    public CsvColumnRangePartitioner(Path csvPath, String columnName) {
        this.csvPath = csvPath;
        this.columnName = columnName;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Range range = readRange();
        int partitions = (int) Math.min(Math.max(gridSize, 1), range.max() - range.min() + 1);
        long targetSize = (long) Math.ceil((double) (range.max() - range.min() + 1) / partitions);
        Map<String, ExecutionContext> contexts = new LinkedHashMap<>();
        long start = range.min();
        for (int index = 0; index < partitions && start <= range.max(); index++) {
            long end = Math.min(start + targetSize - 1, range.max());
            ExecutionContext context = new ExecutionContext();
            context.putLong(MIN_VALUE, start);
            context.putLong(MAX_VALUE, end);
            contexts.put("partition" + index, context);
            LOGGER.info("event=partition_created file={} column={} partition={} minValue={} maxValue={}",
                    csvPath.getFileName(), columnName, index, start, end);
            start = end + 1;
        }
        return contexts;
    }

    private Range readRange() {
        try (var lines = Files.lines(csvPath)) {
            var iterator = lines.iterator();
            if (!iterator.hasNext()) {
                throw new IllegalStateException("CSV vacío: " + csvPath);
            }
            String[] headers = iterator.next().split(",", -1);
            int columnIndex = findColumn(headers);
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line.isBlank()) {
                    continue;
                }
                String[] values = line.split(",", -1);
                long value = Long.parseLong(values[columnIndex].trim());
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            if (min == Long.MAX_VALUE) {
                throw new IllegalStateException("CSV sin datos: " + csvPath);
            }
            return new Range(min, max);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("No fue posible calcular particiones para " + csvPath, exception);
        }
    }

    private int findColumn(String[] headers) {
        for (int index = 0; index < headers.length; index++) {
            if (columnName.equals(headers[index].trim())) {
                return index;
            }
        }
        throw new IllegalArgumentException("No existe la columna " + columnName + " en " + csvPath);
    }

    private record Range(long min, long max) {
    }
}
