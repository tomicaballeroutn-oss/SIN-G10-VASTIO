package ar.edu.utn.vastio.agenda.api;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ar.edu.utn.vastio.agenda.aplicacion.ExportacionCalendarService;
import ar.edu.utn.vastio.agenda.aplicacion.ExportacionCalendarService.Estado;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Permisos.ACCESO_TOTAL)
@Tag(name="Google Calendar", description="M-06: publicación manual y seguimiento local")
public class CalendarEventoController {
    private final ExportacionCalendarService servicio;
    public CalendarEventoController(ExportacionCalendarService servicio) { this.servicio = servicio; }
    @GetMapping("/eventos/{id}/calendar")
    @Operation(summary="Estado de publicación en Calendar")
    public Estado estado(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) { return servicio.estado(id, UsuarioActual.de(jwt)); }
    @PostMapping("/eventos/{id}/calendar")
    @Operation(summary="Exportar o recuperar una publicación", description="Idempotente; nunca recibe contenido ni calendario del navegador.")
    public Estado exportar(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) { return servicio.operar(id, false, UsuarioActual.de(jwt)); }
    @PutMapping("/eventos/{id}/calendar")
    @Operation(summary="Actualizar o reconciliar la publicación")
    public Estado actualizar(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) { return servicio.operar(id, false, UsuarioActual.de(jwt)); }
    @DeleteMapping("/eventos/{id}/calendar")
    @Operation(summary="Retirar una publicación", description="Repetir una retirada confirmada no llama a Google.")
    public Estado retirar(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) { return servicio.operar(id, true, UsuarioActual.de(jwt)); }
    @GetMapping("/calendar/pendientes")
    @Operation(summary="Pendientes de Google Calendar", description="Fuente: publicaciones persistidas, independiente de lectura de avisos.")
    public List<Estado> pendientes(@AuthenticationPrincipal Jwt jwt) { return servicio.pendientes(UsuarioActual.de(jwt)); }
}
