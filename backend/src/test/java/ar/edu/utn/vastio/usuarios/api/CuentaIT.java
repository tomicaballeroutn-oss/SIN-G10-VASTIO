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
