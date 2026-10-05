package ar.edu.utn.vastio.usuarios.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

/**
 * Alta mínima de usuarios (UI-05).
 */
@PruebaDeIntegracion
@Transactional
class UsuarioIT {

    private static final String ALTA_LUCIA = """
            {"nombreCompleto":" Lucía Ferreyra ","nombreUsuario":"lucia.alta","roles":["VENDEDORA","PLANNER"],
             "email":"lucia@salonavril.com","telefono":"","contrasenaInicial":"bienvenida-2026"}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void coordinacionDaDeAltaUnUsuarioQueDebeCambiarLaContrasena() throws Exception {
        alta(RolCodigo.COORDINACION, ALTA_LUCIA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombreCompleto").value("Lucía Ferreyra"))
                .andExpect(jsonPath("$.roles[0]").value("VENDEDORA"))
                .andExpect(jsonPath("$.roles[1]").value("PLANNER"))
                .andExpect(jsonPath("$.telefono").doesNotExist())
                .andExpect(jsonPath("$.debeCambiarContrasena").value(true))
                .andExpect(jsonPath("$.hashContrasena").doesNotExist());

        Usuario lucia = usuarios.findByNombreUsuario("lucia.alta").orElseThrow();
        assertThat(passwordEncoder.matches("bienvenida-2026", lucia.getHashContrasena())).isTrue();
    }

    @Test
    void elNombreDeUsuarioNoSeRepite() throws Exception {
        alta(RolCodigo.DIRECCION, ALTA_LUCIA).andExpect(status().isCreated());
        alta(RolCodigo.DIRECCION, ALTA_LUCIA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USUARIO_REPETIDO"));
    }

    @Test
    void validaUsuarioPerfilesYContrasena() throws Exception {
        alta(RolCodigo.DIRECCION, """
                {"nombreCompleto":"Ana Sosa","nombreUsuario":"Ana Sosa","roles":[],"email":"no-es-correo","contrasenaInicial":"corta"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("nombreUsuario")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("roles")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("email")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("contrasenaInicial")));
    }

    @Test
    void laBajaImpideIngresarYSacaAlUsuarioDeLasListasParaElegir() throws Exception {
        Usuario melina = personas.usuario("melina.vend", "Melina Ruiz", RolCodigo.VENDEDORA);
        String direccion = personas.bearer(RolCodigo.DIRECCION);

        mvc.perform(post("/api/v1/usuarios/{id}/baja", melina.getId()).header(HttpHeaders.AUTHORIZATION, direccion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.fechaBaja").exists());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreUsuario\":\"melina.vend\",\"contrasena\":\"" + Personas.CONTRASENA + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/usuarios/personas?perfil=VENDEDORA").header(HttpHeaders.AUTHORIZATION, direccion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombreCompleto", not(hasItem("Melina Ruiz"))));
    }

    @Test
    void nadieSeDaDeBajaASiMismo() throws Exception {
        Usuario yo = personas.de(RolCodigo.COORDINACION);

        mvc.perform(post("/api/v1/usuarios/{id}/baja", yo.getId()).header(HttpHeaders.AUTHORIZATION, personas.bearer(yo)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("BAJA_PROPIA"));
    }

    @Test
    void listaLasVendedorasActivasParaElegir() throws Exception {
        personas.usuario("lucia.vend", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        personas.usuario("ana.planner", "Ana Sosa", RolCodigo.PLANNER);

        mvc.perform(get("/api/v1/usuarios/personas?perfil=VENDEDORA").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COORDINACION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombreCompleto", hasItem("Lucía Ferreyra")))
                .andExpect(jsonPath("$[*].nombreCompleto", not(hasItem("Ana Sosa"))))
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "VENDEDORA", "PLANNER", "COMPRAS", "BARRA", "COCINA"})
    void soloDireccionYCoordinacionAdministranUsuarios(RolCodigo rol) throws Exception {
        String bearer = personas.bearer(rol);
        mvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isForbidden());
        alta(rol, ALTA_LUCIA).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/usuarios/1/baja").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isForbidden());
    }

    @Test
    void direccionListaLosUsuarios() throws Exception {
        personas.usuario("lucia.vend", "Lucía Ferreyra", RolCodigo.VENDEDORA);

        mvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.DIRECCION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombreUsuario", hasItem("lucia.vend")))
                .andExpect(jsonPath("$[0].hashContrasena").doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions alta(RolCodigo rol, String json) throws Exception {
        return mvc.perform(post("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, personas.bearer(rol))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
