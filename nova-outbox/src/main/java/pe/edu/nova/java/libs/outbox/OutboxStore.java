package pe.edu.nova.java.libs.outbox;

import java.util.UUID;

/**
 * Dónde se escribe el evento. Cada operación corre en la transacción vigente, y nunca hace commit ni rollback:
 * eso es del servicio.
 */
public interface OutboxStore {

    /**
     * Escribe el evento.
     *
     * @param event el evento
     * @throws OutboxException si el almacén falló
     */
    void insert(OutboxEvent event);

    /**
     * Borra el evento. Con Debezium se borra en la misma transacción en que se insertó: el log conserva la
     * inserción, que es lo que se publica, y la tabla no crece.
     *
     * @param id el id del evento
     * @throws OutboxException si el almacén falló
     */
    void delete(UUID id);
}
