package ar.edu.utn.vastio.agenda.api;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.agenda.api.EventoDto.EventoResumen;
import ar.edu.utn.vastio.agenda.api.EventoDto.FichaResponse;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.ContratoService;
import ar.edu.utn.vastio.agenda.aplicacion.DatosEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.InvitadosService;
import ar.edu.utn.vastio.agenda.aplicacion.PreReservaService;
import ar.edu.utn.vastio.agenda.aplicacion.SenaService;
import ar.edu.utn.vastio.agenda.aplicacion.ServiciosService;
import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.agenda.dominio.Evento;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Eventos: pre-reserva, ficha, datos, liberación, seña y firma de contrato.
 */
@RestController
@RequestMapping("/api/v1/eventos")
@Tag(name = "Eventos", description = "Pre-reserva, ficha, datos, liberación, seña y contrato")
public class EventoController {

    private final PreReservaService preReservas;
    private final ConsultaEventoService consultas;
    private final DatosEventoService datos;
    private final SenaService senas;
    private final ContratoService contratos;
    private final ServiciosService servicios;
    private final InvitadosService invitados;
    private final EventoDto dto;

    public EventoController(PreReservaService preReservas, ConsultaEventoService consultas, DatosEventoService datos,
            SenaService senas, ContratoService contratos, ServiciosService servicios, InvitadosService invitados,
            EventoDto dto) {
        this.invitados = invitados;
        this.senas = senas;
        this.contratos = contratos;
        this.servicios = servicios;
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

    @PutMapping("/{id}/invitados")
    @PreAuthorize(Permisos.EDITAR_EVENTOS)
    @Operation(summary = "Registrar cantidad de invitados", description = """
            Cantidad (mayor a 0) y si ya es definitiva, entre Pre-reserva y Confirmado. Lo hacen la vendedora titular, la
            planner asignada, Coordinación y Dirección. En un evento confirmado la cantidad cambia pero sigue siendo
            definitiva. Superar la capacidad del salón no se rechaza (la pantalla lo advierte). Devuelve la ficha.""")
    @ApiResponse(responseCode = "409", description = "EVENTO_MODIFICADO: otra persona guardó cambios en el medio")
    @ApiResponse(responseCode = "422", description = "Quitar «definitiva» en un evento confirmado")
    public FichaResponse registrarInvitados(@PathVariable long id, @Valid @RequestBody InvitadosRequest pedido,
            @AuthenticationPrincipal Jwt jwt) {
        UsuarioActual quien = UsuarioActual.de(jwt);
        invitados.registrar(id, pedido.version(), pedido.cantidad(), pedido.definitivos(), quien);
        return dto.ficha(consultas.ficha(id, quien));
    }

    @PutMapping("/{id}/servicios")
    @PreAuthorize(Permisos.EDITAR_EVENTOS)
    @Operation(summary = "Registrar servicios contratados", description = """
            Un texto libre por categoría (vacío la deja sin servicio; las que no vienen no cambian), entre Pre-reserva y
            Confirmado. Lo hacen la vendedora titular, la planner asignada, Coordinación y Dirección. En un evento
            confirmado no se pueden vaciar las categorías requeridas para confirmar. Cada cambio queda en el historial.
            Devuelve la ficha.""")
    @ApiResponse(responseCode = "409", description = "EVENTO_MODIFICADO: otra persona guardó cambios en el medio")
    @ApiResponse(responseCode = "422", description = "Categoría dada de baja o requerida vacía en un evento confirmado")
    public FichaResponse registrarServicios(@PathVariable long id, @Valid @RequestBody ServiciosRequest pedido,
            @AuthenticationPrincipal Jwt jwt) {
        UsuarioActual quien = UsuarioActual.de(jwt);
        servicios.registrar(id, pedido.version(), pedido.lista(), quien);
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

    @PostMapping("/{id}/sena")
    @PreAuthorize(Permisos.PRERESERVA)
    @Operation(summary = "Registrar seña", description = """
            La pre-reserva pasa a Señado con el importe, la fecha del pago y los datos del firmante. La registran la
            vendedora titular, Coordinación y Dirección. Avisa a Administración y Coordinación. Devuelve la ficha.""")
    @ApiResponse(responseCode = "422", description = "El evento no está en Pre-reserva, o la fecha del pago es futura")
    public FichaResponse registrarSena(@PathVariable long id, @Valid @RequestBody SenaRequest pedido,
            @AuthenticationPrincipal Jwt jwt) {
        UsuarioActual quien = UsuarioActual.de(jwt);
        senas.registrar(id, pedido.sena(), quien);
        return dto.ficha(consultas.ficha(id, quien));
    }

    @PostMapping(path = "/{id}/contrato", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Permisos.ACCESO_TOTAL)
    @Operation(summary = "Registrar firma de contrato", description = """
            El evento señado pasa a Contratado con la fecha de firma (no anterior a la seña ni futura) y el contrato
            digitalizado: de 1 a 10 archivos PDF, JPG o PNG de hasta 10 MB cada uno. La registran Coordinación y Dirección.
            Avisa a Administración, Coordinación y la vendedora titular. Devuelve la ficha.""")
    @ApiResponse(responseCode = "413", description = "Un archivo pesa más de 10 MB")
    @ApiResponse(responseCode = "422", description = "El evento no está Señado, la fecha no corresponde o un archivo no es válido")
    public FichaResponse registrarContrato(@PathVariable long id, @Valid @ModelAttribute ContratoRequest pedido,
            @AuthenticationPrincipal Jwt jwt) {
        UsuarioActual quien = UsuarioActual.de(jwt);
        contratos.registrar(id, pedido.fechaFirma(), pedido.archivosLeidos(), quien);
        return dto.ficha(consultas.ficha(id, quien));
    }

    @GetMapping("/{id}/documentos/{documentoId}")
    @PreAuthorize(Permisos.CONSULTAR_EVENTOS)
    @Operation(summary = "Descargar un archivo del legajo", description = """
            Solo para quien ve el importe de la seña (Dirección, Coordinación, Administración y la vendedora titular):
            el contrato tiene importes.""")
    public ResponseEntity<byte[]> descargarDocumento(@PathVariable long id, @PathVariable long documentoId,
            @AuthenticationPrincipal Jwt jwt) {
        ContratoService.Descarga descarga = contratos.descargar(id, documentoId, UsuarioActual.de(jwt));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descarga.documento().getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(descarga.documento().getNombreArchivo(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(descarga.contenido());
    }

    @PostMapping("/{id}/liberacion")
    @PreAuthorize(Permisos.PRERESERVA)
    @Operation(summary = "Liberar pre-reserva", description = """
            La pre-reserva que no prosperó pasa a Liberada y la fecha vuelve a estar disponible. No es una cancelación.
            La hacen la vendedora titular, Coordinación y Dirección. Queda en el historial. Devuelve la ficha.""")
    @ApiResponse(responseCode = "422", description = "El evento no está en Pre-reserva")
    public FichaResponse liberar(@PathVariable long id, @Valid @RequestBody(required = false) LiberacionRequest pedido,
            @AuthenticationPrincipal Jwt jwt) {
        UsuarioActual quien = UsuarioActual.de(jwt);
        String observacion = pedido == null || pedido.observacion() == null || pedido.observacion().isBlank()
                ? null : pedido.observacion().trim();
        preReservas.liberar(id, observacion, quien);
        return dto.ficha(consultas.ficha(id, quien));
    }

    /** Comentario opcional: queda en el historial junto a la liberación. */
    public record LiberacionRequest(@Size(max = 255, message = "Usá hasta 255 caracteres.") String observacion) {
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
