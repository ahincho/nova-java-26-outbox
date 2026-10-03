package pe.edu.nova.java.libs.outbox;

/** Dice si hay una transacción activa en el hilo: sin ella, agregar un evento falla (ADR-048, regla 1). */
@FunctionalInterface
public interface Transactions {

    /**
     * Dice si hay una transacción activa.
     *
     * @return {@code true} si el evento se confirmaría con el cambio del negocio
     */
    boolean active();
}
