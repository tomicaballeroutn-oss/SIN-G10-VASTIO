package ar.edu.utn.vastio.agenda.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.agenda.aplicacion.BloqueoService;
import ar.edu.utn.vastio.agenda.aplicacion.BloqueoService.BloqueoVista;
import ar.edu.utn.vastio.agenda.aplicacion.BloqueoService.Pedido;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Bloqueo de unidades (UI-21). Lo hacen Administración, Coordinación y Dirección; el resto ve el bloqueo en la agenda.
 */
@RestController
@RequestMapping("/api/v1/bloqueos")
@PreAuthorize(Permisos.BLOQUEAR_UNIDADES)
@Tag(name = "Bloqueos", description = "Salones, fechas y turnos fuera de la venta por motivos no comerciales")
public class BloqueoController {

    private final BloqueoService bloqueos;

    public BloqueoController(BloqueoService bloqueos) {
        this.bloqueos = bloqueos;
    }

    @GetMapping
    @Operation(summary = "Bloqueos activos", description = """
            Los bloqueos activos que todavía tienen alguna fecha por delante, el más próximo primero.
            Cada uno resume los salones, el rango de fechas y los turnos que cubre.""")
    public List<BloqueoVista> activos() {
        return bloqueos.activos();
    }

    @PostMapping
    @Operation(summary = "Registrar un bloqueo", description = """
            Bloquea salones × fechas × turnos con un motivo de bloqueo. `hasta` vacío bloquea un solo día; el rango admite
            hasta 180 días y no puede empezar en el pasado. Las unidades se toman con SELECT … FOR UPDATE, así que
            una pre-reserva simultánea sobre las mismas fechas se resuelve sin pisarse.""")
    @ApiResponse(responseCode = "201", description = "Bloqueo registrado")
    @ApiResponse(responseCode = "409", description = "Alguna unidad tiene un evento activo (UNIDAD_OCUPADA, con el código del evento) o ya está bloqueada (YA_BLOQUEADA)")
    @ApiResponse(responseCode = "422", description = "Rango inválido, fecha pasada, rango de más de 180 días, motivo que no es de bloqueo o salón dado de baja")
    public ResponseEntity<BloqueoVista> registrar(@Valid @RequestBody BloqueoRequest pedido, @AuthenticationPrincipal Jwt jwt) {
        BloqueoVista creado = bloqueos.registrar(pedido.pedido(), UsuarioActual.de(jwt));
        return ResponseEntity.created(URI.create("/api/v1/bloqueos")).body(creado);
    }

    @PostMapping("/{id}/desbloqueo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desbloquear", description = """
            Las unidades vuelven a la venta. El bloqueo no se borra: queda inactivo con quién y cuándo lo desbloqueó.""")
    @ApiResponse(responseCode = "204", description = "Desbloqueado")
    @ApiResponse(responseCode = "404", description = "No existe ese bloqueo")
    @ApiResponse(responseCode = "409", description = "Ya estaba desbloqueado")
    public void desbloquear(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        bloqueos.desbloquear(id, UsuarioActual.de(jwt));
    }

    public record BloqueoRequest(
            @NotEmpty(message = "Elegí al menos un salón.") Set<@NotNull(message = "Elegí al menos un salón.") Short> salonIds,
            @NotNull(message = "Elegí la fecha desde la que se bloquea.") LocalDate desde,
            LocalDate hasta,
            @NotEmpty(message = "Elegí al menos un turno.") Set<@NotNull(message = "Elegí al menos un turno.") Short> turnoIds,
            @NotNull(message = "Elegí el motivo.") Short motivoId,
            @Size(max = 255, message = "Usá hasta 255 caracteres.") String detalle) {

        Pedido pedido() {
            return new Pedido(salonIds, desde, hasta, turnoIds, motivoId, detalle);
        }
    }
}
