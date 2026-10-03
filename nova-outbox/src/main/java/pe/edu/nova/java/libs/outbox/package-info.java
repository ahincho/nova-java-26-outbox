/**
 * La salida de eventos de Nova (ADR-048): el {@link pe.edu.nova.java.libs.outbox.Outbox} que escribe el evento
 * en la transacción del negocio, y el {@link pe.edu.nova.java.libs.outbox.Inbox} que deduplica a quien lo
 * consume. El núcleo es Java puro; los almacenes JDBC están en {@code jdbc} y los de memoria en {@code memory}.
 */
package pe.edu.nova.java.libs.outbox;
