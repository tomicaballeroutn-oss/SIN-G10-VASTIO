package ar.edu.utn.vastio.usuarios.api;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Administrar usuarios (UI-05): modificar, reactivar, protecciones y eventos afectados por una baja.
 */
@PruebaDeIntegracion
@Transactional
class AdministrarUsuariosIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    Escenario escenario;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    Usuario melina;
    Usuario ana;

    @BeforeEach
    void personas() {
        melina = personas.usuario("melina.admin", "Melina Sifón", RolCodigo.COORDINACION);
        ana = personas.usuario("ana.admin", "Ana Sosa", RolCodigo.PLANNER);
    }

    @Test
    void coordinacionModificaNombrePerfilesYContactoPeroNoElUsuario() throws Exception {
        modificar(melina, ana, """
                {"nombreCompleto":" Ana María Sosa ","nombreUsuario":"otro.nombre","roles":["VENDEDORA","PLANNER"],
                 "email":"ana@salonavril.com","telefono":" "}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("Ana María Sosa"))
                .andExpect(jsonPath("$.nombreUsuario").value("ana.admin"))
                .andExpect(jsonPath("$.roles", contains("VENDEDORA", "PLANNER")))
                .andExpect(jsonPath("$.email").value("ana@salonavril.com"))
                .andExpect(jsonPath("$.telefono").doesNotExist());

        modificar(melina, ana, """
                {"nombreCompleto":"Ana María Sosa","roles":["VENDEDORA"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("VENDEDORA")));
        entityManager.flush();
        entityManager.clear();
        mvc.perform(get("/api/v1/usuarios/personas").param("perfil", "PLANNER").header(HttpHeaders.AUTHORIZATION, personas.bearer(melina)))
                .andExpect(jsonPath("$[*].id", not(hasItem(ana.getId().intValue()))));
    }

    @Test
    void validaLaModificacion() throws Exception {
        modificar(melina, ana, """
                {"nombreCompleto":"","roles":[],"email":"no-es-correo"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", containsInAnyOrder("nombreCompleto", "roles", "email")));
        mvc.perform(put("/api/v1/usuarios/{id}", 999999).header(HttpHeaders.AUTHORIZATION, personas.bearer(melina))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombreCompleto":"Nadie","roles":["PLANNER"]}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void nadieSeQuitaASiMismoElAccesoTotal() throws Exception {
        modificar(melina, melina, """
                {"nombreCompleto":"Melina Sifón","roles":["VENDEDORA"]}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PERFIL_PROPIO"));
        modificar(melina, melina, """
                {"nombreCompleto":"Melina Sifón","roles":["COORDINACION","VENDEDORA"]}""")
                .andExpect(status().isOk());
    }

    @Test
    void siempreQuedaUnUsuarioActivoDeDireccion() throws Exception {
        Usuario duenio = personas.usuario("duenio.admin", "Roberto Díaz", RolCodigo.DIRECCION);
        entityManager.flush();
        // Solo él queda activo con Dirección (la base de tests puede tener otros: los del perfil dev).
        jdbc.update("""
                UPDATE usuario SET activo = false WHERE usuario_id <> ? AND usuario_id IN
                  (SELECT ur.usuario_id FROM usuario_rol ur JOIN rol r USING (rol_id) WHERE r.codigo = 'DIRECCION')""", duenio.getId());
        entityManager.clear();

        baja(melina, duenio)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ULTIMA_DIRECCION"))
                .andExpect(jsonPath("$.detail").value(startsWith("Roberto Díaz es el único usuario activo de Dirección")));
        modificar(melina, duenio, """
                {"nombreCompleto":"Roberto Díaz","roles":["COMPRAS"]}""")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ULTIMA_DIRECCION"));

        Usuario hijo = personas.usuario("hijo.admin", "Martín Díaz", RolCodigo.DIRECCION);
        baja(melina, duenio).andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        baja(melina, hijo).andExpect(status().isUnprocessableContent());
    }

    @Test
    void unUsuarioDadoDeBajaSeReactivaYVuelveAIngresar() throws Exception {
        baja(melina, ana).andExpect(status().isOk());
        login("ana.admin").andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/usuarios/{id}/reactivacion", ana.getId()).header(HttpHeaders.AUTHORIZATION, personas.bearer(melina)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.fechaBaja").doesNotExist());
        entityManager.flush();

        login("ana.admin").andExpect(status().isOk());
        mvc.perform(get("/api/v1/usuarios/personas").param("perfil", "PLANNER").header(HttpHeaders.AUTHORIZATION, personas.bearer(melina)))
                .andExpect(jsonPath("$[*].id", hasItem(ana.getId().intValue())));
    }

    @Test
    void antesDeLaBajaSeVenLosEventosActivosDeLaPersona() throws Exception {
        Usuario lucia = personas.usuario("lucia.admin", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        LocalDate fecha = LocalDate.now().plusMonths(3);
        escenario.evento("avril", fecha, "noche", "CONTRATADO", lucia, ana, "Bruno y Martina");
        escenario.evento("club", fecha, "noche", "SENADO", lucia, "Quince de Delfina");
        escenario.evento("club", fecha, "mediodia", "CANCELADO", lucia, ana, "Cancelado");
        escenario.evento("avril", LocalDate.now().minusDays(3), "noche", "CONFIRMADO", lucia, ana, "Ya pasó");

        afectados(ana).andExpect(status().isOk()).andExpect(jsonPath("$[*].nombre", contains("Bruno y Martina")));
        afectados(lucia).andExpect(jsonPath("$[*].nombre", containsInAnyOrder("Bruno y Martina", "Quince de Delfina")));
        // La baja se permite igual: la pantalla avisa antes.
        baja(melina, ana).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "VENDEDORA", "PLANNER", "COMPRAS", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoAdministraUsuarios(RolCodigo rol) throws Exception {
        Usuario quien = personas.de(rol);
        modificar(quien, ana, """
                {"nombreCompleto":"Ana Sosa","roles":["PLANNER"]}""").andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/usuarios/{id}/reactivacion", ana.getId()).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)))
                .andExpect(status().isForbidden());
        afectados(quien, ana).andExpect(status().isForbidden());
    }

    private ResultActions modificar(Usuario quien, Usuario usuario, String json) throws Exception {
        return mvc.perform(put("/api/v1/usuarios/{id}", usuario.getId()).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions baja(Usuario quien, Usuario usuario) throws Exception {
        return mvc.perform(post("/api/v1/usuarios/{id}/baja", usuario.getId()).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }

    private ResultActions afectados(Usuario usuario) throws Exception {
        return afectados(melina, usuario);
    }

    private ResultActions afectados(Usuario quien, Usuario usuario) throws Exception {
        return mvc.perform(get("/api/v1/eventos/afectados").param("usuarioId", String.valueOf(usuario.getId()))
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }

    private ResultActions login(String usuario) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreUsuario\":\"%s\",\"contrasena\":\"%s\"}".formatted(usuario, Personas.CONTRASENA)));
    }
}
