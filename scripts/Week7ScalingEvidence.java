import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class Week7ScalingEvidence {
    private Week7ScalingEvidence() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || args[0].isBlank()) {
            throw new IllegalArgumentException("Uso: Week7ScalingEvidence <correlationId>");
        }
        String correlationId = args[0];
        try (Connection connection = DriverManager.getConnection(
                env("ANOMALY_DB_URL", "jdbc:postgresql://localhost:5432/banco_legacy"),
                env("ANOMALY_DB_USERNAME", "postgres"),
                env("ANOMALY_DB_PASSWORD", "postgres"))) {
            printDistribution(connection, correlationId);
            printTotals(connection, correlationId);
        }
    }

    private static void printDistribution(Connection connection, String correlationId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT consumer_instance, partition_id, COUNT(*) AS processed
                  FROM processed_anomaly_event
                 WHERE correlation_id = ?
                 GROUP BY consumer_instance, partition_id
                 ORDER BY consumer_instance, partition_id
                """)) {
            statement.setString(1, correlationId);
            try (ResultSet rows = statement.executeQuery()) {
                System.out.println("consumer_instance|partition_id|processed");
                while (rows.next()) {
                    System.out.printf("%s|%d|%d%n", rows.getString(1), rows.getInt(2), rows.getLong(3));
                }
            }
        }
    }

    private static void printTotals(Connection connection, String correlationId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) AS rows,
                       COUNT(DISTINCT transaction_id) AS distinct_transactions,
                       COUNT(DISTINCT consumer_instance) AS consumers,
                       COUNT(DISTINCT partition_id) AS partitions
                  FROM processed_anomaly_event
                 WHERE correlation_id = ?
                """)) {
            statement.setString(1, correlationId);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                long rows = row.getLong(1);
                long distinctTransactions = row.getLong(2);
                System.out.printf("rows=%d distinctTransactions=%d duplicates=%d consumers=%d partitions=%d%n",
                        rows, distinctTransactions, rows - distinctTransactions, row.getLong(3), row.getLong(4));
            }
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
