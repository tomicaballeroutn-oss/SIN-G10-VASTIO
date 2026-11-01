package ar.edu.utn.vastio.agenda.aplicacion;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.PublicacionCalendar;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.PublicacionCalendarRepository;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;
import ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/** Proyección local. Solo desde el procesador aislado o una operación Calendar explícita. */
@Service
@Transactional
public class SeguimientoCalendar {
    public static final Set<EstadoEvento> NUEVOS = Set.of(EstadoEvento.SENADO, EstadoEvento.CONTRATADO, EstadoEvento.CONFIRMADO);
    private final EventoRepository eventos;
    private final PublicacionCalendarRepository publicaciones;
    private final ContenidoCalendar contenido;
    private final NotificacionService avisos;
    private final jakarta.persistence.EntityManager em;
    private final ClienteCalendar cliente;

    public SeguimientoCalendar(EventoRepository eventos, PublicacionCalendarRepository publicaciones,
            ContenidoCalendar contenido, NotificacionService avisos, jakarta.persistence.EntityManager em, ClienteCalendar cliente) {
        this.eventos = eventos; this.publicaciones = publicaciones; this.contenido = contenido; this.avisos = avisos;
        this.em = em; this.cliente=cliente;
    }

    public PublicacionCalendar revisar(long id) {
        if(!cliente.configurado()) return publicaciones.consultar(id);
        Evento evento = eventos.conDetalle(id).orElseThrow(() -> ar.edu.utn.vastio.comun.errores.ProblemaException.noEncontrado("EVENTO_INEXISTENTE", "No encontramos ese evento."));
        // Solo proyección aislada: no fuerza flush de operaciones comerciales.
        em.refresh(evento, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var p = publicaciones.de(id);
        if (p == null && !NUEVOS.contains(evento.getEstado())) return null;
        if (p == null) { publicaciones.asegurar(id); p = publicaciones.de(id); }
        String pendiente;
        // Una identidad reservada puede existir remotamente aunque nunca se confirmó localmente.
        if (p.retiroSolicitado() || EstadoEvento.INACTIVOS.contains(evento.getEstado()) || evento.getEstado() == EstadoEvento.PRE_RESERVA) {
            pendiente = p.googleId() != null && !p.estado().equals("RETIRADO") ? "RETIRAR" : "NINGUNA";
        } else if (p.estado().equals("RETIRADO")) {
            pendiente = "NINGUNA"; // Retirada manual explícita: no se publica de nuevo automáticamente.
        } else if (p.huella() == null) {
            pendiente = NUEVOS.contains(evento.getEstado()) || p.googleId() != null ? "EXPORTAR" : "NINGUNA";
        } else {
            pendiente = contenido.de(evento).huella().equals(p.huella()) ? "NINGUNA" : "ACTUALIZAR";
        }
        if (!pendiente.equals(p.pendiente())) {
            publicaciones.jdbc().update("UPDATE publicacion_calendar SET pendiente=?, version=version+1 WHERE evento_id=?", pendiente, id);
        }
        String clave = p.error() != null ? "ERROR:" + p.error() + ":" + pendiente : pendiente.equals("NINGUNA") ? null : pendiente;
        if (!java.util.Objects.equals(clave, p.avisoClave())) avisar(evento, clave);
        return publicaciones.de(id);
    }

    public void avisar(Evento evento, String clave) {
        avisos.resolverCalendar(evento.getId());
        if (clave != null) {
            String accion = clave.startsWith("ERROR:") ? "Revisar error de Google Calendar" + (clave.endsWith(":RETIRAR") ? " y retirar publicación" : "")
                    : switch (clave) {
                        case "EXPORTAR" -> "Exportar a Google Calendar";
                        case "ACTUALIZAR" -> "Actualizar en Google Calendar";
                        default -> "Retirar de Google Calendar";
                    };
            // Origen null: ambos perfiles reciben el pendiente, incluso quien hizo el cambio.
            avisos.notificar(evento.getId(), TipoNotificacion.CALENDAR_PENDIENTE,
                    accion + ": " + evento.getCodigo() + ".", null, List.of(RolCodigo.DIRECCION, RolCodigo.COORDINACION));
        }
        publicaciones.jdbc().update("UPDATE publicacion_calendar SET aviso_clave=? WHERE evento_id=?", clave, evento.getId());
    }
}
