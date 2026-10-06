package ar.edu.utn.vastio.agenda.aplicacion;

import static ar.edu.utn.vastio.usuarios.dominio.RolCodigo.ADMINISTRACION;
import static ar.edu.utn.vastio.usuarios.dominio.RolCodigo.COCINA;
import static ar.edu.utn.vastio.usuarios.dominio.RolCodigo.COMPRAS;
import static ar.edu.utn.vastio.usuarios.dominio.RolCodigo.COORDINACION;
import static ar.edu.utn.vastio.usuarios.dominio.RolCodigo.DIRECCION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;
import ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Ruteo de los avisos de la agenda (docs/sprint-2.md, decisión 10). Administración recibe todos los eventos nuevos y
 * cambios; el resto de las áreas, solo lo que las afecta. Nunca le llega el aviso a quien hizo el cambio
 * (lo descarta {@link NotificacionService}). Un solo aviso por guardado, no uno por dato.
 */
@Component
public class AvisosEvento {

    /** Datos cuyo cambio en un evento confirmado afecta a cocina (más los servicios visibles en cocina). */
    static final Set<String> CAMPOS_COCINA = Set.of("cantidad_invitados", "hora_inicio");

    /** Datos cuyo cambio en un evento confirmado afecta a compras (más los servicios de bebida). */
    static final Set<String> CAMPOS_COMPRAS = Set.of("cantidad_invitados");

    /** «Todas las áreas»: reprogramación y cancelación. Barra no ve eventos. */
    private static final List<RolCodigo> TODAS_LAS_AREAS = List.of(DIRECCION, COORDINACION, ADMINISTRACION, COMPRAS, COCINA);

    private final NotificacionService notificaciones;
    private final DescripcionEvento descripcion;

    public AvisosEvento(NotificacionService notificaciones, DescripcionEvento descripcion) {
        this.notificaciones = notificaciones;
        this.descripcion = descripcion;
    }

    /**
     * Lo llama {@code MaquinaDeEstados} después de cada transición. Los estados sin aviso (Liberada, En curso,
     * Realizado, Cerrado) no generan nada.
     *
     * @param origenId null si la transición es automática.
     */
    public void cambioDeEstado(Evento evento, Long origenId) {
        switch (evento.getEstado()) {
            case PRE_RESERVA -> avisar(evento, TipoNotificacion.EVENTO_NUEVO, "Nueva pre-reserva", origenId,
                    List.of(ADMINISTRACION, COORDINACION), List.of());
            case SENADO -> avisar(evento, TipoNotificacion.SENA, "Seña registrada", origenId,
                    List.of(ADMINISTRACION, COORDINACION), List.of());
            case CONTRATADO -> avisar(evento, TipoNotificacion.CONTRATO, "Contrato firmado", origenId,
                    List.of(ADMINISTRACION, COORDINACION), List.of(evento.getVendedoraId()));
            case CONFIRMADO -> avisar(evento, TipoNotificacion.CONFIRMACION, "Evento confirmado", origenId,
                    List.of(COMPRAS, COCINA, ADMINISTRACION), List.of(evento.getVendedoraId()));
            case CANCELADO -> avisar(evento, TipoNotificacion.CANCELACION, "Evento cancelado", origenId,
                    TODAS_LAS_AREAS, titularYPlanner(evento));
            default -> {
                // Liberada no es una cancelación y las transiciones operativas no avisan.
            }
        }
    }

    /**
     * Un guardado que cambió datos del evento.
     *
     * @param campos       nombres de los datos que cambiaron, como en {@code modificacion_evento.campo}.
     * @param afectaCocina true si cambió un servicio visible en cocina.
     * @param afectaCompras true si cambió un servicio de bebida (bodega, tipo de barra).
     */
    public void modificacion(Evento evento, Collection<String> campos, boolean afectaCocina, boolean afectaCompras,
            long origenId) {
        if (campos.isEmpty()) {
            return;
        }
        Set<RolCodigo> perfiles = EnumSet.of(COORDINACION, ADMINISTRACION);
        if (evento.getEstado() == EstadoEvento.CONFIRMADO) {
            if (afectaCocina || campos.stream().anyMatch(CAMPOS_COCINA::contains)) {
                perfiles.add(COCINA);
            }
            if (afectaCompras || campos.stream().anyMatch(CAMPOS_COMPRAS::contains)) {
                perfiles.add(COMPRAS);
            }
        }
        avisar(evento, TipoNotificacion.MODIFICACION, "Evento modificado", origenId, perfiles, titularYPlanner(evento));
    }

    public void modificacion(Evento evento, Collection<String> campos, long origenId) {
        modificacion(evento, campos, false, false, origenId);
    }

    /** Cambio de salón, fecha o turno. El evento ya tiene la unidad nueva. */
    public void reprogramacion(Evento evento, String antes, long origenId) {
        avisar(evento, TipoNotificacion.REPROGRAMACION, "Evento reprogramado", " (antes " + antes + ")", origenId,
                TODAS_LAS_AREAS, titularYPlanner(evento));
    }

    /**
     * Asignación, cambio o quita de la planner: le llega a la nueva, a la anterior y a Administración.
     *
     * @param nombreNueva null si se quitó la planner.
     */
    public void planner(Evento evento, Long anteriorId, String nombreNueva, long origenId) {
        List<Long> personas = new ArrayList<>();
        if (evento.getPlannerId() != null) {
            personas.add(evento.getPlannerId());
        }
        if (anteriorId != null) {
            personas.add(anteriorId);
        }
        String titulo = nombreNueva == null ? "Planner quitada" : "Planner asignada (" + nombreNueva + ")";
        avisar(evento, TipoNotificacion.PLANNER_ASIGNADA, titulo, origenId, List.of(ADMINISTRACION), personas);
    }

    private static List<Long> titularYPlanner(Evento evento) {
        Set<Long> personas = new LinkedHashSet<>();
        personas.add(evento.getVendedoraId());
        if (evento.getPlannerId() != null) {
            personas.add(evento.getPlannerId());
        }
        return List.copyOf(personas);
    }

    private void avisar(Evento evento, TipoNotificacion tipo, String titulo, Long origenId,
            Collection<RolCodigo> perfiles, Collection<Long> personas) {
        avisar(evento, tipo, titulo, "", origenId, perfiles, personas);
    }

    private void avisar(Evento evento, TipoNotificacion tipo, String titulo, String sufijo, Long origenId,
            Collection<RolCodigo> perfiles, Collection<Long> personas) {
        notificaciones.notificar(evento.getId(), tipo, recortar(titulo + ": " + descripcion.de(evento) + sufijo),
                origenId, perfiles, personas);
    }

    /** {@code notificacion.mensaje} es varchar(255). */
    private static String recortar(String mensaje) {
        return mensaje.length() <= 255 ? mensaje : mensaje.substring(0, 254) + "…";
    }
}
