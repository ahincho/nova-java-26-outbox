package pe.edu.nova.java.libs.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import pe.edu.nova.java.libs.outbox.memory.InMemoryInbox;
import pe.edu.nova.java.libs.outbox.memory.InMemoryOutboxStore;

class DefaultOutboxTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T15:00:00.123456789Z"), ZoneOffset.UTC);

    private final InMemoryOutboxStore store = new InMemoryOutboxStore();
    private final AtomicBoolean inTransaction = new AtomicBoolean(true);
    private final AtomicReference<TraceContext.Trace> trace = new AtomicReference<>();

    private DefaultOutbox outbox(boolean removeAfterInsert) {
        return new DefaultOutbox(
                store,
                inTransaction::get,
                () -> Optional.ofNullable(trace.get()),
                CLOCK,
                "/plaza/orders",
                removeAfterInsert);
    }

    @Test
    void anEventIsWrittenWithTheSourceTheMomentAndTheTraceOfTheTransaction() {
        trace.set(new TraceContext.Trace(TRACEPARENT, "nova=1"));

        OutboxEvent event = outbox(false).append("orders", "order-1", "pe.edu.nova.plaza.order.created.v1", "{}");

        assertEquals(List.of(event), store.rows());
        assertEquals("/plaza/orders", event.source());
        assertEquals(Instant.parse("2026-10-02T15:00:00.123456Z"), event.time());
        assertEquals(TRACEPARENT, event.traceparent());
        assertEquals("nova=1", event.tracestate());
    }

    @Test
    void withDebeziumTheRowIsDeletedInTheSameTransactionAndTheLogKeepsTheInsert() {
        OutboxEvent event = outbox(true).append("orders", "order-1", "pe.edu.nova.plaza.order.created.v1", "{}");

        assertEquals(List.of(), store.rows());
        assertEquals(List.of(event), store.appended());
    }

    @Test
    void withoutATransactionAppendingFailsAndWritesNothing() {
        inTransaction.set(false);

        OutboxException error =
                assertThrows(OutboxException.class, () -> outbox(true).append("orders", "order-1", "type.v1", "{}"));

        assertTrue(error.getMessage().contains("inside a transaction"));
        assertEquals(List.of(), store.appended());
    }

    @Test
    void eachEventHasItsOwnId() {
        DefaultOutbox outbox = outbox(true);

        assertNotEquals(
                outbox.append("orders", "order-1", "type.v1", "{}").id(),
                outbox.append("orders", "order-1", "type.v1", "{}").id());
    }

    @Test
    void aMalformedTraceIsDroppedInsteadOfBlockingTheEvent() {
        trace.set(new TraceContext.Trace("not-a-traceparent", "nova=1"));
        OutboxEvent malformed = outbox(true).append("orders", "order-1", "type.v1", "{}");
        trace.set(new TraceContext.Trace(TRACEPARENT, "x".repeat(513)));
        OutboxEvent longState = outbox(true).append("orders", "order-1", "type.v1", "{}");
        trace.set(new TraceContext.Trace(TRACEPARENT, ""));
        OutboxEvent emptyState = outbox(true).append("orders", "order-1", "type.v1", "{}");

        assertNull(malformed.traceparent());
        assertNull(malformed.tracestate());
        assertEquals(TRACEPARENT, longState.traceparent());
        assertNull(longState.tracestate());
        assertNull(emptyState.tracestate());
    }

    @Test
    void withoutATraceTheEventHasNone() {
        OutboxEvent event = new DefaultOutbox(store, () -> true, TraceContext.none(), CLOCK, "/plaza/orders", true)
                .append("orders", "order-1", "type.v1", "{}");

        assertNull(event.traceparent());
        assertNull(event.tracestate());
    }

    @Test
    void anEventNeedsItsFieldsAndTheOutboxItsSource() {
        DefaultOutbox outbox = outbox(true);

        assertThrows(IllegalArgumentException.class, () -> outbox.append(" ", "order-1", "type.v1", "{}"));
        assertThrows(IllegalArgumentException.class, () -> outbox.append("orders", null, "type.v1", "{}"));
        assertThrows(IllegalArgumentException.class, () -> outbox.append("orders", "order-1", "", "{}"));
        assertThrows(IllegalArgumentException.class, () -> outbox.append("orders", "order-1", "type.v1", null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new DefaultOutbox(store, () -> true, TraceContext.none(), CLOCK, " ", true));
    }

    @Test
    void theTextOfAnEventNeverShowsItsPayloadNorItsTrace() {
        trace.set(new TraceContext.Trace(TRACEPARENT, null));
        OutboxEvent event = outbox(true).append("orders", "order-1", "type.v1", "{\"card\":\"4111\"}");

        assertFalse(event.toString().contains("4111"));
        assertFalse(event.toString().contains(TRACEPARENT));
        assertTrue(event.toString().contains(event.id().toString()));
    }

    @Test
    void theInMemoryInboxRemembersEachEventOfEachConsumer() {
        Inbox inbox = new InMemoryInbox();

        assertTrue(inbox.register("ranking", "/plaza/orders", "event-1"));
        assertFalse(inbox.register("ranking", "/plaza/orders", "event-1"));
        assertTrue(inbox.register("audit", "/plaza/orders", "event-1"));
        assertTrue(inbox.register("ranking", "/plaza/payments", "event-1"));
    }

    @Test
    void theInMemoryStoreForgetsEverythingOnClear() {
        outbox(false).append("orders", "order-1", "type.v1", "{}");

        store.clear();

        assertEquals(List.of(), store.appended());
        assertEquals(List.of(), store.rows());
    }
}
