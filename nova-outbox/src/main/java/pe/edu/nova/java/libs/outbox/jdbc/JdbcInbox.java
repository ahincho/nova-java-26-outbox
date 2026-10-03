package pe.edu.nova.java.libs.outbox.jdbc;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import pe.edu.nova.java.libs.outbox.Inbox;

/**
 * El inbox para PostgreSQL, con JDBC puro (ADR-048).
 *
 * <p>La tabla la crea el servicio con su migración, copiando
 * {@code db/nova/outbox/postgresql/V1__create_inbox_message.sql}. Registrar es una sola inserción con
 * {@code on conflict do nothing}: la clave primaria decide quién procesa el evento. Un duplicado concurrente
 * espera a que la primera transacción termine; si confirma, recibe {@code false}, y si se revierte, inserta él.
 *
 * <p>Es seguro usarlo desde varios hilos.
 */
public final class JdbcInbox implements Inbox {

    private final Jdbc jdbc;
    private final Clock clock;
    private final String insert;

    /**
     * Crea el inbox sobre la tabla {@code inbox_message}, con un timeout de cinco segundos por sentencia.
     *
     * @param connections de dónde sale la conexión de la transacción
     */
    public JdbcInbox(ConnectionProvider connections) {
        this(connections, "inbox_message", Duration.ofSeconds(5), Clock.systemUTC());
    }

    /**
     * Crea el inbox.
     *
     * @param connections      de dónde sale la conexión de la transacción
     * @param table            la tabla, con su esquema o sin él
     * @param statementTimeout cuánto puede tardar cada sentencia
     * @param clock            el reloj con que se anota cuándo se procesó
     */
    public JdbcInbox(ConnectionProvider connections, String table, Duration statementTimeout, Clock clock) {
        this.jdbc = new Jdbc(connections, statementTimeout);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.insert = "insert into " + Jdbc.table(table)
                + " (consumer, source, id, processed_at) values (?, ?, ?, ?) on conflict do nothing";
    }

    @Override
    public boolean register(String consumer, String source, String id) {
        requireText(consumer, "consumer");
        requireText(source, "source");
        requireText(id, "id");
        return jdbc.run("registration of a processed event", insert, statement -> {
            statement.setString(1, consumer);
            statement.setString(2, source);
            statement.setString(3, id);
            statement.setObject(4, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            return statement.executeUpdate() == 1;
        });
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The inbox needs the " + name + " of the event");
        }
    }
}
