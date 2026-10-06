package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Registrar evento: datos básicos, contactos y registro de modificaciones.
 */
@PruebaDeIntegracion
@Transactional
class DatosEventoIT {

    private static final LocalDate FECHA = LocalDate.of(2031, 11, 22);

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

    Usuario lucia;
    long evento;

    @BeforeEach
    void evento() {
        lucia = personas.usuario("lucia.datos", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        evento = escenario.evento("avril", FECHA, "noche", "PRE_RESERVA", lucia, "Quince de Delfina");
    }

    @Test
    void laVendedoraCompletaLosDatosYCadaCambioQuedaRegistrado() throws Exception {
        guardar(lucia, datos(0, "Los 15 de Delfina", "Prefieren que las llamen de tarde.", "351 555-1234", """
                [{"nombre":"María Ríos","vinculo":"madre","telefono":"351 444-0000"}]"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Los 15 de Delfina"))
                .andExpect(jsonPath("$.cliente.telefono").value("351 555-1234"))
                .andExpect(jsonPath("$.contactos", hasSize(1)))
                .andExpect(jsonPath("$.contactos[0].vinculo").value("madre"))
                .andExpect(jsonPath("$.version").value(greaterThan(0)))
                .andExpect(jsonPath("$.historial[?(@.tipo == 'MODIFICACION')].campo", hasItem("observaciones_internas")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'nombre')].valorAnterior", hasItem("Quince de Delfina")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'contacto')].valorNuevo", hasItem("María Ríos (madre) · 351 444-0000")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'cliente.telefono')].usuario.nombre", hasItem("Lucía Ferreyra")));

        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT campo, valor_anterior, valor_nuevo, usuario_id FROM modificacion_evento WHERE evento_id = ? ORDER BY campo", evento);
        assertThat(filas).extracting(f -> f.get("campo"))
                .containsExactly("cliente.telefono", "contacto", "nombre", "observaciones_internas");
        assertThat(filas).allSatisfy(f -> assertThat(f.get("usuario_id")).isEqualTo(lucia.getId()));
    }

    @Test
    void laHoraDeInicioSeCambiaYVaciaVuelveALaDelTurno() throws Exception {
        mvc.perform(put("/api/v1/eventos/{id}", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":0,"nombre":"Quince de Delfina","tipoEventoId":2,"horaInicio":"21:30",
                                 "cliente":{"nombre":"Cliente de Quince de Delfina"},"contactos":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaInicio").value("21:30:00"))
                .andExpect(jsonPath("$.turno.horaInicio").value("20:00:00"))
                .andExpect(jsonPath("$.historial[?(@.campo == 'hora_inicio')].valorNuevo", hasItem("21:30")));
        entityManager.flush();
        int version = jdbc.queryForObject("SELECT version FROM evento WHERE evento_id = ?", Integer.class, evento);

        guardar(lucia, datos(version, "Quince de Delfina", null, null, "[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaInicio").doesNotExist())
                .andExpect(jsonPath("$.historial[?(@.campo == 'hora_inicio' && @.valorAnterior == '21:30')]").isNotEmpty());
    }

    @Test
    void cambiarYQuitarContactosTambienQuedaRegistrado() throws Exception {
        String ficha = guardar(lucia, datos(0, "Quince de Delfina", null, null, """
                [{"nombre":"María Ríos","vinculo":"madre"},{"nombre":"Jorge Ríos","vinculo":"padre"}]"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(ficha, "$.contactos[*].id");
        int version = JsonPath.read(ficha, "$.version");
        assertThat(version).as("agregar contactos también es una versión nueva").isPositive();

        guardar(lucia, datos(version, "Quince de Delfina", null, null, """
                [{"id":%d,"nombre":"María Ríos","vinculo":"madre","telefono":"351 444-0000"}]""".formatted(ids.get(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactos", hasSize(1)))
                .andExpect(jsonPath("$.historial[?(@.campo == 'contacto')].valorAnterior", hasItem("Jorge Ríos (padre)")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'contacto')].valorNuevo", hasItem("María Ríos (madre) · 351 444-0000")));
    }

    @Test
    void guardarSinCambiosNoAgregaNadaAlHistorial() throws Exception {
        guardar(lucia, datos(0, "Quince de Delfina", null, null, "[]")).andExpect(status().isOk());
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM modificacion_evento WHERE evento_id = ?", Integer.class, evento)).isZero();
    }

    @Test
    void siOtraPersonaGuardoEnElMedioNoSePisa() throws Exception {
        guardar(lucia, datos(0, "Primera versión", null, null, "[]")).andExpect(status().isOk());
        entityManager.flush();

        guardar(personas.de(RolCodigo.COORDINACION), datos(0, "Versión vieja", null, null, "[]"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EVENTO_MODIFICADO"));
    }

    @Test
    void quienesPuedenModificar() throws Exception {
        Usuario sofia = personas.usuario("sofia.datos", "Sofía Méndez", RolCodigo.VENDEDORA);
        Usuario ana = personas.usuario("ana.datos", "Ana Sosa", RolCodigo.PLANNER);

        guardar(sofia, datos(0, "Ajeno", null, null, "[]"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("EVENTO_DE_OTRA_VENDEDORA"));
        guardar(ana, datos(0, "Sin asignar", null, null, "[]"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        guardar(personas.de(RolCodigo.ADMINISTRACION), datos(0, "Administración", null, null, "[]"))
                .andExpect(status().isForbidden());

        jdbc.update("UPDATE evento SET planner_id = ? WHERE evento_id = ?", ana.getId(), evento);
        entityManager.clear();
        guardar(ana, datos(0, "La planner asignada", null, null, "[]")).andExpect(status().isOk());
    }

    @Test
    void unaPreReservaLiberadaNoSeModifica() throws Exception {
        long liberada = escenario.evento("club", FECHA, "noche", "LIBERADA", lucia, "No prosperó");

        mvc.perform(put("/api/v1/eventos/{id}", liberada).header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia))
                        .contentType(MediaType.APPLICATION_JSON).content(datos(0, "Otra cosa", null, null, "[]")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ESTADO_NO_PERMITE"))
                .andExpect(jsonPath("$.detail").value("El evento está en Liberada: no se puede modificar sus datos."));
    }

    @Test
    void validaLosDatos() throws Exception {
        guardar(lucia, """
                {"version":0,"nombre":" ","tipoEventoId":2,
                 "cliente":{"nombre":"","documento":"12.345"},"contactos":[{"nombre":""}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("nombre")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("cliente.nombre")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("cliente.documento")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("contactos[0].nombre")));
    }

    @Test
    void laFichaIncluyeLaVersionParaEditar() throws Exception {
        mvc.perform(get("/api/v1/eventos/{id}", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia)))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.contactos", hasSize(0)));
    }

    private ResultActions guardar(Usuario quien, String json) throws Exception {
        return mvc.perform(put("/api/v1/eventos/{id}", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String datos(int version, String nombre, String observaciones, String telefono,
            String contactos) {
        return """
                {"version":%d,"nombre":"%s","tipoEventoId":2,"observacionesInternas":%s,
                 "cliente":{"nombre":"Cliente de Quince de Delfina","telefono":%s},"contactos":%s}"""
                .formatted(version, nombre, observaciones == null ? "null" : "\"" + observaciones + "\"",
                        telefono == null ? "null" : "\"" + telefono + "\"", contactos);
    }
}
