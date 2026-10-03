package pe.edu.nova.java.libs.outbox;

import java.io.Serial;

/**
 * Un evento que no se pudo agregar o registrar. Su mensaje nunca lleva el payload: dice qué operación falló y,
 * si vino de la base, con qué SQLState.
 */
public class OutboxException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Crea la excepción.
     *
     * @param message qué falló, sin datos del evento
     */
    public OutboxException(String message) {
        super(message);
    }
}
