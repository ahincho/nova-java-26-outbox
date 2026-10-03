package pe.edu.nova.java.libs.outbox.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import pe.edu.nova.java.libs.outbox.OutboxEvent;
import pe.edu.nova.java.libs.outbox.OutboxStore;

/**
 * El almacén del outbox para PostgreSQL, con JDBC puro (ADR-048).
 *
 * <p>La tabla la crea el servicio con su migración, copiando
 * {@code db/nova/outbox/postgresql/V1__create_outbox.sql}, que viene dentro de este módulo. Cada operación es
 * una sola sentencia, con {@code setQueryTimeout}, en la conexión que entrega el {@link ConnectionProvider}.
 * Un payload que no es JSON lo rechaza la base, por el tipo {@code jsonb}.
 *
 * <p>Es seguro usarlo desde varios hilos.
 */
public final class JdbcOutboxStore implements OutboxStore {

    private final Jdbc jdbc;
    private final String insert;
    private final String delete;

    /**
     * Crea el almacén sobre la tabla {@code outbox}, con un timeout de cinco segundos por sentencia.
     *
     * @param connections de dónde sale la conexión de la transacción
     */
    public JdbcOutboxStore(ConnectionProvider connections) {
        this(connections, "outbox", Duration.ofSeconds(5));
    }

    /**
     * Crea el almacén.
     *
     * @param connections      de dónde sale la conexión de la transacción
     * @param table            la tabla, con su esquema o sin él
     * @param statementTimeout cuánto puede tardar cada sentencia
     */
    public JdbcOutboxStore(ConnectionProvider connections, String table, Duration statementTimeout) {
        this.jdbc = new Jdbc(connections, statementTimeout);
        String name = Jdbc.table(table);
        this.insert = "insert into " + name + " (id, aggregate_type, aggregate_id, type, source, time, payload, "
                + "traceparent, tracestate) values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)";
        this.delete = "delete from " + name + " where id = ?";
    }

    @Override
    public void insert(OutboxEvent event) {
        jdbc.run("insert of an outbox event", insert, statement -> {
            statement.setObject(1, event.id());
            statement.setString(2, event.aggregateType());
            statement.setString(3, event.aggregateId());
            statement.setString(4, event.type());
            statement.setString(5, event.source());
            statement.setObject(6, OffsetDateTime.ofInstant(event.time(), ZoneOffset.UTC));
            statement.setString(7, event.payload());
            setNullable(statement, 8, event.traceparent());
            setNullable(statement, 9, event.tracestate());
            return statement.executeUpdate();
        });
    }

    @Override
    public void delete(UUID id) {
        jdbc.run("delete of an outbox event", delete, statement -> {
            statement.setObject(1, id);
            return statement.executeUpdate();
        });
    }

    private static void setNullable(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}
