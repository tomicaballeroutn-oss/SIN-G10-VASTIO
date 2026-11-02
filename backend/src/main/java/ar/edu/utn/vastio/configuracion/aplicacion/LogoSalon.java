package ar.edu.utn.vastio.configuracion.aplicacion;

import java.util.Optional;

/**
 * Logo de cada salón, para los documentos que se imprimen (por ahora, el comprobante de la seña, M-07).
 * <p>
 * Los recursos se buscan por el código estable del salón, nunca por su nombre visible o por el ID de base de datos.
 */
public interface LogoSalon {

    /** La imagen PNG del salón, si hay un logo configurado para ese código. */
    Optional<byte[]> de(String salonCodigo);
}
