package com.duoc.banco_legacy_batch;

import com.duoc.banco_legacy_batch.partition.CsvColumnRangePartitioner;
import com.duoc.banco_legacy_batch.reader.PartitionRangeItemReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PartitioningUnitTests {

    @TempDir
    Path tempDir;

    @Test
    void creaRangosContiguosSinSolapamientos() throws Exception {
        Path csv = tempDir.resolve("datos.csv");
        Files.writeString(csv, "id,valor\n1,a\n2,b\n3,c\n4,d\n5,e\n6,f\n7,g\n8,h\n9,i\n10,j\n");

        var partitions = new CsvColumnRangePartitioner(csv, "id").partition(4);

        assertThat(partitions).hasSize(4);
        assertThat(partitions.values())
                .extracting(context -> context.getLong("minValue"), context -> context.getLong("maxValue"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, 3L),
                        org.assertj.core.groups.Tuple.tuple(4L, 6L),
                        org.assertj.core.groups.Tuple.tuple(7L, 9L),
                        org.assertj.core.groups.Tuple.tuple(10L, 10L));
    }

    @Test
    void readerEntregaSoloElementosDeSuParticion() throws Exception {
        var reader = new PartitionRangeItemReader<>(
                new TestStreamReader(List.of(1L, 2L, 3L, 4L, 5L, 6L)), value -> value, 3L, 5L);
        reader.open(new ExecutionContext());
        List<Long> values = new ArrayList<>();
        Long value;
        while ((value = reader.read()) != null) {
            values.add(value);
        }
        reader.close();

        assertThat(values).containsExactly(3L, 4L, 5L);
    }

    private static class TestStreamReader implements ItemStreamReader<Long> {
        private final java.util.Iterator<Long> iterator;

        private TestStreamReader(List<Long> values) {
            this.iterator = values.iterator();
        }

        @Override
        public Long read() {
            return iterator.hasNext() ? iterator.next() : null;
        }
    }
}
