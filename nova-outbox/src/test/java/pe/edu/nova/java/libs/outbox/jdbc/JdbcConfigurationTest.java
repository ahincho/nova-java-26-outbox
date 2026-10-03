package pe.edu.nova.java.libs.outbox.jdbc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import pe.edu.nova.java.libs.outbox.DefaultOutbox;
import pe.edu.nova.java.libs.outbox.OutboxException;
import pe.edu.nova.java.libs.outbox.TraceContext;

/** Lo que los almacenes validan antes de tocar la base, sin PostgreSQL. */
class JdbcConfigurationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC);

    @Test
    void aTableNameMustBeAPlainIdentifierAndTheTimeoutPositive() {
        ConnectionProvider connections = () -> {
            throw new SQLException("not used");
        };

        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcOutboxStore(connections, "outbox; drop table orders", Duration.ofSeconds(1)));
        assertThrows(
                IllegalArgumentException.class, () -> new JdbcOutboxStore(connections, "app.outbox", Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcInbox(connections, "Inbox", Duration.ofSeconds(1), CLOCK));
    }

    @Test
    void anInboxRegistrationNeedsTheConsumerTheSourceAndTheId() {
        JdbcInbox inbox = new JdbcInbox(() -> {
            throw new SQLException("not used");
        });

        assertThrows(IllegalArgumentException.class, () -> inbox.register(" ", "/plaza/orders", "event-1"));
        assertThrows(IllegalArgumentException.class, () -> inbox.register("ranking", null, "event-1"));
        assertThrows(IllegalArgumentException.class, () -> inbox.register("ranking", "/plaza/orders", ""));
    }

    @Test
    void aConnectionThatCannotBeObtainedIsAnOutboxException() {
        JdbcOutboxStore store = new JdbcOutboxStore(() -> {
            throw new SQLException("down", "08001");
        });

        OutboxException error = assertThrows(
                OutboxException.class,
                () -> new DefaultOutbox(store, () -> true, TraceContext.none(), CLOCK, "/plaza/orders", false)
                        .append("orders", "order-1", "type.v1", "{}"));

        assertTrue(error.getMessage().contains("08001"));
    }
}
