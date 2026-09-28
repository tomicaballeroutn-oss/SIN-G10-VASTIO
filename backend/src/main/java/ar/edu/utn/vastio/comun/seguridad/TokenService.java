package ar.edu.utn.vastio.comun.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import ar.edu.utn.vastio.comun.errores.SesionVencidaException;

/**
 * Emite y lee los dos tokens de la sesión. Ambos son JWT HS256 y se distinguen por el claim {@code typ}:
 * <ul>
 * <li>acceso: vida corta, viaja en {@code Authorization: Bearer} y el frontend lo guarda solo en memoria.</li>
 * <li>refresco: vida = MINUTOS_EXPIRACION_SESION, viaja en una cookie HttpOnly y solo sirve en /api/v1/auth.</li>
 * </ul>
 * No hay tabla de sesiones (ver diccionario de datos): la baja de un usuario se controla al refrescar.
 */
@Service
public class TokenService {

    static final String CLAIM_TIPO = "typ";
    static final String CLAIM_ROLES = "roles";
    static final String CLAIM_NOMBRE_USUARIO = "usuario";
    static final String TIPO_ACCESO = "acceso";
    static final String TIPO_REFRESCO = "refresco";

    private final JwtEncoder encoder;
    private final JwtDecoder refrescoDecoder;
    private final SeguridadProperties propiedades;

    public TokenService(JwtEncoder encoder, @Qualifier("refrescoDecoder") JwtDecoder refrescoDecoder,
            SeguridadProperties propiedades) {
        this.encoder = encoder;
        this.refrescoDecoder = refrescoDecoder;
        this.propiedades = propiedades;
    }

    public TokenEmitido emitirAcceso(long usuarioId, String nombreUsuario, Collection<String> roles) {
        Duration vida = Duration.ofMinutes(propiedades.minutosAcceso());
        return emitir(usuarioId, TIPO_ACCESO, vida, claims -> claims
                .claim(CLAIM_NOMBRE_USUARIO, nombreUsuario)
                .claim(CLAIM_ROLES, List.copyOf(roles)));
    }

    public TokenEmitido emitirRefresco(long usuarioId, Duration vida) {
        return emitir(usuarioId, TIPO_REFRESCO, vida, claims -> claims);
    }

    /**
     * @return el id del usuario del token de refresco.
     * @throws SesionVencidaException si falta, venció, está adulterado o es de otro tipo.
     */
    public long leerRefresco(String token) {
        if (token == null || token.isBlank()) {
            throw new SesionVencidaException("Sin token de refresco");
        }
        try {
            Jwt jwt = refrescoDecoder.decode(token);
            return Long.parseLong(jwt.getSubject());
        } catch (JwtException | NumberFormatException e) {
            throw new SesionVencidaException("Token de refresco inválido: " + e.getMessage());
        }
    }

    private TokenEmitido emitir(long usuarioId, String tipo, Duration vida,
            UnaryOperator<JwtClaimsSet.Builder> extra) {
        Instant ahora = Instant.now();
        Instant vence = ahora.plus(vida);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("vastio")
                .subject(Long.toString(usuarioId))
                .issuedAt(ahora)
                .expiresAt(vence)
                .claim(CLAIM_TIPO, tipo);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String valor = encoder.encode(JwtEncoderParameters.from(header, extra.apply(claims).build())).getTokenValue();
        return new TokenEmitido(valor, vence, vida);
    }

    public record TokenEmitido(String valor, Instant vence, Duration vida) {
    }
}
