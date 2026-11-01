package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.jayway.jsonpath.JsonPath;
import ar.edu.utn.vastio.*;
import ar.edu.utn.vastio.usuarios.dominio.*;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

@PruebaDeIntegracion
@Transactional
class BloqueoValidacionIT {
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired Personas personas;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;
    String bearer;
    static final String CUERPO = "{\"salonIds\":[1],\"desde\":\"2055-02-01\",\"hasta\":null,\"turnoIds\":[2],\"motivoId\":7,\"detalle\":null}";

    String sesion(String nombre, RolCodigo... roles) {
        Usuario u = personas.usuario(nombre, nombre, roles);
        u.setDebeCambiarContrasena(false);
        usuarios.save(u);
        return personas.bearer(u);
    }

    @BeforeEach void datos() {
        bearer = sesion("audit50.validacion", RolCodigo.ADMINISTRACION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"salonIds\":[1]", "\"turnoIds\":[2]"})
    void listasVaciasONulasYElementosNulosDan400(String original) throws Exception {
        String campo = original.substring(0, original.indexOf(':'));
        for (String valor : new String[]{"[]", "null", "[null]", "[1,null]"}) {
            mvc.perform(post("/api/v1/bloqueos").header(HttpHeaders.AUTHORIZATION, bearer)
                    .contentType(MediaType.APPLICATION_JSON).content(CUERPO.replace(original, campo + ":" + valor)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        }
    }

    @Test void valoresNulosYJsonNuloNoLleganAlServicio() throws Exception {
        for (String cuerpo : new String[]{"null", CUERPO.replace("\"desde\":\"2055-02-01\"", "\"desde\":null"),
                CUERPO.replace("\"motivoId\":7", "\"motivoId\":null")}) {
            mvc.perform(post("/api/v1/bloqueos").header(HttpHeaders.AUTHORIZATION, bearer)
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest());
        }
    }

    @Test void idsRepetidosSeNormalizanYElDetalleAdmite255() throws Exception {
        String cuerpo = CUERPO.replace("[1]", "[2,1,2,1]").replace("[2]", "[2,1,2]")
                .replace("\"hasta\":null", "\"hasta\":\"2055-02-03\"")
                .replace("\"detalle\":null", "\"detalle\":\"" + "x".repeat(255) + "\"");
        String respuesta = mvc.perform(post("/api/v1/bloqueos").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidadUnidades").value(12)).andReturn().getResponse().getContentAsString();
        long id = JsonPath.parse(respuesta).read("$.id", Long.class);
        em.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo_unidad WHERE bloqueo_id=?", Integer.class, id)).isEqualTo(12);
    }

    @ParameterizedTest
    @EnumSource(value=RolCodigo.class, names={"DIRECCION","COORDINACION","ADMINISTRACION"})
    void losTresPerfilesPuedenConsultarCrearYDesbloquear(RolCodigo rol) throws Exception {
        String token = sesion("audit50." + rol.name().toLowerCase(java.util.Locale.ROOT), rol);
        String respuesta = mvc.perform(post("/api/v1/bloqueos").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = JsonPath.parse(respuesta).read("$.id", Long.class);
        mvc.perform(get("/api/v1/bloqueos").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/bloqueos/{id}/desbloqueo", id).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isNoContent());
    }

    @Test void sinTokenLasTresOperacionesSon401() throws Exception {
        mvc.perform(get("/api/v1/bloqueos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/bloqueos").contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/bloqueos/1/desbloqueo")).andExpect(status().isUnauthorized());
    }

    @Test void variosRolesMantienenLaUnionDePermisosSinConcederAccesoALosDemas() throws Exception {
        String denegado = sesion("audit50.multiple.no", RolCodigo.VENDEDORA, RolCodigo.PLANNER);
        mvc.perform(get("/api/v1/bloqueos").header(HttpHeaders.AUTHORIZATION, denegado)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/bloqueos").header(HttpHeaders.AUTHORIZATION, denegado)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/bloqueos/1/desbloqueo").header(HttpHeaders.AUTHORIZATION, denegado)).andExpect(status().isForbidden());
        String permitido = sesion("audit50.multiple.si", RolCodigo.VENDEDORA, RolCodigo.ADMINISTRACION);
        String respuesta = mvc.perform(post("/api/v1/bloqueos").header(HttpHeaders.AUTHORIZATION, permitido)
                .contentType(MediaType.APPLICATION_JSON).content(CUERPO)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = JsonPath.parse(respuesta).read("$.id", Long.class);
        mvc.perform(get("/api/v1/bloqueos").header(HttpHeaders.AUTHORIZATION, permitido)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/bloqueos/{id}/desbloqueo", id).header(HttpHeaders.AUTHORIZATION, permitido)).andExpect(status().isNoContent());
    }
}
