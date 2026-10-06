package ar.edu.utn.vastio.notificaciones.api;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;
import ar.edu.utn.vastio.notificaciones.dominio.NotificacionDestinatario;
import ar.edu.utn.vastio.notificaciones.dominio.TipoNotificacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Consultar notificaciones (UI-22). Todos los perfiles; cada persona ve y marca solo los suyos.
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
@Tag(name = "Notificaciones", description = "Avisos de la persona: lista, contador y lectura")
public class NotificacionController {

    private final NotificacionService notificaciones;

    public NotificacionController(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    /** {@code eventoId}: el aviso lleva a la ficha de ese evento. */
    public record NotificacionDto(long id, TipoNotificacion tipo, String mensaje, Long eventoId, OffsetDateTime fechaHora,
            boolean leida) {
        static NotificacionDto de(NotificacionDestinatario d) {
            var n = d.getNotificacion();
            return new NotificacionDto(n.getId(), n.getTipo(), n.getMensaje(), n.getEventoId(), n.getFechaHora(), d.isLeida());
        }
    }

    public record PaginaNotificaciones(List<NotificacionDto> notificaciones, int pagina, boolean hayMas, long sinLeer) {
    }

    public record SinLeer(long cantidad) {
    }

    @GetMapping
    @Operation(summary = "Mis notificaciones", description = """
            Más recientes primero, de a 20. Con soloSinLeer=true, solo las que faltan leer. Trae también la cantidad sin
            leer para el contador de la campana.""")
    public PaginaNotificaciones listar(@RequestParam(defaultValue = "false") boolean soloSinLeer,
            @RequestParam(defaultValue = "0") int pagina, @AuthenticationPrincipal Jwt jwt) {
        long usuario = UsuarioActual.de(jwt).id();
        var slice = notificaciones.delUsuario(usuario, soloSinLeer, pagina);
        return new PaginaNotificaciones(slice.getContent().stream().map(NotificacionDto::de).toList(), slice.getNumber(),
                slice.hasNext(), notificaciones.sinLeer(usuario));
    }

    @GetMapping("/sin-leer")
    @Operation(summary = "Cantidad sin leer", description = "Para el contador de la campana; la pantalla lo pide cada minuto.")
    public SinLeer sinLeer(@AuthenticationPrincipal Jwt jwt) {
        return new SinLeer(notificaciones.sinLeer(UsuarioActual.de(jwt).id()));
    }

    @PostMapping("/{id}/lectura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marcar una como leída", description = "404 si el aviso no es de quien lo pide.")
    public void marcarLeida(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        notificaciones.marcarLeida(id, UsuarioActual.de(jwt).id());
    }

    @PostMapping("/lectura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marcar todas como leídas")
    public void marcarTodasLeidas(@AuthenticationPrincipal Jwt jwt) {
        notificaciones.marcarTodasLeidas(UsuarioActual.de(jwt).id());
    }
}
