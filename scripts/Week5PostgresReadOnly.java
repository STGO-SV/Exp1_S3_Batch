import java.sql.*;
import java.util.List;

public final class Week5PostgresReadOnly {
    private Week5PostgresReadOnly() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Modo inválido");
        String url = required("DB_URL");
        String user = required("DB_USER");
        String password = required("DB_PASSWORD");
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                switch (args[0]) {
                    case "ping" -> System.out.println(scalar(connection, "SELECT 1"));
                    case "account" -> System.out.println(selectAccount(connection));
                    case "balance" -> System.out.println(balance(connection, account(args)));
                    case "explain" -> explain(connection, account(args));
                    default -> throw new IllegalArgumentException("Modo no permitido");
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private static long account(String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("Falta accountId");
        return Long.parseLong(args[1]);
    }

    private static long selectAccount(Connection connection) throws SQLException {
        String latest = """
                SELECT cuenta_id FROM (
                  SELECT DISTINCT ON (cuenta_id) cuenta_id, saldo_procesado
                  FROM interes_procesado ORDER BY cuenta_id, id DESC
                ) latest WHERE %s AND saldo_procesado >= 0.01 ORDER BY cuenta_id LIMIT 1
                """;
        Long preferred = optionalLong(connection, latest.formatted("cuenta_id = 101"));
        if (preferred != null) return preferred;
        Long fallback = optionalLong(connection, latest.formatted("TRUE"));
        if (fallback == null) throw new IllegalStateException("No existe una cuenta apta");
        return fallback;
    }

    private static String balance(Connection connection, long accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT saldo_procesado FROM interes_procesado WHERE cuenta_id=? ORDER BY id DESC LIMIT 1")) {
            statement.setLong(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Cuenta inexistente");
                return result.getBigDecimal(1).toPlainString();
            }
        }
    }

    private static void explain(Connection connection, long accountId) throws SQLException {
        List<String> queries = List.of(
            "SELECT cuenta_id,nombre,saldo_original,tasa,saldo_procesado,tipo FROM interes_procesado WHERE cuenta_id=? ORDER BY id DESC LIMIT 1",
            "SELECT saldo_procesado,tipo FROM interes_procesado WHERE cuenta_id=? ORDER BY id DESC LIMIT 1",
            "SELECT saldo_procesado FROM interes_procesado WHERE cuenta_id=? ORDER BY id DESC LIMIT 1",
            "SELECT cuenta_id,fecha,tipo_transaccion,monto,descripcion FROM movimiento_anual_procesado WHERE cuenta_id=? ORDER BY fecha DESC,id DESC LIMIT 20",
            "SELECT fecha,tipo_transaccion,monto FROM movimiento_anual_procesado WHERE cuenta_id=? ORDER BY fecha DESC,id DESC LIMIT 5",
            "SELECT tipo_transaccion,monto FROM movimiento_anual_procesado WHERE cuenta_id=? ORDER BY fecha DESC,id DESC LIMIT 3",
            "SELECT transaccion_id,fecha,monto,tipo,anomalia FROM transaccion_procesada WHERE anomalia=TRUE ORDER BY fecha DESC,id DESC LIMIT 10"
        );
        for (int index = 0; index < queries.size(); index++) {
            System.out.println("PLAN " + (index + 1));
            try (PreparedStatement statement = connection.prepareStatement("EXPLAIN (ANALYZE, BUFFERS) " + queries.get(index))) {
                if (queries.get(index).contains("?")) statement.setLong(1, accountId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) System.out.println(result.getString(1));
                }
            }
            System.out.println();
        }
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) throw new IllegalStateException("Sin resultado");
            return result.getString(1);
        }
    }

    private static Long optionalLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getLong(1) : null;
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Falta " + name);
        return value;
    }
}
