package ar.edu.utn.vastio.agenda.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonInclude;

import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.Acciones;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService.Ficha;
import ar.edu.utn.vastio.agenda.dominio.CambioEstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Cliente;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.Salon;
import ar.edu.utn.vastio.configuracion.dominio.Turno;

/**
 * Respuestas de la ficha (UI-09) y de la lista de eventos. Arma los nombres de salón, turno y tipo con el
 * módulo configuración.
 */
@Component
public class EventoDto {

    private final CatalogoService catalogos;

    public EventoDto(CatalogoService catalogos) {
        this.catalogos = catalogos;
    }

    public record Nombre(long id, String nombre) {
    }

    public record SalonDto(short id, String codigo, String nombre) {
    }

    public record TurnoDto(short id, String codigo, String nombre, LocalTime horaInicio, LocalTime horaFin) {
    }

    public record ClienteDto(long id, String nombre, String documento, String telefono, String email) {
        static ClienteDto de(Cliente c) {
            return new ClienteDto(c.getId(), c.getNombre(), c.getDocumento(), c.getTelefono(), c.getEmail());
        }
    }

    /** {@code importe} solo viaja a quien ve datos económicos (RNF-SEG-04): no alcanza con ocultarlo en pantalla. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SenaDto(BigDecimal importe, LocalDate fecha, String firmanteNombre, String firmanteDni,
            String firmanteContacto) {
    }

    /** Una entrada del historial: cambio de estado. */
    public record HistorialDto(String tipo, EstadoEvento estadoAnterior, EstadoEvento estadoNuevo, Nombre usuario,
            OffsetDateTime fechaHora, String observacion) {
    }

    public record FichaResponse(long id, String codigo, EstadoEvento estado, String nombre, Nombre tipo, SalonDto salon,
            LocalDate fecha, TurnoDto turno, ClienteDto cliente, Nombre vendedora, Nombre planner,
            Integer cantidadInvitados, boolean invitadosDefinitivos, String observacionesInternas, SenaDto sena,
            OffsetDateTime fechaCreacion, Acciones acciones, List<HistorialDto> historial) {
    }

    public record EventoResumen(long id, String codigo, EstadoEvento estado, String nombre, String tipo, SalonDto salon,
            LocalDate fecha, TurnoDto turno, String cliente, Nombre vendedora, Nombre planner, Integer cantidadInvitados) {
    }

    public FichaResponse ficha(Ficha f) {
        Evento e = f.evento();
        Map<Long, String> nombres = f.nombres();
        SenaDto sena = e.getFechaSena() == null ? null
                : new SenaDto(f.veImportes() ? e.getImporteSena() : null, e.getFechaSena(), e.getFirmanteNombre(),
                        e.getFirmanteDni(), e.getFirmanteContacto());
        List<HistorialDto> historial = f.cambios().stream().map(c -> historial(c, nombres)).toList();
        return new FichaResponse(e.getId(), e.getCodigo(), e.getEstado(), e.getNombre(), tipo(e), salon(e),
                e.getUnidad().getFecha(), turno(e), ClienteDto.de(e.getCliente()), persona(e.getVendedoraId(), nombres),
                persona(e.getPlannerId(), nombres), e.getCantidadInvitados(), e.isInvitadosDefinitivos(),
                e.getObservacionesInternas(), sena, e.getFechaCreacion(), f.acciones(), historial);
    }

    public EventoResumen resumen(Evento e, Map<Long, String> nombres) {
        return new EventoResumen(e.getId(), e.getCodigo(), e.getEstado(), e.getNombre(), tipo(e).nombre(), salon(e),
                e.getUnidad().getFecha(), turno(e), e.getCliente().getNombre(), persona(e.getVendedoraId(), nombres),
                persona(e.getPlannerId(), nombres), e.getCantidadInvitados());
    }

    private static HistorialDto historial(CambioEstadoEvento c, Map<Long, String> nombres) {
        return new HistorialDto("ESTADO", c.getEstadoAnterior(), c.getEstadoNuevo(), persona(c.getUsuarioId(), nombres),
                c.getFechaHora(), c.getObservacion());
    }

    private Nombre tipo(Evento e) {
        return new Nombre(e.getTipoEventoId(), catalogos.tipoEvento(e.getTipoEventoId()).getNombre());
    }

    private SalonDto salon(Evento e) {
        Salon s = catalogos.salon(e.getUnidad().getSalonId());
        return new SalonDto(s.getId(), s.getCodigo(), s.getNombre());
    }

    private TurnoDto turno(Evento e) {
        Turno t = catalogos.turno(e.getUnidad().getTurnoId());
        return new TurnoDto(t.getId(), t.getCodigo(), t.getNombre(), t.getHoraInicio(), t.getHoraFin());
    }

    private static Nombre persona(Long id, Map<Long, String> nombres) {
        return id == null ? null : new Nombre(id, nombres.get(id));
    }
}
