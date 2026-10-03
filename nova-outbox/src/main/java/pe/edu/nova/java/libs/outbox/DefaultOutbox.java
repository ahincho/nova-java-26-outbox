package pe.edu.nova.java.libs.outbox;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * La implementación de Nova del {@link Outbox} (ADR-048): arma el evento con un id nuevo, el momento y la
 * traza vigente, lo escribe por el almacén y, si se pidió, lo borra en la misma transacción.
 *
 * <p>Un {@code traceparent} que no tiene la forma de W3C se descarta, igual que un {@code tracestate} de más de
 * 512 caracteres: una cabecera mal formada no tiene que impedir el evento, y el consumidor empieza otra traza.
 */
public final class DefaultOutbox implements Outbox {

    private static final Pattern TRACEPARENT = Pattern.compile("[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    private static final int TRACESTATE_MAX = 512;

    private final OutboxStore store;
    private final Transactions transactions;
    private final TraceContext traceContext;
    private final Clock clock;
    private final String source;
    private final boolean removeAfterInsert;

    /**
     * Crea el outbox.
     *
     * @param store             dónde se escribe el evento
     * @param transactions      quién dice si hay una transacción activa
     * @param traceContext      de dónde sale la traza vigente
     * @param clock             el reloj del servicio
     * @param source            la identidad del servicio, como {@code /plaza/orders}
     * @param removeAfterInsert {@code true} para borrar el evento en la misma transacción, como pide Debezium
     */
    public DefaultOutbox(
            OutboxStore store,
            Transactions transactions,
            TraceContext traceContext,
            Clock clock,
            String source,
            boolean removeAfterInsert) {
        this.store = Objects.requireNonNull(store, "store");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.traceContext = Objects.requireNonNull(traceContext, "traceContext");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("The outbox needs the source of its events, like /plaza/orders");
        }
        this.source = source;
        this.removeAfterInsert = removeAfterInsert;
    }

    @Override
    public OutboxEvent append(String aggregateType, String aggregateId, String type, String payload) {
        if (!transactions.active()) {
            throw new OutboxException("An event can only be appended inside a transaction: without one, the event "
                    + "and the business change would not be committed together");
        }
        TraceContext.Trace trace = traceContext.current().orElse(null);
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                type,
                source,
                // PostgreSQL guarda microsegundos: el momento se trunca para que el evento sea el mismo que la fila.
                clock.instant().truncatedTo(ChronoUnit.MICROS),
                payload,
                traceparent(trace),
                tracestate(trace));
        store.insert(event);
        if (removeAfterInsert) {
            store.delete(event.id());
        }
        return event;
    }

    private static String traceparent(TraceContext.Trace trace) {
        if (trace == null || trace.traceparent() == null) {
            return null;
        }
        return TRACEPARENT.matcher(trace.traceparent()).matches() ? trace.traceparent() : null;
    }

    private static String tracestate(TraceContext.Trace trace) {
        if (trace == null || traceparent(trace) == null) {
            return null;
        }
        String tracestate = trace.tracestate();
        return tracestate == null || tracestate.isEmpty() || tracestate.length() > TRACESTATE_MAX ? null : tracestate;
    }
}
