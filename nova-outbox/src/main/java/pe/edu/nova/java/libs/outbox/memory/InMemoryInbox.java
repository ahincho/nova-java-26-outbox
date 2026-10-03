package pe.edu.nova.java.libs.outbox.memory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import pe.edu.nova.java.libs.outbox.Inbox;

/**
 * Un inbox en memoria, para desarrollo y pruebas. Como el almacén en memoria, no sigue a la transacción: un
 * evento registrado queda registrado aunque su efecto se revierta. Es seguro usarlo desde varios hilos.
 */
public final class InMemoryInbox implements Inbox {

    private final Set<List<String>> processed = new HashSet<>();

    /** Crea un inbox vacío. */
    public InMemoryInbox() {}

    @Override
    public synchronized boolean register(String consumer, String source, String id) {
        return processed.add(List.of(consumer, source, id));
    }
}
