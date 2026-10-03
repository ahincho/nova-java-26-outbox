package pe.edu.nova.java.starters.outbox;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import pe.edu.nova.java.libs.outbox.TraceContext;

/**
 * Captura la traza vigente con Micrometer Tracing: el propagador del servicio la escribe en W3C, como lo haría en
 * una cabecera HTTP, y de ahí salen {@code traceparent} y {@code tracestate}.
 */
public final class MicrometerTraceContext implements TraceContext {

    private final Tracer tracer;
    private final Propagator propagator;

    /**
     * Crea el contexto.
     *
     * @param tracer     el tracer del servicio
     * @param propagator el propagador del servicio, el W3C de OpenTelemetry en Nova
     */
    public MicrometerTraceContext(Tracer tracer, Propagator propagator) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
        this.propagator = Objects.requireNonNull(propagator, "propagator");
    }

    @Override
    public Optional<Trace> current() {
        io.micrometer.tracing.TraceContext context =
                tracer.currentTraceContext().context();
        if (context == null) {
            return Optional.empty();
        }
        Map<String, String> headers = new HashMap<>();
        propagator.inject(context, headers, Map::put);
        String traceparent = headers.get("traceparent");
        return traceparent == null ? Optional.empty() : Optional.of(new Trace(traceparent, headers.get("tracestate")));
    }
}
