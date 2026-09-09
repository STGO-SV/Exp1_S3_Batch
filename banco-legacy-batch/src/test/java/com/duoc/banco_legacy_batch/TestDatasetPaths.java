package com.duoc.banco_legacy_batch;

import java.nio.file.Files;
import java.nio.file.Path;

final class TestDatasetPaths {

    private TestDatasetPaths() {
    }

    static String week3() {
        String direct = firstNonBlank(System.getProperty("batch.test-input-directory"),
                System.getenv("BATCH_TEST_INPUT_DIR"));
        String root = firstNonBlank(System.getProperty("batch.test-data-root"),
                System.getenv("BATCH_TEST_DATA_ROOT"));
        Path path = direct != null
                ? Path.of(direct)
                : root != null
                ? Path.of(root, "semana_3")
                : Path.of("..", "..", "bank_legacy_data", "data", "semana_3");
        Path resolved = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(resolved)) {
            throw new IllegalStateException("No se encontró el dataset de Semana 3 en " + resolved
                    + ". Configure -Dbatch.test-input-directory=<ruta> o -Dbatch.test-data-root=<ruta-data>.");
        }
        return resolved.toString();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }
}
