import java.math.BigDecimal;
import java.sql.*;
import java.util.List;
import java.util.concurrent.*;

public final class Week5AtmPostgresLab {
    private Week5AtmPostgresLab() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) throw new IllegalArgumentException("Argumentos inválidos");
        String mode = args[0];
        long accountId = Long.parseLong(args[1]);
        String marker = args[2];
        try (Connection connection = open()) {
            switch (mode) {
                case "setup" -> setup(connection, accountId, marker);
                case "balance" -> System.out.println(balance(connection, accountId).toPlainString());
                case "movement-count" -> System.out.println(countMovements(connection, accountId));
                case "cleanup" -> cleanup(connection, accountId, marker);
                case "concurrency" -> concurrency(accountId);
                default -> throw new IllegalArgumentException("Modo no permitido");
            }
        }
    }

    private static void setup(Connection connection, long accountId, String marker) throws SQLException {
        connection.setAutoCommit(false);
        try {
            assertUnused(connection, accountId);
            insertAccount(connection, accountId, marker, new BigDecimal("10.00"));
            assertUnused(connection, accountId + 1);
            insertAccount(connection, accountId + 1, marker, new BigDecimal("150.00"));
            connection.commit();
        } catch (Throwable failure) {
            connection.rollback();
            throw failure;
        }
    }

    private static void concurrency(long accountId) throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = List.of(
                    pool.submit(() -> withdraw(accountId, start)),
                    pool.submit(() -> withdraw(accountId, start)));
            start.countDown();
            String first = results.get(0).get(20, TimeUnit.SECONDS);
            String second = results.get(1).get(20, TimeUnit.SECONDS);
            try (Connection verification = open()) {
                BigDecimal finalBalance = balance(verification, accountId);
                long movements = countMovements(verification, accountId);
                long completed = List.of(first, second).stream().filter("COMPLETED"::equals).count();
                long rejected = List.of(first, second).stream().filter("REJECTED"::equals).count();
                boolean passed = completed == 1 && rejected == 1
                        && finalBalance.compareTo(new BigDecimal("50.00")) == 0 && movements == 1;
                System.out.printf("passed=%s; outcomes=%s,%s; balance=%s; movements=%d%n",
                        passed, first, second, finalBalance.toPlainString(), movements);
                if (!passed) throw new IllegalStateException("Falló la validación concurrente PostgreSQL");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static String withdraw(long accountId, CountDownLatch start) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                BigDecimal current;
                long rowId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT id, saldo_procesado FROM interes_procesado
                        WHERE cuenta_id=? ORDER BY id DESC LIMIT 1 FOR UPDATE
                        """)) {
                    statement.setLong(1, accountId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new IllegalStateException("Cuenta de laboratorio inexistente");
                        rowId = result.getLong(1);
                        current = result.getBigDecimal(2);
                    }
                }
                if (current.compareTo(new BigDecimal("100.00")) < 0) {
                    connection.rollback();
                    return "REJECTED";
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE interes_procesado SET saldo_procesado=? WHERE id=?")) {
                    update.setBigDecimal(1, current.subtract(new BigDecimal("100.00")));
                    update.setLong(2, rowId);
                    requireOne(update.executeUpdate(), "actualización");
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO movimiento_anual_procesado
                        (cuenta_id, fecha, tipo_transaccion, monto, descripcion)
                        VALUES (?, CURRENT_DATE, 'retiro', 100.00, 'Retiro ATM')
                        """)) {
                    insert.setLong(1, accountId);
                    requireOne(insert.executeUpdate(), "movimiento");
                }
                connection.commit();
                return "COMPLETED";
            } catch (Throwable failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static void cleanup(Connection connection, long accountId, String marker) throws SQLException {
        connection.setAutoCommit(false);
        try {
            for (long id : List.of(accountId, accountId + 1)) {
                try (PreparedStatement ownership = connection.prepareStatement(
                        "SELECT COUNT(*) FROM interes_procesado WHERE cuenta_id=? AND nombre=?")) {
                    ownership.setLong(1, id);
                    ownership.setString(2, marker);
                    try (ResultSet result = ownership.executeQuery()) {
                        result.next();
                        if (result.getLong(1) != 1) throw new IllegalStateException("No se pudo acreditar propiedad del dato de laboratorio " + id);
                    }
                }
                try (PreparedStatement movements = connection.prepareStatement(
                        "DELETE FROM movimiento_anual_procesado WHERE cuenta_id=?")) {
                    movements.setLong(1, id);
                    movements.executeUpdate();
                }
                try (PreparedStatement account = connection.prepareStatement(
                        "DELETE FROM interes_procesado WHERE cuenta_id=? AND nombre=?")) {
                    account.setLong(1, id);
                    account.setString(2, marker);
                    requireOne(account.executeUpdate(), "limpieza de cuenta");
                }
            }
            connection.commit();
        } catch (Throwable failure) {
            connection.rollback();
            throw failure;
        }
    }

    private static void assertUnused(Connection connection, long accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT (SELECT COUNT(*) FROM interes_procesado WHERE cuenta_id=?)
                     + (SELECT COUNT(*) FROM movimiento_anual_procesado WHERE cuenta_id=? )
                """)) {
            statement.setLong(1, accountId);
            statement.setLong(2, accountId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getLong(1) != 0) throw new IllegalStateException("El identificador reservado ya contiene datos");
            }
        }
    }

    private static void insertAccount(Connection connection, long accountId, String marker, BigDecimal balance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO interes_procesado
                (cuenta_id, nombre, saldo_original, tasa, saldo_procesado, tipo)
                VALUES (?, ?, ?, 0, ?, 'ahorro')
                """)) {
            statement.setLong(1, accountId);
            statement.setString(2, marker);
            statement.setBigDecimal(3, balance);
            statement.setBigDecimal(4, balance);
            requireOne(statement.executeUpdate(), "creación de cuenta de laboratorio");
        }
    }

    private static BigDecimal balance(Connection connection, long accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT saldo_procesado FROM interes_procesado WHERE cuenta_id=? ORDER BY id DESC LIMIT 1")) {
            statement.setLong(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Cuenta inexistente");
                return result.getBigDecimal(1);
            }
        }
    }

    private static long countMovements(Connection connection, long accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM movimiento_anual_procesado WHERE cuenta_id=? AND tipo_transaccion='retiro'")) {
            statement.setLong(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void requireOne(int rows, String operation) {
        if (rows != 1) throw new IllegalStateException("Se esperaba una fila en " + operation + "; filas: " + rows);
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(required("DB_URL"), required("DB_USER"), required("DB_PASSWORD"));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Falta " + name);
        return value;
    }
}
