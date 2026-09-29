package ar.edu.utn.vastio.usuarios.api;

import java.time.Instant;
import java.util.List;

import ar.edu.utn.vastio.usuarios.aplicacion.AutenticacionService.Sesion;

/**
 * Respuesta de login y refresh. El token de refresco no viaja acá: va en la cookie HttpOnly.
 *
 * @param minutosExpiracionSesion inactividad tolerada antes de pedir volver a ingresar (UI-02).
 * @param minutosAvisoExpiracion  anticipación del aviso «Tu sesión va a expirar pronto».
 */
public record SesionResponse(
        String tokenAcceso,
        Instant tokenAccesoVence,
        int minutosExpiracionSesion,
        int minutosAvisoExpiracion,
        UsuarioResponse usuario) {

    public record UsuarioResponse(long id, String nombreCompleto, String nombreUsuario, List<String> roles,
            boolean debeCambiarContrasena) {
    }

    static SesionResponse de(Sesion sesion) {
        var u = sesion.usuario();
        return new SesionResponse(
                sesion.acceso().valor(),
                sesion.acceso().vence(),
                sesion.minutosExpiracionSesion(),
                sesion.minutosAvisoExpiracion(),
                new UsuarioResponse(u.id(), u.nombreCompleto(), u.nombreUsuario(), u.roles(), u.debeCambiarContrasena()));
    }
}
