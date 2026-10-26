package ar.edu.utn.vastio.agenda.aplicacion;

import static ar.edu.utn.vastio.usuarios.dominio.RolCodigo.ADMINISTRACION;
import static ar.edu.utn.vastio.usuarios.dominio.RolCodigo.COCINA;
import static ar.edu.utn.vastio.usuarios.dominio.RolCodigo.COMPRAS;
import static ar.edu.utn.vastio.usuarios.dominio.RolCodigo.COORDINACION;
import static ar.edu.utn.vastio.usuarios.dominio.RolCodigo.DIRECCION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;
import ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Ruteo y textos de los avisos de la agenda (docs/sprint-2.md, decisión 10). Administración recibe todos los eventos
 * nuevos y cambios; el resto de las áreas, solo lo que las afecta. Nunca le llega el aviso a quien hizo el cambio
 * (lo descarta {@link NotificacionService}). Un solo aviso por guardado, no uno por dato.
 *
 * <p>Los textos dicen quién hizo el cambio: «Lucía Ferreyra confirmó el evento: …». La titular y la planner reciben
 * el suyo en segunda persona («… confirmó tu evento», «… te asignó como planner»).
 */
@Component
public class AvisosEvento {

    /** Datos cuyo cambio en un evento confirmado afecta a cocina (más los servicios visibles en cocina). */
    static final Set<String> CAMPOS_COCINA = Set.of("cantidad_invitados", "hora_inicio");

    /** Datos cuyo cambio en un evento confirmado afecta a compras (más los servicios de bebida). */
    static final Set<String> CAMPOS_COMPRAS = Set.of("cantidad_invitados");

    /** «Todas las áreas»: reprogramación y cancelación. Barra no ve eventos. */
    private static final List<RolCodigo> TODAS_LAS_AREAS = List.of(DIRECCION, COORDINACION, ADMINISTRACION, COMPRAS, COCINA);

    /** Cómo se nombra en un aviso cada dato de {@code modificacion_evento.campo}. Los servicios son «servicio.Categoría». */
    private static final Map<String, String> NOMBRE_CAMPO = Map.ofEntries(
            Map.entry("nombre", "el nombre"),
            Map.entry("tipo_evento", "el tipo de evento"),
            Map.entry("cantidad_invitados", "la cantidad de invitados"),
            Map.entry("invitados_definitivos", "los invitados definitivos"),
            Map.entry("planner", "la planner"),
            Map.entry("hora_inicio", "la hora de inicio"),
            Map.entry("unidad", "el salón, la fecha o el turno"),
            Map.entry("observaciones_internas", "las observaciones internas"),
            Map.entry("cliente.nombre", "el nombre del cliente"),
            Map.entry("cliente.documento", "el documento del cliente"),
            Map.entry("cliente.telefono", "el teléfono del cliente"),
            Map.entry("cliente.email", "el correo del cliente"),
            Map.entry("contacto", "los contactos"));

    /** Datos que se nombran en el aviso; de los demás se dice cuántos más cambiaron. */
    private static final int DATOS_NOMBRADOS = 3;

    private final NotificacionService notificaciones;
    private final DescripcionEvento descripcion;
    private final UsuarioService usuarios;
    private final CatalogoService catalogos;

    public AvisosEvento(NotificacionService notificaciones, DescripcionEvento descripcion, UsuarioService usuarios,
            CatalogoService catalogos) {
        this.catalogos = catalogos;
        this.notificaciones = notificaciones;
        this.descripcion = descripcion;
        this.usuarios = usuarios;
    }

    /**
     * Lo llama {@code MaquinaDeEstados} después de cada transición. Los estados sin aviso (Liberada, En curso,
     * Cerrado) no generan nada. Realizado avisa solo a la planner de un evento con asistencia por segmento, para que
     * cargue la real (docs/sprint-4.md, decisión 9).
     *
     * @param origenId null si la transición es automática.
     */
    public void cambioDeEstado(Evento evento, Long origenId) {
        String autor = autor(origenId);
        switch (evento.getEstado()) {
            case PRE_RESERVA -> avisar(evento, TipoNotificacion.EVENTO_NUEVO, accion(autor, "cargó una pre-reserva"), "",
                    origenId, List.of(ADMINISTRACION, COORDINACION), Map.of());
            case SENADO -> avisar(evento, TipoNotificacion.SENA, accion(autor, "registró la seña"), "", origenId,
                    List.of(ADMINISTRACION, COORDINACION), Map.of());
            case CONTRATADO -> avisar(evento, TipoNotificacion.CONTRATO, accion(autor, "registró la firma del contrato"), "",
                    origenId, List.of(ADMINISTRACION, COORDINACION),
                    Map.of(evento.getVendedoraId(), accion(autor, "registró la firma del contrato de tu evento")));
            case CONFIRMADO -> avisar(evento, TipoNotificacion.CONFIRMACION, accion(autor, "confirmó el evento"), "",
                    origenId, List.of(COMPRAS, COCINA, ADMINISTRACION),
                    Map.of(evento.getVendedoraId(), accion(autor, "confirmó tu evento")));
            case REALIZADO -> {
                if (evento.getPlannerId() != null && catalogos.tipoEvento(evento.getTipoEventoId()).isUsaSegmentos()) {
                    avisar(evento, TipoNotificacion.ASISTENCIA_REAL, "Cargá la asistencia real de cierre", "", origenId,
                            List.of(), Map.of(evento.getPlannerId(), "Cargá la asistencia real de cierre"));
                }
            }
            case CANCELADO -> avisar(evento, TipoNotificacion.CANCELACION, accion(autor, "canceló el evento"), "", origenId,
                    TODAS_LAS_AREAS, titularYPlanner(evento, accion(autor, "canceló tu evento")));
            default -> {
                // Liberada no es una cancelación y las transiciones operativas no avisan.
            }
        }
    }

    /**
     * Un guardado que cambió datos del evento. El aviso nombra los datos, no los valores: esos quedan en el historial.
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
        String autor = autor(origenId);
        String datos = datos(campos);
        avisar(evento, TipoNotificacion.MODIFICACION, accion(autor, "cambió " + datos), "", origenId, perfiles,
                titularYPlanner(evento, accion(autor, "cambió " + datos + " de tu evento")));
    }

    public void modificacion(Evento evento, Collection<String> campos, long origenId) {
        modificacion(evento, campos, false, false, origenId);
    }

    /** Cambio de salón, fecha o turno. El evento ya tiene la unidad nueva. */
    public void reprogramacion(Evento evento, String antes, long origenId) {
        String autor = autor(origenId);
        avisar(evento, TipoNotificacion.REPROGRAMACION, accion(autor, "reprogramó el evento"), " (antes " + antes + ")",
                origenId, TODAS_LAS_AREAS, titularYPlanner(evento, accion(autor, "reprogramó tu evento")));
    }

    /**
     * Asignación, cambio o quita de la planner: le llega a la nueva, a la anterior y a Administración.
     *
     * @param nombreNueva null si se quitó la planner.
     */
    public void planner(Evento evento, Long anteriorId, String nombreNueva, long origenId) {
        String autor = autor(origenId);
        String nombreAnterior = anteriorId == null ? null : usuarios.nombres(List.of(anteriorId)).get(anteriorId);
        String general = nombreNueva == null ? accion(autor, "quitó a " + nombreAnterior + " como planner")
                : accion(autor, "asignó a " + nombreNueva + " como planner");
        String sufijo = nombreNueva != null && nombreAnterior != null ? " (antes " + nombreAnterior + ")" : "";
        Map<Long, String> personales = new LinkedHashMap<>();
        if (evento.getPlannerId() != null) {
            personales.put(evento.getPlannerId(), accion(autor, "te asignó como planner"));
        }
        if (anteriorId != null) {
            personales.put(anteriorId, accion(autor, "te quitó como planner"));
        }
        avisar(evento, TipoNotificacion.PLANNER_ASIGNADA, general, sufijo, origenId, List.of(ADMINISTRACION), personales);
    }

    /** El mismo texto en segunda persona para la titular y la planner. */
    private static Map<Long, String> titularYPlanner(Evento evento, String texto) {
        Map<Long, String> personas = new LinkedHashMap<>();
        personas.put(evento.getVendedoraId(), texto);
        if (evento.getPlannerId() != null) {
            personas.put(evento.getPlannerId(), texto);
        }
        return personas;
    }

    private String autor(Long origenId) {
        return origenId == null ? null : usuarios.nombres(List.of(origenId)).get(origenId);
    }

    /** «Lucía Ferreyra confirmó el evento»; sin autor (cambio automático), «Se confirmó el evento». */
    private static String accion(String autor, String que) {
        return autor == null ? "Se " + que : autor + " " + que;
    }

    /** «la cantidad de invitados, la hora de inicio, el nombre y 2 datos más». */
    static String datos(Collection<String> campos) {
        List<String> nombres = new ArrayList<>(campos.stream().map(AvisosEvento::nombreCampo).distinct().toList());
        if (nombres.size() > DATOS_NOMBRADOS + 1) {
            int resto = nombres.size() - DATOS_NOMBRADOS;
            nombres = new ArrayList<>(nombres.subList(0, DATOS_NOMBRADOS));
            nombres.add(resto + " datos más");
        }
        if (nombres.size() == 1) {
            return nombres.get(0);
        }
        return String.join(", ", nombres.subList(0, nombres.size() - 1)) + " y " + nombres.get(nombres.size() - 1);
    }

    private static String nombreCampo(String campo) {
        if (campo.startsWith("servicio.")) {
            return "el servicio de " + campo.substring("servicio.".length());
        }
        if (campo.startsWith(AsistenciaService.CAMPO_PREVISTA)) {
            return "la asistencia prevista de " + campo.substring(AsistenciaService.CAMPO_PREVISTA.length());
        }
        return NOMBRE_CAMPO.getOrDefault(campo, campo);
    }

    /**
     * @param personales texto propio de cada persona afectada; le llega ese y no el general.
     */
    private void avisar(Evento evento, TipoNotificacion tipo, String titulo, String sufijo, Long origenId,
            Collection<RolCodigo> perfiles, Map<Long, String> personales) {
        String delEvento = ": " + descripcion.de(evento) + sufijo;
        Map<Long, String> textos = new LinkedHashMap<>();
        personales.forEach((id, texto) -> textos.put(id, recortar(texto + delEvento)));
        notificaciones.notificar(evento.getId(), tipo, recortar(titulo + delEvento), origenId, perfiles, List.of(), textos);
    }

    /** {@code notificacion.mensaje} es varchar(255). */
    private static String recortar(String mensaje) {
        return mensaje.length() <= 255 ? mensaje : mensaje.substring(0, 254) + "…";
    }
}
