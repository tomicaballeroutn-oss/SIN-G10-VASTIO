package ar.edu.utn.vastio.agenda.infraestructura;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ar.edu.utn.vastio.agenda.dominio.PublicacionCalendar;

/** SQL explícito para ON CONFLICT y coordinación PostgreSQL, sin estado en memoria. */
@Repository
public class PublicacionCalendarRepository {
    private final JdbcTemplate jdbc;
    public JdbcTemplate jdbc() { return jdbc; }
    public PublicacionCalendarRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void asegurar(long id) {
        jdbc.update("INSERT INTO publicacion_calendar(evento_id) VALUES (?) ON CONFLICT DO NOTHING", id);
    }
    public PublicacionCalendar de(long id) {
        return jdbc.query("SELECT * FROM publicacion_calendar WHERE evento_id = ? FOR UPDATE", (r, n) ->
            new PublicacionCalendar(r.getLong("evento_id"), r.getString("calendar_id"), r.getString("instalacion"),
                r.getString("google_id"), r.getString("estado"), r.getString("pendiente"), r.getString("huella"),
                r.getString("huella_intento"), r.getBoolean("retiro_solicitado"), r.getString("etag"), r.getObject("intento", java.time.OffsetDateTime.class),
                r.getObject("exito", java.time.OffsetDateTime.class), r.getString("error"),
                r.getObject("usuario_id", Long.class), r.getString("aviso_clave"), r.getInt("version")), id)
                .stream().findFirst().orElse(null);
    }
    public List<Long> ids() {
        return jdbc.queryForList("""
            SELECT e.evento_id FROM evento e LEFT JOIN publicacion_calendar p USING(evento_id)
            WHERE p.evento_id IS NOT NULL OR e.estado IN ('SENADO','CONTRATADO','CONFIRMADO')
            ORDER BY e.evento_id
            """, Long.class);
    }
    public void auditar(long id, String accion, Long usuario, String codigo) {
        jdbc.update("INSERT INTO operacion_calendar(evento_id, accion, usuario_id, codigo) VALUES (?,?,?,?)",
                id, accion, usuario, codigo);
    }
}
