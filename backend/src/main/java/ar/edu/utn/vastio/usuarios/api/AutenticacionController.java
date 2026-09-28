package ar.edu.utn.vastio.usuarios.api;

import java.time.Duration;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.comun.errores.ManejadorDeErrores;
import ar.edu.utn.vastio.comun.errores.SesionVencidaException;
import ar.edu.utn.vastio.comun.seguridad.SeguridadProperties;
import ar.edu.utn.vastio.usuarios.aplicacion.AutenticacionService;
import ar.edu.utn.vastio.usuarios.aplicacion.AutenticacionService.Sesion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Sesión", description = "Inicio de sesión, renovación y cierre")
@SecurityRequirements // públicos: no piden token de acceso
public class AutenticacionController {

    static final String COOKIE_REFRESCO = "vastio_refresco";
    private static final String RUTA_COOKIE = "/api/v1/auth";

    private final AutenticacionService autenticacion;
    private final SeguridadProperties propiedades;

    public AutenticacionController(AutenticacionService autenticacion, SeguridadProperties propiedades) {
        this.autenticacion = autenticacion;
        this.propiedades = propiedades;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Devuelve el token de acceso y deja el de refresco en una cookie HttpOnly.")
    @ApiResponse(responseCode = "200", description = "Sesión iniciada")
    @ApiResponse(responseCode = "401", description = "Usuario o contraseña incorrectos, o usuario dado de baja")
    public ResponseEntity<SesionResponse> login(@Valid @RequestBody LoginRequest pedido) {
        return conCookie(autenticacion.iniciarSesion(pedido.nombreUsuario(), pedido.contrasena()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar la sesión", description = "Usa la cookie de refresco; emite un token de acceso nuevo y rota la cookie.")
    @ApiResponse(responseCode = "200", description = "Sesión renovada")
    @ApiResponse(responseCode = "401", description = "Sesión vencida: hay que volver a ingresar")
    public ResponseEntity<SesionResponse> refresh(
            @CookieValue(name = COOKIE_REFRESCO, required = false) String tokenRefresco) {
        return conCookie(autenticacion.renovar(tokenRefresco));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar sesión", description = "Borra la cookie de refresco.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    /** Sesión vencida al refrescar: además del 401, se borra la cookie. */
    @ExceptionHandler(SesionVencidaException.class)
    ResponseEntity<ProblemDetail> sesionVencida() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .body(ManejadorDeErrores.sesionVencida());
    }

    private ResponseEntity<SesionResponse> conCookie(Sesion sesion) {
        ResponseCookie cookie = cookie(sesion.refresco().valor(), sesion.refresco().vida());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(SesionResponse.de(sesion));
    }

    private ResponseCookie cookie(String valor, Duration vida) {
        return ResponseCookie.from(COOKIE_REFRESCO, valor)
                .httpOnly(true)
                .secure(propiedades.cookieSegura())
                .sameSite("Strict")
                .path(RUTA_COOKIE)
                .maxAge(vida)
                .build();
    }
}
