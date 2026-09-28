package ar.edu.utn.vastio.comun.seguridad;

import java.nio.charset.StandardCharsets;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de sesión ({@code vastio.seguridad.*}).
 *
 * @param jwtSecreto    clave HS256 para firmar los tokens; al menos 32 bytes. En producción, variable {@code VASTIO_JWT_SECRETO}.
 * @param minutosAcceso vida del token de acceso. La del token de refresco sale del parámetro MINUTOS_EXPIRACION_SESION.
 * @param cookieSegura  marca la cookie de refresco como {@code Secure}. Solo se apaga en tests con MockMvc.
 */
@Validated
@ConfigurationProperties("vastio.seguridad")
public record SeguridadProperties(
        @NotBlank(message = "Falta VASTIO_JWT_SECRETO") String jwtSecreto,
        @Min(1) int minutosAcceso,
        boolean cookieSegura) {

    @AssertTrue(message = "VASTIO_JWT_SECRETO tiene que tener al menos 32 caracteres")
    public boolean isSecretoSuficiente() {
        return jwtSecreto == null || jwtSecreto.getBytes(StandardCharsets.UTF_8).length >= 32;
    }
}
