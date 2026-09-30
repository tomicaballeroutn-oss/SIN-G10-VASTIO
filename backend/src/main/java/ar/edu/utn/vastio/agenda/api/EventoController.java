package ar.edu.utn.vastio.agenda.api;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.agenda.aplicacion.PreReservaService;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Eventos: pre-reserva y, en las historias siguientes, ficha, datos, liberación y seña.
 */
@RestController
@RequestMapping("/api/v1/eventos")
@Tag(name = "Eventos", description = "Pre-reserva, ficha, datos, liberación y seña")
public class EventoController {

    private final PreReservaService preReservas;

    public EventoController(PreReservaService preReservas) {
        this.preReservas = preReservas;
    }

    @PostMapping
    @PreAuthorize(Permisos.PRERESERVA)
    @Operation(summary = "Registrar pre-reserva", description = """
            Aparta la unidad (salón, fecha y turno) para un cliente existente o nuevo. El evento nace en Pre-reserva,
            con código EV-AAAA-NNNNN, y avisa a Administración y Coordinación.""")
    @ApiResponse(responseCode = "201", description = "Pre-reserva registrada")
    @ApiResponse(responseCode = "409", description = "FECHA_TOMADA o UNIDAD_BLOQUEADA")
    @ApiResponse(responseCode = "422", description = "Fecha pasada, salón o tipo dado de baja, vendedora inválida")
    public ResponseEntity<PreReservaResponse> registrar(@Valid @RequestBody PreReservaRequest pedido,
            @AuthenticationPrincipal Jwt jwt) {
        Evento evento = preReservas.registrar(pedido.pedido(), UsuarioActual.de(jwt));
        return ResponseEntity.created(URI.create("/api/v1/eventos/" + evento.getId())).body(PreReservaResponse.de(evento));
    }

    public record PreReservaResponse(long id, String codigo, String nombre, EstadoEvento estado) {
        static PreReservaResponse de(Evento e) {
            return new PreReservaResponse(e.getId(), e.getCodigo(), e.getNombre(), e.getEstado());
        }
    }
}
