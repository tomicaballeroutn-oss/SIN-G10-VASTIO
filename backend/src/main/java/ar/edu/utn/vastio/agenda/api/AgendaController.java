package ar.edu.utn.vastio.agenda.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

import ar.edu.utn.vastio.agenda.aplicacion.AgendaService;
import ar.edu.utn.vastio.agenda.aplicacion.AgendaService.BloqueoEnAgenda;
import ar.edu.utn.vastio.agenda.aplicacion.AgendaService.EventoEnAgenda;
import ar.edu.utn.vastio.agenda.aplicacion.AgendaService.Persona;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Consultar agenda (UI-07). La ven Dirección, Coordinación, Administración, Vendedora, Planner y Compras.
 */
@RestController
@RequestMapping("/api/v1/agenda")
@Tag(name = "Agenda", description = "Disponibilidad de los salones por fecha y turno")
public class AgendaController {

    private final AgendaService agenda;

    public AgendaController(AgendaService agenda) {
        this.agenda = agenda;
    }

    @GetMapping
    @PreAuthorize(Permisos.AGENDA)
    @Operation(summary = "Agenda de un mes", description = """
            Devuelve solo las unidades ocupadas (evento activo o bloqueo); las que no aparecen están disponibles.
            Para los eventos de otra vendedora, la vendedora recibe estado, vendedora y planner, sin datos del evento.""")
    public AgendaResponse mes(@Parameter(example = "2026-09") @RequestParam YearMonth mes, @AuthenticationPrincipal Jwt jwt) {
        return AgendaResponse.de(agenda.mes(mes, UsuarioActual.de(jwt)));
    }

    public record AgendaResponse(YearMonth mes, List<SalonAgenda> salones, List<TurnoAgenda> turnos,
            List<UnidadOcupada> unidades) {
        static AgendaResponse de(AgendaService.Agenda a) {
            return new AgendaResponse(a.mes(),
                    a.salones().stream().map(s -> new SalonAgenda(s.getId(), s.getCodigo(), s.getNombre(), s.isActivo())).toList(),
                    a.turnos().stream().map(t -> new TurnoAgenda(t.getId(), t.getCodigo(), t.getNombre(), t.getHoraInicio(),
                            t.getHoraFin(), t.isCruzaMedianoche())).toList(),
                    a.unidades().stream().map(u -> new UnidadOcupada(u.unidad().getFecha(), u.unidad().getSalonId(),
                            u.unidad().getTurnoId(), u.estado(), u.evento() == null ? null : EventoAgenda.de(u.evento()),
                            u.bloqueo())).toList());
        }
    }

    public record SalonAgenda(short id, String codigo, String nombre, boolean activo) {
    }

    public record TurnoAgenda(short id, String codigo, String nombre, LocalTime horaInicio, LocalTime horaFin,
            boolean cruzaMedianoche) {
    }

    /** {@code estado}: el del evento (PRE_RESERVA, SENADO, …) o BLOQUEADO. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UnidadOcupada(LocalDate fecha, short salonId, short turnoId, String estado, EventoAgenda evento,
            BloqueoEnAgenda bloqueo) {
    }

    /** Sin detalle, los datos del evento no viajan (ni siquiera como null). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventoAgenda(Long id, String codigo, String nombre, String tipo, String cliente, Persona vendedora,
            Persona planner, boolean detalle) {
        static EventoAgenda de(EventoEnAgenda e) {
            return new EventoAgenda(e.id(), e.codigo(), e.nombre(), e.tipo(), e.cliente(), e.vendedora(), e.planner(),
                    e.detalle());
        }
    }
}
