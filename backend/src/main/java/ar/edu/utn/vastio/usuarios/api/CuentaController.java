package ar.edu.utn.vastio.usuarios.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.aplicacion.AutenticacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Mi cuenta (UI-03). Todos los perfiles; cada persona administra solo la suya: el usuario sale del token,
 * nunca de la ruta ni del cuerpo.
 */
@RestController
@RequestMapping("/api/v1/cuenta")
@Tag(name = "Cuenta", description = "Mi cuenta: la persona cambia su propia contraseña")
public class CuentaController {

    private final AutenticacionService autenticacion;

    public CuentaController(AutenticacionService autenticacion) {
        this.autenticacion = autenticacion;
    }

    @PostMapping("/contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cambiar mi contraseña", description = """
            Pide la contraseña actual y una nueva de 8 a 72 caracteres y hasta 72 bytes UTF-8 (BCrypt), distinta de la actual.
            Quita la marca «debe cambiar la contraseña» del usuario. Las sesiones ya abiertas siguen valiendo hasta que venzan.""")
    @ApiResponse(responseCode = "204", description = "Contraseña cambiada")
    @ApiResponse(responseCode = "400", description = "Falta algún dato o la nueva no tiene entre 8 y 72 caracteres")
    @ApiResponse(responseCode = "422", description = "La contraseña actual no es correcta, la nueva es igual a la actual o supera 72 bytes UTF-8")
    public void cambiarContrasena(@Valid @RequestBody CambioContrasenaRequest pedido, @AuthenticationPrincipal Jwt jwt) {
        autenticacion.cambiarContrasena(UsuarioActual.de(jwt).id(), pedido.contrasenaActual(), pedido.contrasenaNueva());
    }

    public record CambioContrasenaRequest(
            @NotBlank(message = "Escribí tu contraseña actual.")
            @Size(max = 200, message = "Usá hasta 200 caracteres.")
            String contrasenaActual,
            @NotBlank(message = "Escribí la contraseña nueva.")
            @Size(min = 8, max = 72, message = "La contraseña nueva tiene que tener entre 8 y 72 caracteres.")
            String contrasenaNueva) {
    }
}
