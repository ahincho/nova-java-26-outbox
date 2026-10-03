package pe.edu.nova.java.libs.outbox.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;
import pe.edu.nova.java.libs.outbox.OutboxException;

/** Lo que comparten los almacenes JDBC: el nombre de la tabla, el timeout y una sentencia con su conexión. */
final class Jdbc {

    // Un nombre de tabla va en el SQL, así que solo se aceptan identificadores simples, con esquema o sin él.
    private static final Pattern TABLE = Pattern.compile("[a-z_][a-z0-9_]*(\\.[a-z_][a-z0-9_]*)?");

    private final ConnectionProvider connections;
    private final int timeoutSeconds;

    Jdbc(ConnectionProvider connections, Duration statementTimeout) {
        this.connections = Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(statementTimeout, "statementTimeout");
        if (statementTimeout.isNegative() || statementTimeout.isZero()) {
            throw new IllegalArgumentException("The statement timeout must be positive");
        }
        // JDBC mide el timeout en segundos enteros: se redondea hacia arriba para no acortarlo.
        this.timeoutSeconds = (int) Math.max(1, (statementTimeout.toMillis() + 999) / 1000);
    }

    static String table(String table) {
        if (table == null || !TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException(
                    "The table name must be a plain SQL identifier, like outbox or app.outbox");
        }
        return table;
    }

    int timeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Corre una sentencia con la conexión de la transacción. Un fallo de la base dice qué operación falló y con
     * qué SQLState, y no encadena la {@link SQLException}: el mensaje del servidor puede citar la fila, y la fila
     * lleva el payload.
     */
    <T> T run(String operation, String sql, Statement<T> statement) {
        try {
            Connection connection = connections.acquire();
            try (PreparedStatement prepared = connection.prepareStatement(sql)) {
                prepared.setQueryTimeout(timeoutSeconds);
                return statement.run(prepared);
            } finally {
                connections.release(connection);
            }
        } catch (SQLException e) {
            throw new OutboxException("The " + operation + " failed in the database with SQLState " + e.getSQLState());
        }
    }

    @FunctionalInterface
    interface Statement<T> {
        T run(PreparedStatement statement) throws SQLException;
    }
}
