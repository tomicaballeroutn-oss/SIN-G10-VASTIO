package ar.edu.utn.vastio.agenda.infraestructura;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import jakarta.annotation.PreDestroy;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ar.edu.utn.vastio.comun.errores.ProblemaException;

/** Dos sesiones dedicadas: una llamada lenta a Google nunca retiene conexiones del pool comercial. */
@Component
public class BloqueosCalendar {
    private final DataSource principal;
    private HikariDataSource sesiones;
    public BloqueosCalendar(DataSource principal) { this.principal=principal; }
    private synchronized HikariDataSource sesiones() throws SQLException {
        if(sesiones==null) {
            var origen=principal.unwrap(HikariDataSource.class);
            var config=new HikariConfig();
            config.setJdbcUrl(origen.getJdbcUrl());config.setUsername(origen.getUsername());config.setPassword(origen.getPassword());
            config.setDataSourceProperties(origen.getDataSourceProperties());
            config.setMaximumPoolSize(2);config.setMinimumIdle(0);config.setConnectionTimeout(1000);
            config.setInitializationFailTimeout(-1);config.setPoolName("calendar-bloqueos");
            sesiones=new HikariDataSource(config);
        }
        return sesiones;
    }
    public Sesion tomar(long id) {
        Connection c=null;
        try {
            c=sesiones().getConnection();
            try(var s=c.prepareStatement("SELECT pg_try_advisory_lock(6006, ?)")) {
                s.setInt(1,Long.hashCode(id));
                try(var r=s.executeQuery()) {
                    r.next();
                    if(!r.getBoolean(1)) throw ProblemaException.conflicto("CALENDAR_EN_PROCESO","Ya hay una operación de Calendar en curso. Volvé a consultar el estado.");
                }
            }
            return new Sesion(c,id);
        } catch(SQLException | RuntimeException e) {
            if(c!=null) {
                // Una respuesta SQL perdida podría haber adquirido el lock: no reciclar esa sesión.
                if(!(e instanceof ProblemaException)) try { c.abort(Runnable::run); } catch(SQLException abortar) { /* Se cierra debajo. */ }
                try { c.close(); } catch(SQLException cierre) { /* Cerrada o abortada. */ }
            }
            if(e instanceof ProblemaException p) throw p;
            throw new ProblemaException(HttpStatus.SERVICE_UNAVAILABLE,"CALENDAR_COORDINACION_NO_DISPONIBLE",
                    "Calendar ocupado","Las sesiones de Calendar están ocupadas o no disponibles. Reintentá más tarde; la agenda sigue disponible.");
        }
    }
    public static final class Sesion implements AutoCloseable {
        private final Connection connection;
        private final long id;
        private Sesion(Connection connection,long id) { this.connection=connection;this.id=id; }
        @Override public void close() {
            try(var s=connection.prepareStatement("SELECT pg_advisory_unlock(6006, ?)")) {
                s.setInt(1,Long.hashCode(id));s.execute();
            } catch(SQLException e) {
                // Nunca devolver una sesión posiblemente bloqueada al pool.
                try { connection.abort(Runnable::run); } catch(SQLException abortar) { /* La conexión se cierra debajo. */ }
            } finally { try { connection.close(); } catch(SQLException e) { /* Ya cerrada o abortada. */ } }
        }
    }
    @PreDestroy public synchronized void cerrar() { if(sesiones!=null) sesiones.close(); }
}
