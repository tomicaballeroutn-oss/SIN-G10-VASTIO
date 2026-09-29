package ar.edu.utn.vastio.comun;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documento OpenAPI. Swagger UI queda en /api/docs y solo se habilita en el perfil dev.
 * Para probar endpoints protegidos: POST /api/v1/auth/login, copiar {@code tokenAcceso} y usar «Authorize».
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA = "token-acceso";

    @Bean
    OpenAPI vastioOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Vastio API")
                        .version("v1")
                        .description("Agenda de eventos y control de existencias de bebida. "
                                + "Los errores siguen RFC 9457 (ProblemDetail) con las propiedades «codigo» y, en validaciones, «errores»."))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
