package ar.edu.utn.vastio.comun.seguridad;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

import ar.edu.utn.vastio.comun.errores.SesionVencidaException;

/**
 * API sin estado con JWT. Las rutas de /api/v1/auth son públicas; el resto exige token de acceso.
 * La autorización por perfil va en cada endpoint con {@code @PreAuthorize("hasRole('...')")}.
 * Los 401 y 403 pasan por {@link ar.edu.utn.vastio.comun.errores.ManejadorDeErrores} para salir como ProblemDetail.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SeguridadProperties.class)
public class SeguridadConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver errores) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // sin sesión de servidor; la cookie de refresco es SameSite=Strict
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/actuator/health/**", "/api/docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/cuenta/contrasena")
                        .access((autenticacion, contexto) -> new AuthorizationDecision(
                                autenticacion.get().getPrincipal() instanceof Jwt))
                        .anyRequest().access((autenticacion, contexto) -> {
                            // La marca firmada se emite desde la base al iniciar o renovar la sesión.
                            var principal = autenticacion.get().getPrincipal();
                            return new AuthorizationDecision(principal instanceof Jwt jwt
                                    && Boolean.FALSE.equals(jwt.getClaims().get(TokenService.CLAIM_CAMBIO_OBLIGATORIO)));
                        }))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(convertidorDeRoles()))
                        .authenticationEntryPoint((req, res, ex) -> errores.resolveException(req, res, null, ex))
                        .accessDeniedHandler((req, res, ex) -> errores.resolveException(req, res, null, ex)))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> errores.resolveException(req, res, null, ex))
                        .accessDeniedHandler((req, res, ex) -> errores.resolveException(req, res, null, ex)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(SeguridadProperties propiedades) {
        return NimbusJwtEncoder.withSecretKey(clave(propiedades)).build();
    }

    /** Decodifica tokens de acceso. Es el que usa el resource server. */
    @Bean
    @Primary
    JwtDecoder jwtDecoder(SeguridadProperties propiedades) {
        return decoder(propiedades, TokenService.TIPO_ACCESO);
    }

    /** Decodifica tokens de refresco; solo lo usa {@link TokenService}. */
    @Bean
    JwtDecoder refrescoDecoder(SeguridadProperties propiedades) {
        return decoder(propiedades, TokenService.TIPO_REFRESCO);
    }

    private static JwtDecoder decoder(SeguridadProperties propiedades, String tipo) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(clave(propiedades)).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> delTipo = jwt -> tipo.equals(jwt.getClaims().get(TokenService.CLAIM_TIPO))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Tipo de token incorrecto", null));
        OAuth2Error invalida = new OAuth2Error("invalid_token", "Claims de sesión inválidos", null);
        OAuth2TokenValidator<Jwt> deLaSesion = jwt -> {
            try {
                TokenService.usuarioId(jwt);
                if (TokenService.TIPO_ACCESO.equals(tipo)
                        && !(jwt.getClaims().get(TokenService.CLAIM_CAMBIO_OBLIGATORIO) instanceof Boolean)) {
                    // Un access token anterior al claim debe pasar por refresh; nunca se asume false.
                    return OAuth2TokenValidatorResult.failure(invalida);
                }
                return OAuth2TokenValidatorResult.success();
            } catch (SesionVencidaException e) {
                return OAuth2TokenValidatorResult.failure(invalida);
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("vastio"), delTipo, deLaSesion));
        return decoder;
    }

    private static JwtAuthenticationConverter convertidorDeRoles() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(TokenService.CLAIM_ROLES);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(roles);
        return convertidor;
    }

    private static SecretKey clave(SeguridadProperties propiedades) {
        return new SecretKeySpec(propiedades.jwtSecreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
