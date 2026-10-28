package ar.edu.utn.vastio.usuarios.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

/**
 * Mi cuenta (UI-03): cambio de contraseña de la propia persona.
 */
@PruebaDeIntegracion
@Transactional
class CuentaIT {

    private static final String NUEVA = "otra-clave-larga";

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    EntityManager entityManager;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(post("/api/v1/cuenta/contrasena").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(Personas.CONTRASENA, NUEVA)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SIN_SESION"));
    }

    @ParameterizedTest
    @EnumSource(RolCodigo.class)
    void cualquierPerfilCambiaSuContrasena(RolCodigo rol) throws Exception {
        Usuario usuario = personas.de(rol);
        usuario.setDebeCambiarContrasena(true);

        cambiar(usuario, Personas.CONTRASENA, NUEVA).andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        Usuario guardado = usuarios.findById(usuario.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(NUEVA, guardado.getHashContrasena())).isTrue();
        assertThat(passwordEncoder.matches(Personas.CONTRASENA, guardado.getHashContrasena())).isFalse();
        assertThat(guardado.isDebeCambiarContrasena()).isFalse();
    }

    @Test
    void despuesDelCambioIngresaConLaNuevaYNoConLaVieja() throws Exception {
        Usuario usuario = personas.de(RolCodigo.VENDEDORA);
        cambiar(usuario, Personas.CONTRASENA, NUEVA).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        mvc.perform(login(usuario.getNombreUsuario(), NUEVA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.debeCambiarContrasena").value(false));
        mvc.perform(login(usuario.getNombreUsuario(), Personas.CONTRASENA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void conLaActualIncorrectaNoCambiaNada() throws Exception {
        Usuario usuario = personas.de(RolCodigo.COMPRAS);
        usuario.setDebeCambiarContrasena(true);
        String hashAntes = usuario.getHashContrasena();

        cambiar(usuario, "no-es-esta", NUEVA)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_ACTUAL_INCORRECTA"));

        entityManager.flush();
        entityManager.clear();
        Usuario guardado = usuarios.findById(usuario.getId()).orElseThrow();
        assertThat(guardado.getHashContrasena()).isEqualTo(hashAntes);
        assertThat(guardado.isDebeCambiarContrasena()).isTrue();
    }

    @Test
    void laNuevaNoPuedeSerIgualALaActual() throws Exception {
        cambiar(personas.de(RolCodigo.BARRA), Personas.CONTRASENA, Personas.CONTRASENA)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_REPETIDA"));
    }

    @Test
    void laNuevaDebeTenerEntre8y72Caracteres() throws Exception {
        Usuario usuario = personas.de(RolCodigo.PLANNER);

        cambiar(usuario, Personas.CONTRASENA, "corta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores[?(@.campo == 'contrasenaNueva')].mensaje")
                        .value(hasItem("La contraseña nueva tiene que tener entre 8 y 72 caracteres.")));
        cambiar(usuario, Personas.CONTRASENA, "x".repeat(73)).andExpect(status().isBadRequest());
    }

    @Test
    void faltandoDatosMarcaLosCampos() throws Exception {
        mvc.perform(post("/api/v1/cuenta/contrasena").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COCINA))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[?(@.campo == 'contrasenaActual')].mensaje").value(hasItem("Escribí tu contraseña actual.")))
                .andExpect(jsonPath("$.errores[?(@.campo == 'contrasenaNueva')].mensaje").value(hasItem("Escribí la contraseña nueva.")));
    }

    @Test
    void unUsuarioDadoDeBajaNoCambiaLaContrasena() throws Exception {
        Usuario usuario = personas.usuario("ex.empleada", "Ex Empleada", RolCodigo.BARRA);
        usuario.darDeBaja();
        usuarios.save(usuario);

        cambiar(usuario, Personas.CONTRASENA, NUEVA)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("USUARIO_INACTIVO"));
    }


    @ParameterizedTest
    @EnumSource(RolCodigo.class)
    void laContrasenaInicialBloqueaLaApiHastaCambiarlaInclusoConElMismoToken(RolCodigo rol) throws Exception {
        Usuario usuario = personas.de(rol);
        usuario.setDebeCambiarContrasena(true);
        String token = personas.bearer(usuario);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/_sonda/autenticado")
                .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());

        cambiar(usuario, Personas.CONTRASENA, NUEVA).andExpect(status().isNoContent());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/_sonda/autenticado")
                .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
    }

    @Test
    void variosRolesNoEvitanElCambioYElRefrescoLeeElEstadoActual() throws Exception {
        Usuario usuario = personas.usuario("inicial.multiperfil", "Primer ingreso", RolCodigo.DIRECCION, RolCodigo.COMPRAS);
        usuario.setDebeCambiarContrasena(true);
        var ingreso = mvc.perform(login(usuario.getNombreUsuario(), Personas.CONTRASENA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.debeCambiarContrasena").value(true)).andReturn();
        var cookie = ingreso.getResponse().getCookie(AutenticacionController.COOKIE_REFRESCO);
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.debeCambiarContrasena").value(true));
        mvc.perform(post("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, personas.bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        cambiar(usuario, Personas.CONTRASENA, NUEVA).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.debeCambiarContrasena").value(false));
        mvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andExpect(status().isNoContent());
    }

    @Test
    void unIdEnElCuerpoNoPermiteCambiarOtraCuenta() throws Exception {
        Usuario propia = personas.de(RolCodigo.VENDEDORA);
        Usuario ajena = personas.de(RolCodigo.DIRECCION);
        String hashAjeno = ajena.getHashContrasena();
        mvc.perform(post("/api/v1/cuenta/contrasena")
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(propia))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":%d,\"contrasenaActual\":\"%s\",\"contrasenaNueva\":\"%s\"}"
                        .formatted(ajena.getId(), Personas.CONTRASENA, NUEVA)))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertThat(usuarios.findById(ajena.getId()).orElseThrow().getHashContrasena()).isEqualTo(hashAjeno);
        assertThat(passwordEncoder.matches(NUEVA, usuarios.findById(propia.getId()).orElseThrow().getHashContrasena())).isTrue();
    }

    @Test
    void respetaElLimiteRealDeBcryptConUnicodeSinResponder500() throws Exception {
        Usuario usuario = personas.de(RolCodigo.BARRA);
        String hash = usuario.getHashContrasena();
        cambiar(usuario, Personas.CONTRASENA, "á".repeat(37))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_DEMASIADO_LARGA"));
        assertThat(usuarios.findById(usuario.getId()).orElseThrow().getHashContrasena()).isEqualTo(hash);
        cambiar(usuario, Personas.CONTRASENA, "á".repeat(36)).andExpect(status().isNoContent());
        assertThat(passwordEncoder.matches("á".repeat(36), usuario.getHashContrasena())).isTrue();
    }

    private ResultActions cambiar(Usuario usuario, String actual, String nueva) throws Exception {
        return mvc.perform(post("/api/v1/cuenta/contrasena")
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(actual, nueva)));
    }

    private static String cuerpo(String actual, String nueva) {
        return "{\"contrasenaActual\":\"%s\",\"contrasenaNueva\":\"%s\"}".formatted(actual, nueva);
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String usuario, String contrasena) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreUsuario\":\"%s\",\"contrasena\":\"%s\"}".formatted(usuario, contrasena));
    }
}
