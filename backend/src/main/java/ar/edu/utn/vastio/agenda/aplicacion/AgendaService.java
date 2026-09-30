package ar.edu.utn.vastio.agenda.aplicacion;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.agenda.dominio.UnidadComercializable;
import ar.edu.utn.vastio.agenda.infraestructura.BloqueoRepository;
import ar.edu.utn.vastio.agenda.infraestructura.EventoRepository;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.Salon;
import ar.edu.utn.vastio.configuracion.dominio.TipoEvento;
import ar.edu.utn.vastio.configuracion.dominio.Turno;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;

/**
 * Consultar agenda (UI-07): las unidades ocupadas de un mes. Las que no aparecen están disponibles.
 * Liberadas y canceladas no ocupan la unidad, así que no aparecen.
 */
@Service
@Transactional(readOnly = true)
public class AgendaService {

    private final EventoRepository eventos;
    private final BloqueoRepository bloqueos;
    private final CatalogoService catalogos;
    private final UsuarioService usuarios;

    public AgendaService(EventoRepository eventos, BloqueoRepository bloqueos, CatalogoService catalogos,
            UsuarioService usuarios) {
        this.eventos = eventos;
        this.bloqueos = bloqueos;
        this.catalogos = catalogos;
        this.usuarios = usuarios;
    }

    public Agenda mes(YearMonth mes, UsuarioActual quien) {
        LocalDate desde = mes.atDay(1);
        LocalDate hasta = mes.atEndOfMonth();
        List<Evento> ocupadas = eventos.activosEntre(desde, hasta, EstadoEvento.INACTIVOS);

        Set<Long> personas = new HashSet<>();
        ocupadas.forEach(e -> {
            personas.add(e.getVendedoraId());
            if (e.getPlannerId() != null) {
                personas.add(e.getPlannerId());
            }
        });
        Map<Long, String> nombres = usuarios.nombres(personas);
        Map<Short, String> tipos = catalogos.tiposEvento().stream()
                .collect(Collectors.toMap(TipoEvento::getId, TipoEvento::getNombre));

        List<Unidad> unidades = new ArrayList<>();
        Set<Long> conEvento = new HashSet<>();
        for (Evento e : ocupadas) {
            conEvento.add(e.getUnidad().getId());
            boolean detalle = AccesoEvento.veDetalle(quien, e);
            Persona vendedora = new Persona(e.getVendedoraId(), nombres.get(e.getVendedoraId()));
            Persona planner = e.getPlannerId() == null ? null : new Persona(e.getPlannerId(), nombres.get(e.getPlannerId()));
            EventoEnAgenda evento = detalle
                    ? new EventoEnAgenda(e.getId(), e.getCodigo(), e.getNombre(), tipos.get(e.getTipoEventoId()),
                            e.getCliente().getNombre(), vendedora, planner, true)
                    : new EventoEnAgenda(null, null, null, null, null, vendedora, planner, false);
            unidades.add(new Unidad(e.getUnidad(), e.getEstado().name(), evento, null));
        }
        for (var bloqueada : bloqueos.activosEntre(desde, hasta)) {
            // Un evento activo y un bloqueo no conviven (los separa el FOR UPDATE); si pasara, manda el evento.
            if (!conEvento.contains(bloqueada.unidad().getId())) {
                var b = bloqueada.bloqueo();
                unidades.add(new Unidad(bloqueada.unidad(), BLOQUEADO, null,
                        new BloqueoEnAgenda(catalogos.motivo(b.getMotivoId()).getNombre(), b.getDetalle())));
            }
        }
        unidades.sort(Comparator.comparing((Unidad u) -> u.unidad().getFecha())
                .thenComparing(u -> u.unidad().getTurnoId())
                .thenComparing(u -> u.unidad().getSalonId()));
        return new Agenda(mes, catalogos.salones(), catalogos.turnos(), unidades);
    }

    /** Estado de una unidad bloqueada; no es un estado del evento. */
    public static final String BLOQUEADO = "BLOQUEADO";

    public record Agenda(YearMonth mes, List<Salon> salones, List<Turno> turnos, List<Unidad> unidades) {
    }

    /** Una unidad ocupada: por un evento activo o por un bloqueo. */
    public record Unidad(UnidadComercializable unidad, String estado, EventoEnAgenda evento, BloqueoEnAgenda bloqueo) {
    }

    /**
     * Si {@code detalle} es false (evento de otra vendedora), solo viajan estado, vendedora y planner:
     * la vendedora sabe quién tiene la fecha, pero no los datos del evento.
     */
    public record EventoEnAgenda(Long id, String codigo, String nombre, String tipo, String cliente, Persona vendedora,
            Persona planner, boolean detalle) {
    }

    public record Persona(long id, String nombre) {
    }

    public record BloqueoEnAgenda(String motivo, String detalle) {
    }
}
