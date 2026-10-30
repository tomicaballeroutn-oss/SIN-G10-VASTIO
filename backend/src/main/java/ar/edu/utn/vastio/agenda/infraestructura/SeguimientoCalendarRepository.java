package ar.edu.utn.vastio.agenda.infraestructura;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Cola coalescente: una revisión nueva nunca se pierde al confirmar una revisión anterior. */
@Repository
public class SeguimientoCalendarRepository {
    private final JdbcTemplate jdbc;
    public SeguimientoCalendarRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Trabajo(long revision, long procesada, String error) { }

    public Trabajo consultar(long id) {
        return jdbc.query("SELECT revision,procesada,ultimo_error FROM calendar_seguimiento WHERE evento_id=?",
                (r,n) -> new Trabajo(r.getLong(1),r.getLong(2),r.getString(3)),id).stream().findFirst().orElse(null);
    }
    public long bloquear(long id) {
        return jdbc.queryForObject("SELECT revision FROM calendar_seguimiento WHERE evento_id=? FOR UPDATE",Long.class,id);
    }
    public void confirmar(long id,long revision) {
        jdbc.update("UPDATE calendar_seguimiento SET procesada=?,intentos=0,ultimo_error=NULL WHERE evento_id=? AND revision=?",revision,id,revision);
    }
    public void fallar(long id) {
        jdbc.update("""
            UPDATE calendar_seguimiento SET intentos=intentos+1,ultimo_error='SEGUIMIENTO_CALENDAR_FALLIDO',
            proximo_intento=now()+make_interval(secs => least(300, 5*(intentos+1))) WHERE evento_id=? AND revision>procesada
            """,id);
    }
    public void encolarExistentes() {
        jdbc.update("""
            INSERT INTO calendar_seguimiento(evento_id)
            SELECT e.evento_id FROM evento e LEFT JOIN publicacion_calendar p USING(evento_id)
            WHERE e.estado IN ('SENADO','CONTRATADO','CONFIRMADO') OR p.evento_id IS NOT NULL
            ON CONFLICT(evento_id) DO UPDATE SET revision=calendar_seguimiento.revision+1,proximo_intento=now()
            """);
    }
    public List<Long> pendientes() {
        return jdbc.queryForList("""
            SELECT q.evento_id FROM calendar_seguimiento q JOIN evento e USING(evento_id)
            LEFT JOIN publicacion_calendar p USING(evento_id)
            WHERE (q.revision>q.procesada AND q.proximo_intento<=now()) OR p.estado='EN_PROCESO'
            ORDER BY q.evento_id LIMIT 50
            """,Long.class);
    }
}
