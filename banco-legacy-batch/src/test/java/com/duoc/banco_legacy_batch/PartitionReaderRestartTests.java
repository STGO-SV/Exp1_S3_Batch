package com.duoc.banco_legacy_batch;
import com.duoc.banco_legacy_batch.reader.PartitionRangeItemReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.core.io.FileSystemResource;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
class PartitionReaderRestartTests {
    @TempDir Path directory;
    @Test
    void resumesAtCommittedCheckpointIncludingFilteredRows() throws Exception {
        Path csv = directory.resolve("restart.csv");
        Files.writeString(csv, "id\n1\n3\n2\n4\n5\n");
        var checkpoint = new ExecutionContext();
        var first = reader(csv);
        first.open(checkpoint);
        assertThat(first.read()).isEqualTo(3L);
        first.update(checkpoint);
        assertThat(first.read()).isEqualTo(4L);
        first.close();
        var restarted = reader(csv);
        restarted.open(checkpoint);
        assertThat(restarted.read()).isEqualTo(4L);
        assertThat(restarted.read()).isEqualTo(5L);
        assertThat(restarted.read()).isNull();
        restarted.close();
    }
    private PartitionRangeItemReader<Long> reader(Path csv) {
        var delegate = new FlatFileItemReaderBuilder<Long>().name("restart-reader")
                .resource(new FileSystemResource(csv)).linesToSkip(1)
                .lineMapper((line, number) -> Long.valueOf(line)).build();
        return new PartitionRangeItemReader<>(delegate, value -> value, 3L, 5L);
    }
}