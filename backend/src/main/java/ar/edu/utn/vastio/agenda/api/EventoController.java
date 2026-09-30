package ar.edu.utn.vastio.agenda.api;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.agenda.api.EventoDto.EventoResumen;
import ar.edu.utn.vastio.agenda.api.EventoDto.FichaResponse;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.DatosEventoService;
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
    private final ConsultaEventoService consultas;
    private final DatosEventoService datos;
    private final EventoDto dto;

    public EventoController(PreReservaService preReservas, ConsultaEventoService consultas, DatosEventoService datos,
            EventoDto dto) {
        this.preReservas = preReservas;
        this.consultas = consultas;
        this.datos = datos;
        this.dto = dto;
    }

    @PutMapping("/{id}")
    @PreAuthorize(Permisos.EDITAR_EVENTOS)
    @Operation(summary = "Registrar evento", description = """
            Completa o modifica cliente, contactos, tipo, nombre, invitados y observaciones internas, entre Pre-reserva
            y Confirmado. Lo hacen la vendedora titular, la planner asignada, Coordinación y Dirección. Cada dato que
            cambia queda en el historial. Devuelve la ficha actualizada.""")
    @ApiResponse(responseCode = "409", description = "EVENTO_MODIFICADO: otra persona guardó cambios en el medio")
    public FichaResponse registrarDatos(@PathVariable long id, @Valid @RequestBody DatosEventoRequest pedido,
            @AuthenticationPrincipal Jwt jwt) {
        UsuarioActual quien = UsuarioActual.de(jwt);
        datos.registrar(id, pedido.datos(), quien);
        return dto.ficha(consultas.ficha(id, quien));
    }

    @GetMapping
    @PreAuthorize(Permisos.CONSULTAR_EVENTOS)
    @Operation(summary = "Próximos eventos", description = """
            Eventos activos desde hoy. La vendedora ve los suyos («Mis eventos»); Dirección, Coordinación,
            Administración, Planner y Compras, todos.""")
    public List<EventoResumen> proximos(@AuthenticationPrincipal Jwt jwt) {
        var proximos = consultas.proximos(UsuarioActual.de(jwt));
        return proximos.eventos().stream().map(e -> dto.resumen(e, proximos.nombres())).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.CONSULTAR_EVENTOS)
    @Operation(summary = "Ficha del evento", description = """
            Datos, estado, acciones disponibles para quien consulta e historial de cambios. La vendedora solo abre
            sus eventos. El importe de la seña solo llega a Dirección, Coordinación, Administración y la vendedora titular.""")
    @ApiResponse(responseCode = "403", description = "Evento de otra vendedora")
    public FichaResponse ficha(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return dto.ficha(consultas.ficha(id, UsuarioActual.de(jwt)));
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
