package pe.edu.nova.java.libs.outbox.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import pe.edu.nova.java.libs.outbox.OutboxEvent;
import pe.edu.nova.java.libs.outbox.OutboxStore;

/**
 * Un almacén en memoria, para desarrollo y pruebas. No sigue a la transacción: un rollback no quita el evento,
 * así que nunca sirve en producción, y el starter solo lo usa con {@code nova.outbox.store=memory}.
 *
 * <p>Recuerda todo lo que se agregó, incluso lo que se borró después, para que una prueba vea qué eventos
 * habría publicado Debezium. Es seguro usarlo desde varios hilos.
 */
public final class InMemoryOutboxStore implements OutboxStore {

    private final List<OutboxEvent> appended = new ArrayList<>();
    private final List<OutboxEvent> rows = new ArrayList<>();

    /** Crea un almacén vacío. */
    public InMemoryOutboxStore() {}

    @Override
    public synchronized void insert(OutboxEvent event) {
        appended.add(event);
        rows.add(event);
    }

    @Override
    public synchronized void delete(UUID id) {
        rows.removeIf(event -> event.id().equals(id));
    }

    /**
     * Los eventos agregados, en orden: lo que Debezium habría leído del log.
     *
     * @return una copia
     */
    public synchronized List<OutboxEvent> appended() {
        return List.copyOf(appended);
    }

    /**
     * Las filas que siguen en la tabla.
     *
     * @return una copia
     */
    public synchronized List<OutboxEvent> rows() {
        return List.copyOf(rows);
    }

    /** Olvida todo, entre una prueba y otra. */
    public synchronized void clear() {
        appended.clear();
        rows.clear();
    }
}
