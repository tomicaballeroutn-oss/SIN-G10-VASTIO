package ar.edu.utn.vastio.comun.errores;

import org.springframework.security.core.AuthenticationException;

/**
 * El token de refresco falta, venció, es inválido o su usuario fue dado de baja: hay que volver a ingresar.
 */
public class SesionVencidaException extends AuthenticationException {

    public SesionVencidaException(String motivo) {
        super(motivo);
    }
}
