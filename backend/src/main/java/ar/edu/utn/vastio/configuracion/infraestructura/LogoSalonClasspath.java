package ar.edu.utn.vastio.configuracion.infraestructura;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import ar.edu.utn.vastio.configuracion.aplicacion.LogoSalon;

/** Logos de los salones incluidos en la aplicación para documentos generados por el backend. */
@Component
public class LogoSalonClasspath implements LogoSalon {

    private static final Map<String, String> RECURSOS = Map.of(
            "avril", "brand/salones/avril.png",
            "club", "brand/salones/club.png",
            // La fuente recibida es blanca y transparente; los comprobantes se imprimen sobre superficie clara.
            "santa-barbara", "brand/salones/santa-barbara-positivo.png");

    @Override
    public Optional<byte[]> de(String salonCodigo) {
        String ruta = RECURSOS.get(salonCodigo);
        if (ruta == null) return Optional.empty();
        try (var entrada = new ClassPathResource(ruta).getInputStream()) {
            return Optional.of(entrada.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar el logo del salón " + salonCodigo, e);
        }
    }
}
