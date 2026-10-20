package ar.edu.utn.vastio.bebida.api;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ar.edu.utn.vastio.bebida.aplicacion.PreparacionService;
import ar.edu.utn.vastio.bebida.api.PreparacionDto.*;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/eventos/{eventoId}/preparaciones")
@PreAuthorize(Permisos.REGISTRAR_PREPARACION)
@Tag(name = "Preparación", description = "Traspasos manuales del depósito madre a transición, sin consumo")
public class PreparacionController {
    private final PreparacionService preparaciones;
    private final UsuarioService usuarios;
    public PreparacionController(PreparacionService preparaciones, UsuarioService usuarios) {
        this.preparaciones = preparaciones;
        this.usuarios = usuarios;
    }
    @GetMapping
    @Operation(summary = "Consultar circuitos e historial de preparación del evento")
    public PantallaResponse pantalla(@PathVariable long eventoId) {
        var p = preparaciones.pantalla(eventoId);
        return PantallaResponse.de(p, usuarios.nombres(p.historial().stream().map(r -> r.preparacion().getUsuarioId()).distinct().toList()));
    }
    @PostMapping
    @Operation(summary = "Registrar preparación", description = """
            Confirmado o En curso. Bebidas activas, cantidades positivas en botellas enteras, sin repetir.
            Destino revisado debe coincidir con la transición activa de la barra del salón. Actualiza ambos saldos
            en una transacción; permite negativo y alerta a Compras y Administración. No constituye consumo.
            Idempotency-Key obligatorio (1 a 100 letras, números, guion o guion bajo), único por evento:
            201 al crear; 200 al recuperar mismo contenido, incluso después del cierre; 409 si cambia el contenido.
            Operaciones e historial inmutables. 403 para los perfiles no autorizados.""")
    public ResponseEntity<Response> registrar(@PathVariable long eventoId,
            @RequestHeader("Idempotency-Key") String clave, @Valid @RequestBody Request pedido,
            @AuthenticationPrincipal Jwt jwt) {
        var r = preparaciones.registrar(eventoId, clave, pedido.datos(), UsuarioActual.de(jwt).id());
        return ResponseEntity.status(r.repetida() ? 200 : 201).body(Response.de(r.registrada(),
                usuarios.nombres(List.of(r.registrada().preparacion().getUsuarioId()))));
    }
}
