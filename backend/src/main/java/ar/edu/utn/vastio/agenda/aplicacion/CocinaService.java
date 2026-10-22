package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.ServicioContratado;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.ServicioContratadoRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.ParametroService;
import ar.edu.utn.vastio.configuracion.dominio.ParametroClave;

/**
 * Consultar vista de cocina (A7-02, UI-23): los eventos que cocina tiene que preparar, con lo gastronómico. Solo
 * Confirmados y En curso: un evento cancelado o todavía sin confirmar no aparece, y el estado no se muestra.
 * <p>
 * El perfil Cocina ve desde hoy hasta {@code HORIZONTE_COCINA_DIAS} adelante (docs/perfiles-y-permisos.md). Los demás
 * perfiles con acceso (Dirección, Coordinación, Planner, Compras) ven el mes entero: un usuario con varios perfiles
 * suma permisos. Un evento En curso se ve siempre, aunque su jornada haya empezado ayer: es lo que se cocina ahora.
 */
@Service
public class CocinaService {

    static final Set<EstadoEvento> ESTADOS = Set.of(EstadoEvento.CONFIRMADO, EstadoEvento.EN_CURSO);

    /** Perfiles con vista de cocina que no tienen el límite de días. */
    private static final Set<String> SIN_HORIZONTE = Set.of("DIRECCION", "COORDINACION", "PLANNER", "COMPRAS");

    private final EventoRepository eventos;
    private final ServicioContratadoRepository servicios;
    private final ParametroService parametros;
    private final Clock reloj;

    public CocinaService(EventoRepository eventos, ServicioContratadoRepository servicios, ParametroService parametros,
            Clock reloj) {
        this.eventos = eventos;
        this.servicios = servicios;
        this.parametros = parametros;
        this.reloj = reloj;
    }

    /** Lo que puede ver quien consulta: el último día visible es null si no tiene límite. */
    public record Horizonte(LocalDate hoy, LocalDate hasta) {

        boolean incluye(Evento e) {
            if (e.getEstado() == EstadoEvento.EN_CURSO) {
                return true;
            }
            LocalDate fecha = e.getUnidad().getFecha();
            return hasta == null || (!fecha.isBefore(hoy) && !fecha.isAfter(hasta));
        }
    }

    /** Un evento de la vista con sus servicios cargados (de todas las categorías; filtra quien arma la respuesta). */
    public record EventoCocina(Evento evento, List<ServicioContratado> servicios) {
    }

    public record Cocina(YearMonth mes, Horizonte horizonte, List<EventoCocina> eventos) {
    }

    @Transactional(readOnly = true)
    public Cocina mes(YearMonth mes, UsuarioActual quien) {
        Horizonte horizonte = horizonte(quien);
        List<Evento> delMes = eventos.enEstadosEntre(mes.atDay(1), mes.atEndOfMonth(), ESTADOS).stream()
                .filter(horizonte::incluye)
                .toList();
        return new Cocina(mes, horizonte, conServicios(delMes));
    }

    /** La tarjeta de un evento, para el aviso que lleva a ella. Si ya no está en la vista, lo dice. */
    @Transactional(readOnly = true)
    public EventoCocina evento(long id, UsuarioActual quien) {
        Horizonte horizonte = horizonte(quien);
        Evento evento = eventos.conDetalle(id)
                .filter(e -> ESTADOS.contains(e.getEstado()) && horizonte.incluye(e))
                .orElseThrow(() -> ProblemaException.noEncontrado("FUERA_DE_COCINA",
                        "Ese evento ya no figura en la vista de cocina: se canceló, se reprogramó o todavía no está confirmado."));
        return conServicios(List.of(evento)).get(0);
    }

    public Horizonte horizonte(UsuarioActual quien) {
        LocalDate hoy = LocalDate.now(reloj);
        if (quien.tieneAlguno(SIN_HORIZONTE)) {
            return new Horizonte(hoy, null);
        }
        return new Horizonte(hoy, hoy.plusDays(parametros.entero(ParametroClave.HORIZONTE_COCINA_DIAS)));
    }

    private List<EventoCocina> conServicios(List<Evento> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ServicioContratado>> porEvento = servicios.findByEventoIdIn(lista.stream().map(Evento::getId).toList())
                .stream().collect(Collectors.groupingBy(ServicioContratado::getEventoId));
        return lista.stream().map(e -> new EventoCocina(e, porEvento.getOrDefault(e.getId(), List.of()))).toList();
    }
}
