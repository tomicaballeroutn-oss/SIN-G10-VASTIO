package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
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
    private final PublicacionCalendarRepository publicaciones;
    private final EventoRepository eventos;
    private final SeguimientoCalendar seguimiento;
    private final ContenidoCalendar contenidos;
    private final ClienteCalendar cliente;
    private final CalendarProperties config;
    private final TransactionTemplate tx;
    private final EntityManager em;
    private final ar.edu.utn.vastio.agenda.infraestructura.BloqueosCalendar bloqueos;
    private final ar.edu.utn.vastio.agenda.infraestructura.SeguimientoCalendarRepository cola;

    public ExportacionCalendarService(PublicacionCalendarRepository publicaciones, EventoRepository eventos,
            SeguimientoCalendar seguimiento, ContenidoCalendar contenidos, ClienteCalendar cliente,
            CalendarProperties config, TransactionTemplate tx, EntityManager em, ar.edu.utn.vastio.agenda.infraestructura.BloqueosCalendar bloqueos, ar.edu.utn.vastio.agenda.infraestructura.SeguimientoCalendarRepository cola) {
        this.publicaciones = publicaciones; this.eventos = eventos; this.seguimiento = seguimiento;
        this.contenidos = contenidos; this.cliente = cliente; this.config = config; this.tx = tx; this.em = em; this.bloqueos=bloqueos;this.cola=cola;
    }

    public record Estado(long eventoId, String codigo, String salon, LocalDate fecha, String turno,
            String estado, String pendiente, String error, OffsetDateTime ultimoExito, boolean configurado,
            boolean puedeExportar, boolean puedeRetirar, boolean seguimientoPendiente, String errorSeguimiento) { }
    private record Intento(long id, String calendario, String googleId, String etag, String huellaAnterior, String huellaIntento,
            Contenido contenido, boolean retirar, boolean recuperar, boolean confirmado, boolean crearPermitido) { }
    public record RevisionRemota(String etag, boolean coincideConVastio) { }

    /** Lectura remota solicitada explícitamente. No importa campos de Google a Vastio. */
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NEVER)
    public RevisionRemota verificarConflicto(long id,UsuarioActual quien) {
        autorizar(quien);
        if(!cliente.configurado()) throw problema("CALENDAR_NO_CONFIGURADO");
        try(var lock=bloqueos.tomar(id)) {
            var p=tx.execute(t->publicacionEnConflicto(id));
            cliente.comprobarDestino(p.calendarId());
            var remoto=cliente.consultar(p.calendarId(),p.googleId());
            if(remoto==null || remoto.eliminado()) throw problema("EVENTO_REMOTO_AUSENTE");
            String huella=tx.execute(t->contenidos.de(evento(id)).huella());
            return new RevisionRemota(remoto.etag(),huella.equals(remoto.huella()));
        } catch(Fallo f) { throw problema(f.codigo()); }
    }

    /** Aceptar un ETag permite una futura acción explícita; aquí NO se escribe en Google. */
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.NEVER)
    public Estado aceptarRevision(long id,String etagEsperado,UsuarioActual quien) {
        autorizar(quien);
        if(!cliente.configurado()) throw problema("CALENDAR_NO_CONFIGURADO");
        try(var lock=bloqueos.tomar(id)) {
            var p=tx.execute(t->publicacionEnConflicto(id));
            cliente.comprobarDestino(p.calendarId());
            var remoto=cliente.consultar(p.calendarId(),p.googleId());
            if(remoto==null || remoto.eliminado()) throw problema("EVENTO_REMOTO_AUSENTE");
            if(etagEsperado==null || !etagEsperado.equals(remoto.etag())) throw problema("CONFLICTO_REMOTO");
            tx.executeWithoutResult(t->{
                Evento e=evento(id);em.refresh(e,LockModeType.PESSIMISTIC_WRITE);
                publicacionEnConflicto(id);
                publicaciones.jdbc().update("UPDATE publicacion_calendar SET etag=?,error=NULL,estado='PENDIENTE',version=version+1 WHERE evento_id=?",remoto.etag(),id);
                publicaciones.auditar(id,"REVISION_ACEPTADA",quien.id(),null);
                seguimiento.revisar(id);
            });
            return tx.execute(t->ver(id));
        } catch(Fallo f) { throw problema(f.codigo()); }
    }
    private PublicacionCalendar publicacionEnConflicto(long id) {
        evento(id);
        var p=publicaciones.consultar(id);
        if(p==null || !"CONFLICTO_REMOTO".equals(p.error())) throw ProblemaException.conflicto("CALENDAR_SIN_CONFLICTO","La publicación no tiene un conflicto pendiente.");
        if(!Objects.equals(p.calendarId(),config.calendarId()) || !Objects.equals(p.instalacion(),config.instalacion())) throw ProblemaException.conflicto("CALENDAR_DESTINO_CAMBIADO","Revisá el destino antes de aceptar una revisión.");
        return p;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public Estado estado(long id, UsuarioActual quien) { autorizar(quien);return ver(id); }

    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public List<Estado> pendientes(UsuarioActual quien) {
        autorizar(quien);
        if(!cliente.configurado()) return List.of();
        return publicaciones.ids().stream().map(this::ver)
                .filter(e -> !e.pendiente().equals("NINGUNA") || e.error()!=null || e.seguimientoPendiente()
                        || e.estado().equals("INCIERTO") || e.estado().equals("EN_PROCESO")).toList();
    }
    private Estado ver(long id) {
        Evento e=evento(id);
        var p=publicaciones.consultar(id);
        var trabajo=cola.consultar(id);
        var c=contenidos.de(e);
        boolean habilitado=cliente.configurado();
        boolean nuevo=SeguimientoCalendar.NUEVOS.contains(e.getEstado());
        String pendiente=p==null ? nuevo?"EXPORTAR":"NINGUNA" : p.pendiente();
        boolean retrasado=habilitado && trabajo!=null && trabajo.revision()>trabajo.procesada();
        return new Estado(id,e.getCodigo(),c.salon(),e.getUnidad().getFecha(),
                c.titulo().substring(c.titulo().lastIndexOf(" · ")+3),
                p==null?"NO_EXPORTABLE":p.estado(),habilitado?pendiente:"NINGUNA",
                p==null?null:p.error(),p==null?null:p.exito(),habilitado,
                habilitado && (nuevo || p!=null && p.googleId()!=null)
                    && (p==null || !p.estado().equals("RETIRADO") && !p.pendiente().equals("RETIRAR")),
                habilitado && p!=null && p.googleId()!=null && !p.estado().equals("RETIRADO"),
                retrasado,habilitado && trabajo!=null?trabajo.error():null);
    }

    /** Todas las mutaciones remotas pasan por este lock, incluso retirada versus actualización. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    public Estado operar(long id, boolean retirar, UsuarioActual quien) {
        autorizar(quien);
        if (!cliente.configurado()) throw problema("CALENDAR_NO_CONFIGURADO");
        try (var lock = bloqueos.tomar(id)) {
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
        }
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
            exito=now(), retiro_solicitado=false, version=version+1 WHERE evento_id=?
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
    private static ProblemaException problema(String codigo) {
        HttpStatus status=switch(codigo) {
            case "CONFLICTO_REMOTO", "IDENTIDAD_REMOTA_AJENA", "CONTENIDO_REMOTO_MODIFICADO", "ID_EXISTENTE" -> HttpStatus.CONFLICT;
            case "EVENTO_REMOTO_AUSENTE", "EVENTO_REMOTO_ELIMINADO" -> HttpStatus.GONE;
            case "DATOS_CALENDAR_INVALIDOS" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "CUOTA_GOOGLE" -> HttpStatus.TOO_MANY_REQUESTS;
            case "RED_GOOGLE" -> HttpStatus.GATEWAY_TIMEOUT;
            case "RESPUESTA_GOOGLE_INCONSISTENTE", "ERROR_GOOGLE" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        String accion=switch(codigo) {
            case "CONFLICTO_REMOTO" -> "Revisá los cambios externos y aceptá explícitamente la revisión antes de volver a actualizar o retirar.";
            case "CALENDAR_NO_CONFIGURADO" -> "La integración está desactivada. No se realizó ninguna operación externa.";
            case "CUOTA_GOOGLE" -> "Esperá antes de reintentar: Google limitó las solicitudes.";
            case "AUTORIZACION_GOOGLE_REVOCADA", "PERMISO_GOOGLE_INSUFICIENTE" -> "Revisá las credenciales y el permiso de escritura del calendario. La sesión de Vastio sigue vigente.";
            case "CREDENCIALES_NO_DISPONIBLES" -> "Revisá el archivo y sus permisos; luego reiniciá el backend tras reemplazar las credenciales.";
            case "EVENTO_REMOTO_AUSENTE", "EVENTO_REMOTO_ELIMINADO" -> "La entrada externa no está disponible. Verificá el destino; no se recreó silenciosamente.";
            default -> "No confirmamos la operación. Consultá el pendiente y verificá el resultado antes de reintentar.";
        };
        return new ProblemaException(status,codigo,"Google Calendar requiere atención",accion+" Código: "+codigo+". Los datos de Vastio están guardados.");
    }
}
