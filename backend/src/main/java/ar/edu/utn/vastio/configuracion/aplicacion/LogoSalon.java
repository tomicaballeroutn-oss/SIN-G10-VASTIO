package ar.edu.utn.vastio.configuracion.aplicacion;

import java.util.Optional;

/**
 * Logo de cada salón, para los documentos que se imprimen (por ahora, el comprobante de la seña, M-07).
 * <p>
 * Punto de enchufe para M-04 (logo por salón): hoy no hay ninguna implementación y los documentos ponen el nombre del
 * salón. Cuando exista, alcanza con registrar un {@code @Component} que implemente esta interfaz; quien la usa la pide
 * con {@code ObjectProvider<LogoSalon>} y no hay que cambiar nada más.
 */
public interface LogoSalon {

    /** La imagen del logo (PNG o JPEG) del salón, si tiene. */
    Optional<byte[]> de(short salonId);
}
