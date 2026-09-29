package ar.edu.utn.vastio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

/**
 * Tareas 7 y 9 del Sprint 0: usuario inicial de Dirección y Swagger UI, ambos solo en dev.
 */
@SpringBootTest(properties = {
        "vastio.usuario-inicial.usuario=direccion.test",
        "vastio.usuario-inicial.contrasena=clave-inicial-de-prueba"
})
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@Import(TestcontainersConfig.class)
class PerfilDevIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    UsuarioRepository usuarios;

    @Test
    void alArrancarCreaElUsuarioInicialDeDireccion() {
        var usuario = usuarios.findByNombreUsuario("direccion.test").orElseThrow();

        assertThat(usuario.codigosDeRol()).containsExactly(RolCodigo.DIRECCION);
        assertThat(usuario.getHashContrasena()).startsWith("$2").doesNotContain("clave-inicial-de-prueba");
    }

    @Test
    void conElUsuarioInicialSePuedeIniciarSesion() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreUsuario\":\"direccion.test\",\"contrasena\":\"clave-inicial-de-prueba\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.roles[0]").value("DIRECCION"));
    }

    @Test
    void elDocumentoOpenApiEstaPublicadoEnDev() throws Exception {
        mvc.perform(get("/api/docs/openapi.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Vastio API"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists());
    }

    @Test
    void swaggerUiEstaEnApiDocs() throws Exception {
        mvc.perform(get("/api/docs"))
                .andExpect(status().is3xxRedirection());
    }
}
