package pe.edu.nova.java.libs.outbox;

import java.util.Optional;

/** Captura el contexto de la traza en que se agrega un evento, para que el consumidor la continúe (ADR-048, regla 8). */
@FunctionalInterface
public interface TraceContext {

    /**
     * El contexto vigente, en el formato de W3C Trace Context.
     *
     * @return el contexto, o vacío si no hay una traza activa
     */
    Optional<Trace> current();

    /**
     * Un {@code TraceContext} sin traza, para un servicio que no la tiene.
     *
     * @return el contexto vacío
     */
    static TraceContext none() {
        return Optional::empty;
    }

    /**
     * El contexto de una traza.
     *
     * @param traceparent la cabecera {@code traceparent}
     * @param tracestate  la cabecera {@code tracestate}, o {@code null}
     */
    record Trace(String traceparent, String tracestate) {}
}
