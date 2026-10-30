package ar.edu.utn.vastio.usuarios.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ar.edu.utn.vastio.*;
import ar.edu.utn.vastio.usuarios.dominio.*;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

@PruebaDeIntegracion
@Transactional
class CuentaJwtIT {
    @Autowired MockMvc mvc;
    @Autowired Personas personas;
    @Autowired UsuarioRepository usuarios;
    @Autowired JwtEncoder encoder;
    @Autowired EntityManagerFactory emf;
    Usuario usuario;
    static final String CAMBIO = "{\"contrasenaActual\":\"una-clave-larga\",\"contrasenaNueva\":\"nueva-clave-jwt\"}";

    @BeforeEach void datos() { usuario = personas.de(RolCodigo.DIRECCION); usuarios.flush(); }

    String token(String subject, Object marca, String tipo, boolean vencido) {
        Instant ahora = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("vastio")
                .issuedAt(ahora.minusSeconds(300)).expiresAt(ahora.plusSeconds(vencido ? -120 : 900))
                .claim("typ", tipo);
        if (subject != null) claims.subject(subject);
        if ("acceso".equals(tipo)) {
            claims.claim("roles", List.of("DIRECCION", "VENDEDORA"));
            if (marca != null) claims.claim("debeCambiarContrasena", marca);
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    String token(Object marca) { return token(usuario.getId().toString(), marca, "acceso", false); }

    @Test void laMarcaFirmadaInicialRestringeAunqueLaClaveYaSeHayaCambiadoEnOtraSesion() throws Exception {
        mvc.perform(get("/api/v1/_sonda/direccion").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(true)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/cuenta/contrasena").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(true))
                .contentType(MediaType.APPLICATION_JSON).content(CAMBIO)).andExpect(status().isNoContent());
    }

    @Test void laMarcaFalsaPermiteRolesMultiplesSinSentenciasHibernateEnLaSonda() throws Exception {
        // Solo Hibernate: no cuenta JdbcTemplate ni JDBC directo, ni SQL de endpoints de negocio.
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        boolean anterior = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true); stats.clear();
        try {
            String token = token(false);
            for (int i=0; i<3; i++)
                mvc.perform(get("/api/v1/_sonda/direccion").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andExpect(status().isOk());
            System.out.println("CUENTA_JWT_HIBERNATE_STATEMENTS_TRES_SOLICITUDES=" + stats.getPrepareStatementCount());
            assertThat(stats.getPrepareStatementCount()).isZero();
        } finally { stats.setStatisticsEnabled(anterior); }
    }

    @Test void alterarLaMarcaSinVolverAFirmarNoHabilitaLaApi() throws Exception {
        String[] partes = token(true).split("[.]");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(partes[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(payload).contains("\"debeCambiarContrasena\":true");
        payload = payload.replace("\"debeCambiarContrasena\":true", "\"debeCambiarContrasena\":false");
        String adulterado = partes[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + partes[2];
        mvc.perform(get("/api/v1/_sonda/direccion").header(HttpHeaders.AUTHORIZATION, "Bearer " + adulterado))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("SESION_VENCIDA"));
    }

    @Test void unTokenAntiguoSinMarcaDebeRenovarse() throws Exception {
        mvc.perform(get("/api/v1/_sonda/autenticado").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(null)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("SESION_VENCIDA"));
    }

    @ParameterizedTest @ValueSource(strings={"false", "true", ""})
    void laMarcaDebeSerBooleanaNoUnaCadena(String marca) throws Exception {
        mvc.perform(get("/api/v1/_sonda/autenticado").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(marca)))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings={"sin-numero","0","-1","9223372036854775808"})
    void subjectInvalidoSeRechazaAntesDelController(String sub) throws Exception {
        String token = token(sub, false, "acceso", false);
        mvc.perform(get("/api/v1/_sonda/autenticado").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("SESION_VENCIDA"));
        mvc.perform(post("/api/v1/cuenta/contrasena").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(CAMBIO)).andExpect(status().isUnauthorized());
        String refresco = token(sub, null, "refresco", false);
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("vastio_refresco", refresco)))
                .andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(roles="DIRECCION")
    void unPrincipalDePruebaNoPuedeOmitirLaSeguridadNiLlegarSinIdentidadALaCuenta() throws Exception {
        mvc.perform(get("/api/v1/_sonda/direccion")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/cuenta/contrasena").contentType(MediaType.APPLICATION_JSON).content(CAMBIO))
                .andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username="prueba.direccion", roles="DIRECCION")
    void withMockUserPuedeUsarElAdaptadorSinOmitirElDecoder() throws Exception {
        mvc.perform(get("/api/v1/_sonda/direccion").with(personas.jwtDeUsuarioSimulado()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/cuenta/contrasena").with(personas.jwtDeUsuarioSimulado())
                .contentType(MediaType.APPLICATION_JSON).content(CAMBIO))
                .andExpect(status().isNoContent());
    }

    @Test @WithMockUser(username="prueba.direccion", roles="VENDEDORA")
    void elAdaptadorConservaLosRolesSimuladosSinAmpliarlosConLosDelFixture() throws Exception {
        mvc.perform(get("/api/v1/_sonda/direccion").with(personas.jwtDeUsuarioSimulado()))
                .andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username="prueba.direccion", roles="DIRECCION")
    void elAdaptadorConservaLaRestriccionInicialDelFixture() throws Exception {
        usuario.setDebeCambiarContrasena(true); usuarios.saveAndFlush(usuario);
        mvc.perform(get("/api/v1/_sonda/direccion").with(personas.jwtDeUsuarioSimulado()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/cuenta/contrasena").with(personas.jwtDeUsuarioSimulado())
                .contentType(MediaType.APPLICATION_JSON).content(CAMBIO))
                .andExpect(status().isNoContent());
    }

    @Test void sinAutenticacionOVencidoNoHayAcceso() throws Exception {
        mvc.perform(get("/api/v1/_sonda/autenticado")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/_sonda/autenticado").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + token(usuario.getId().toString(), false, "acceso", true))).andExpect(status().isUnauthorized());
    }

    @Test void unRefrescoAntiguoSinMarcaObtieneElEstadoVigente() throws Exception {
        String refresco = token(usuario.getId().toString(), null, "refresco", false);
        usuario.setDebeCambiarContrasena(true); usuarios.saveAndFlush(usuario);
        mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("vastio_refresco", refresco)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usuario.debeCambiarContrasena").value(true));
    }

    @Test void modificarYReactivarNoReactivanLaMarcaDespuesDelPrimerIngreso() throws Exception {
        String token = token(false);
        mvc.perform(put("/api/v1/usuarios/{id}", usuario.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"nombreCompleto":"Dirección editada","roles":["DIRECCION"],"debeCambiarContrasena":true}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.debeCambiarContrasena").value(false));
        usuario.darDeBaja(); usuarios.saveAndFlush(usuario);
        mvc.perform(post("/api/v1/usuarios/{id}/reactivacion", usuario.getId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.debeCambiarContrasena").value(false));
    }
}
