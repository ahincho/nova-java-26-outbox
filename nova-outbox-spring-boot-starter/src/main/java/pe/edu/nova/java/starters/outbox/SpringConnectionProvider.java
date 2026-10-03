package pe.edu.nova.java.starters.outbox;

import java.sql.Connection;
import java.util.Objects;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import pe.edu.nova.java.libs.outbox.jdbc.ConnectionProvider;

/**
 * El {@link ConnectionProvider} que suma los almacenes a la transacción de Spring: entrega la conexión de la
 * transacción del hilo, también la de una transacción de JPA, que la expone con {@code DataSourceUtils}.
 */
public final class SpringConnectionProvider implements ConnectionProvider {

    private final DataSource dataSource;

    /**
     * Crea el proveedor.
     *
     * @param dataSource el {@code DataSource} del servicio, el mismo que usa su transacción
     */
    public SpringConnectionProvider(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Connection acquire() {
        return DataSourceUtils.getConnection(dataSource);
    }

    @Override
    public void release(Connection connection) {
        // No cierra la conexión de la transacción: la devuelve Spring al terminarla.
        DataSourceUtils.releaseConnection(connection, dataSource);
    }
}
