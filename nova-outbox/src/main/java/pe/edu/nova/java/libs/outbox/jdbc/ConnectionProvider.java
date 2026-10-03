package pe.edu.nova.java.libs.outbox.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * De dónde sacan los almacenes JDBC la conexión de cada operación: es lo que los suma a la transacción del
 * negocio en cualquier stack.
 *
 * <p>El almacén pide la conexión al empezar cada operación y la devuelve al terminarla. Nunca hace commit ni
 * rollback, ni cambia el auto-commit: eso es de quien la entrega.
 *
 * <ul>
 *   <li><strong>Spring</strong> la toma con {@code DataSourceUtils.getConnection(dataSource)} y la devuelve con
 *       {@code DataSourceUtils.releaseConnection}; el starter ya lo hace.
 *   <li><strong>Quarkus</strong> la toma de su {@code DataSource} con {@code dataSource::getConnection}: dentro de
 *       un método {@code @Transactional}, Agroal la enlista en la transacción JTA, y cerrarla la devuelve al pool
 *       cuando la transacción termina.
 * </ul>
 */
@FunctionalInterface
public interface ConnectionProvider {

    /**
     * Entrega la conexión de la transacción vigente.
     *
     * @return la conexión
     * @throws SQLException si no se pudo obtener
     */
    Connection acquire() throws SQLException;

    /**
     * Devuelve la conexión cuando la operación terminó. Por defecto la cierra, que es lo que corresponde a una
     * conexión de un pool.
     *
     * @param connection la conexión que entregó {@link #acquire()}
     * @throws SQLException si no se pudo devolver
     */
    default void release(Connection connection) throws SQLException {
        connection.close();
    }
}
