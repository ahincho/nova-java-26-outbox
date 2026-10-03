package pe.edu.nova.java.libs.outbox;

/**
 * Lo que deduplica a un consumidor: el evento llega al menos una vez, y el inbox recuerda cuáles ya procesó
 * (ADR-048, regla 10).
 *
 * <p>Se registra en la transacción que aplica el efecto: si el efecto se revierte, el registro también, y el
 * evento se vuelve a procesar. Un duplicado concurrente espera al primero y recibe {@code false} cuando este
 * confirma.
 */
public interface Inbox {

    /**
     * Registra que un consumidor procesó un evento.
     *
     * @param consumer el consumidor, como {@code catalog-best-sellers}
     * @param source   el {@code ce_source} del evento
     * @param id       el {@code ce_id} del evento
     * @return {@code true} si es la primera vez, y el efecto se aplica; {@code false} si ya se procesó
     * @throws OutboxException si el almacén falló
     */
    boolean register(String consumer, String source, String id);
}
