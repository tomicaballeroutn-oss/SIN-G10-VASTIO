package ar.edu.utn.vastio.agenda.aplicacion;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ar.edu.utn.vastio.agenda.aplicacion.ClienteCalendar.Fallo;
import ar.edu.utn.vastio.agenda.aplicacion.ClienteCalendar.Remoto;
import ar.edu.utn.vastio.agenda.aplicacion.ContenidoCalendar.Contenido;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.PublicacionCalendar;
import ar.edu.utn.vastio.agenda.infraestructura.CalendarProperties;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.PublicacionCalendarRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;

/** Transacciones cortas antes/después de Google. El lock de sesión libera al morir el proceso. */
@Service
public class ExportacionCalendarService {
    private static final int LOCK_NAMESPACE = 6006;
    private final PublicacionCalendarRepository publicaciones;
    private final EventoRepository eventos;
    private final SeguimientoCalendar seguimiento;
    private final ContenidoCalendar contenidos;
    private final ClienteCalendar cliente;
    private final CalendarProperties config;
    private final TransactionTemplate tx;
    private final EntityManager em;
    private final DataSource db;

    public ExportacionCalendarService(PublicacionCalendarRepository publicaciones, EventoRepository eventos,
            SeguimientoCalendar seguimiento, ContenidoCalendar contenidos, ClienteCalendar cliente,
            CalendarProperties config, TransactionTemplate tx, EntityManager em, DataSource db) {
        this.publicaciones = publicaciones; this.eventos = eventos; this.seguimiento = seguimiento;
        this.contenidos = contenidos; this.cliente = cliente; this.config = config; this.tx = tx; this.em = em; this.db = db;
    }

    public record Estado(long eventoId, String codigo, String salon, LocalDate fecha, String turno,
            String estado, String pendiente, String error, OffsetDateTime ultimoExito, boolean configurado,
            boolean puedeExportar, boolean puedeRetirar) { }
    private record Intento(long id, String calendario, String googleId, String etag, String huellaAnterior, String huellaIntento,
            Contenido contenido, boolean retirar, boolean recuperar, boolean confirmado, boolean crearPermitido) { }

    public Estado estado(long id, UsuarioActual quien) {
        autorizar(quien);
        return tx.execute(t -> ver(id));
    }

    public List<Estado> pendientes(UsuarioActual quien) {
        autorizar(quien);
        // Cada evento en su transacción, igual que RealizacionService. No llama a Google.
        return publicaciones.ids().stream().map(id -> tx.execute(t -> ver(id)))
                .filter(e -> !e.pendiente().equals("NINGUNA") || e.error() != null || e.estado().equals("INCIERTO")
                        || e.estado().equals("EN_PROCESO")).toList();
    }

    private Estado ver(long id) {
        Evento e = evento(id);
        var p = seguimiento.revisar(id);
        if (p != null && p.estado().equals("EN_PROCESO") && libreEnTransaccion(id)) {
            publicaciones.jdbc().update("UPDATE publicacion_calendar SET estado='INCIERTO', error='OPERACION_ABANDONADA' WHERE evento_id=?", id);
            publicaciones.auditar(id, "INCIERTO", null, "OPERACION_ABANDONADA");
            p = seguimiento.revisar(id);
        }
        var c = contenidos.de(e);
        boolean nuevo = SeguimientoCalendar.NUEVOS.contains(e.getEstado());
        return new Estado(id, e.getCodigo(), c.salon(), e.getUnidad().getFecha(),
                // La proyección evita enviar el nombre libre del evento o datos del cliente.
                c.titulo().substring(c.titulo().lastIndexOf(" · ") + 3),
                p == null ? "NO_EXPORTABLE" : p.estado(), p == null ? "NINGUNA" : p.pendiente(),
                p == null ? null : p.error(), p == null ? null : p.exito(), cliente.configurado(),
                (nuevo || p != null && p.googleId() != null) && (p == null || !p.estado().equals("RETIRADO") && !p.pendiente().equals("RETIRAR")),
                p != null && p.googleId() != null && !p.estado().equals("RETIRADO"));
    }

    private boolean libreEnTransaccion(long id) {
        return Boolean.TRUE.equals(publicaciones.jdbc().queryForObject(
                "SELECT pg_try_advisory_xact_lock(?, ?)", Boolean.class, LOCK_NAMESPACE, lockId(id)));
    }

    /** Todas las mutaciones remotas pasan por este lock, incluso retirada versus actualización. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    public Estado operar(long id, boolean retirar, UsuarioActual quien) {
        autorizar(quien);
        if (!cliente.configurado()) throw problema("CALENDAR_NO_CONFIGURADO");
        try (Connection lock = db.getConnection()) {
            if (!tomar(lock, id)) throw ProblemaException.conflicto("CALENDAR_EN_PROCESO", "Ya hay una operación de Calendar en curso. Volvé a consultar el estado.");
            try {
                Intento intento = tx.execute(t -> preparar(id, retirar, quien));
                if (intento != null) {
                    try {
                        Remoto resultado = remoto(intento);
                        if (!intento.retirar() && (resultado == null || resultado.eliminado() || resultado.etag() == null
                                || !intento.contenido().huella().equals(resultado.huella()))) {
                            throw new Fallo("RESPUESTA_GOOGLE_INCONSISTENTE", true);
                        }
                        tx.executeWithoutResult(t -> confirmar(intento, resultado, quien));
                    } catch (Fallo fallo) {
                        tx.executeWithoutResult(t -> fallar(id, fallo, quien));
                        throw problema(fallo.codigo());
                    } catch (RuntimeException falloLocal) {
                        // También si la respuesta llegó pero no pudo persistirse: el próximo intento reconcilia.
                        try { tx.executeWithoutResult(t -> fallar(id, new Fallo("RESULTADO_NO_PERSISTIDO", true), quien)); }
                        catch (RuntimeException ignorada) { /* EN_PROCESO durable permite recuperar al volver la base. */ }
                        throw problema("RESULTADO_NO_PERSISTIDO");
                    }
                }
                return tx.execute(t -> ver(id));
            } finally { soltar(lock, id); }
        } catch (SQLException e) { throw problema("CALENDAR_COORDINACION_NO_DISPONIBLE"); }
    }

    private Intento preparar(long id, boolean retirar, UsuarioActual quien) {
        Evento e = evento(id);
        em.refresh(e, LockModeType.PESSIMISTIC_WRITE); // Orden: evento, luego publicación.
        var p = seguimiento.revisar(id);
        if (!retirar && (p == null || p.googleId() == null && !SeguimientoCalendar.NUEVOS.contains(e.getEstado())
                || e.getEstado() == ar.edu.utn.vastio.agenda.dominio.EstadoEvento.PRE_RESERVA
                || ar.edu.utn.vastio.agenda.dominio.EstadoEvento.INACTIVOS.contains(e.getEstado()))) {
            throw ProblemaException.reglaDeNegocio("CALENDAR_NO_EXPORTABLE", "El estado del evento no permite una publicación nueva.");
        }
        if (retirar && (p == null || p.googleId() == null || p.estado().equals("RETIRADO"))) return null;
        if (!retirar && p.retiroSolicitado()) {
            throw ProblemaException.reglaDeNegocio("CALENDAR_RETIRADA_PENDIENTE", "Hay una retirada pendiente. Verificá o reintentá esa operación antes de publicar.");
        }
        if (p.calendarId() != null && (!p.calendarId().equals(config.calendarId()) || !p.instalacion().equals(config.instalacion()))) {
            throw ProblemaException.conflicto("CALENDAR_DESTINO_CAMBIADO", "La publicación pertenece a otro destino o instalación. Revisá la configuración antes de continuar.");
        }
        Contenido contenido = contenidos.de(e);
        if (!retirar && p.estado().equals("EXPORTADO") && p.error() == null && contenido.huella().equals(p.huella())) return null;
        boolean recuperar = p.estado().equals("EN_PROCESO") || p.estado().equals("INCIERTO");
        boolean confirmado = p.huella() != null;
        if (p.estado().equals("RETIRADO") && !retirar) {
            // El ID eliminado puede conservar una lápida remota: no cambiar silenciosamente su identidad.
            throw ProblemaException.reglaDeNegocio("CALENDAR_RETIRADO", "La publicación fue retirada. Su recreación requiere revisión del calendario de prueba/operación.");
        }
        String googleId = p.googleId() == null ? ContenidoCalendar.identidad(config.instalacion(), id) : p.googleId();
        publicaciones.jdbc().update("""
                UPDATE publicacion_calendar SET calendar_id=?, instalacion=?, google_id=?, estado='EN_PROCESO',
                pendiente=?, intento=now(), huella_intento=?, retiro_solicitado=?, usuario_id=?, version=version+1 WHERE evento_id=?
                """, config.calendarId(), config.instalacion(), googleId, retirar ? "RETIRAR" : confirmado ? "ACTUALIZAR" : "EXPORTAR", contenido.huella(), retirar, quien.id(), id);
        publicaciones.auditar(id, recuperar || p.error() != null ? "REINTENTO" : "SOLICITUD", quien.id(), null);
        return new Intento(id, config.calendarId(), googleId, p.etag(), p.huella(), p.huellaIntento(), contenido, retirar, recuperar, confirmado,
                SeguimientoCalendar.NUEVOS.contains(e.getEstado()));
    }

    private Remoto remoto(Intento i) {
        // Verificar acceso al calendario explícito antes de interpretar ausencia de un evento.
        cliente.comprobarDestino(i.calendario());
        Remoto actual = cliente.consultar(i.calendario(), i.googleId());
        if (i.retirar()) {
            if (actual == null || actual.eliminado()) return null;
            if (i.etag() != null && !Objects.equals(i.etag(), actual.etag())
                    && !(i.recuperar() && Objects.equals(i.huellaIntento(), actual.huella()))) throw new Fallo("CONFLICTO_REMOTO", false);
            cliente.retirar(i.calendario(), i.googleId(), actual.etag());
            return null;
        }
        if (actual == null) {
            if (i.confirmado() || !i.crearPermitido()) throw new Fallo("EVENTO_REMOTO_AUSENTE", false);
            try { return cliente.crear(i.calendario(), i.googleId(), i.contenido()); }
            catch (Fallo f) {
                if (!f.codigo().equals("ID_EXISTENTE")) throw f;
                actual = cliente.consultar(i.calendario(), i.googleId());
                if (actual == null) throw new Fallo("RESULTADO_INCIERTO", true);
            }
        }
        if (actual.eliminado()) throw new Fallo("EVENTO_REMOTO_ELIMINADO", false);
        // Confirmar contenido propio idéntico tampoco necesita otra escritura después de un fallo de lectura.
        if (i.contenido().huella().equals(actual.huella())) return actual;
        if (!i.recuperar() && (i.etag() == null || !Objects.equals(i.etag(), actual.etag()))) throw new Fallo("CONFLICTO_REMOTO", false);
        // Una recuperación solo puede sobrescribir si coincide con la última versión confirmada.
        if (i.recuperar() && !Objects.equals(i.huellaAnterior(), actual.huella()) && !Objects.equals(i.huellaIntento(), actual.huella())) throw new Fallo("CONFLICTO_REMOTO", false);
        return cliente.actualizar(i.calendario(), i.googleId(), i.contenido(), actual.etag());
    }

    private void confirmar(Intento i, Remoto remoto, UsuarioActual quien) {
        Evento e = evento(i.id());
        em.refresh(e, LockModeType.PESSIMISTIC_WRITE);
        publicaciones.de(i.id());
        publicaciones.jdbc().update("""
            UPDATE publicacion_calendar SET estado=?, pendiente='NINGUNA', huella=?, etag=?, error=NULL,
            exito=now(), version=version+1 WHERE evento_id=?
            """, i.retirar() ? "RETIRADO" : "EXPORTADO", i.retirar() ? null : i.contenido().huella(),
                remoto == null ? null : remoto.etag(), i.id());
        publicaciones.auditar(i.id(), i.retirar() ? "RETIRADA_CONFIRMADA" : i.confirmado() ? "ACTUALIZACION_CONFIRMADA" : "CREACION_CONFIRMADA", quien.id(), null);
        seguimiento.revisar(i.id()); // Recalcula si se reprogramó/canceló mientras Google respondía.
    }

    private void fallar(long id, Fallo fallo, UsuarioActual quien) {
        Evento e = evento(id);
        em.refresh(e, LockModeType.PESSIMISTIC_WRITE);
        publicaciones.de(id);
        publicaciones.jdbc().update("UPDATE publicacion_calendar SET estado=?, error=?, version=version+1 WHERE evento_id=?",
                fallo.incierto() ? "INCIERTO" : "ERROR", fallo.codigo(), id);
        publicaciones.auditar(id, fallo.incierto() ? "INCIERTO" : "ERROR", quien.id(), fallo.codigo());
        seguimiento.revisar(id);
    }

    private Evento evento(long id) {
        return eventos.conDetalle(id).orElseThrow(() -> ProblemaException.noEncontrado("EVENTO_INEXISTENTE", "No encontramos ese evento."));
    }
    private static void autorizar(UsuarioActual quien) { if (!quien.accesoTotal()) throw ConsultaEventoService.sinPermiso(); }
    private static int lockId(long id) { return Long.hashCode(id); } // Colisiones solo serializan eventos adicionales.
    private static boolean tomar(Connection c, long id) throws SQLException {
        try (var s = c.prepareStatement("SELECT pg_try_advisory_lock(?, ?)")) {
            s.setInt(1, LOCK_NAMESPACE); s.setInt(2, lockId(id));
            try (var r = s.executeQuery()) { r.next(); return r.getBoolean(1); }
        }
    }
    private static void soltar(Connection c, long id) throws SQLException {
        try (var s = c.prepareStatement("SELECT pg_advisory_unlock(?, ?)")) {
            s.setInt(1, LOCK_NAMESPACE); s.setInt(2, lockId(id)); s.execute();
        }
    }
    private static ProblemaException problema(String codigo) {
        return new ProblemaException(HttpStatus.SERVICE_UNAVAILABLE, codigo, "Google Calendar requiere atención",
                "No confirmamos la operación de Google Calendar (" + codigo + "). Consultá el pendiente y reintentá cuando corresponda. Los datos de Vastio están guardados.");
    }
}
