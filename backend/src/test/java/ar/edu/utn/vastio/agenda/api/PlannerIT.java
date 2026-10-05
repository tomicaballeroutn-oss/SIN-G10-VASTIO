package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

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
 * Asignar planner (UI-16).
 */
@PruebaDeIntegracion
@Transactional
class PlannerIT {

    private static final LocalDate FECHA = LocalDate.of(2032, 9, 18);

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
    Usuario ana;
    Usuario carla;
    Usuario coordinacion;
    long contratado;

    @BeforeEach
    void evento() {
        lucia = personas.usuario("lucia.planner", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        ana = personas.usuario("ana.planner", "Ana Sosa", RolCodigo.PLANNER);
        carla = personas.usuario("carla.planner", "Carla Núñez", RolCodigo.PLANNER);
        coordinacion = personas.usuario("melina.planner", "Melina Sifón", RolCodigo.COORDINACION);
        contratado = escenario.evento("avril", FECHA, "noche", "CONTRATADO", lucia, "Bruno y Martina");
    }

    @Test
    void coordinacionAsignaUnaPlannerYQuedaEnElHistorial() throws Exception {
        asignar(coordinacion, contratado, ana.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planner.nombre").value("Ana Sosa"))
                .andExpect(jsonPath("$.historial[?(@.campo == 'planner')].valorNuevo", hasItem("Ana Sosa")))
                .andExpect(jsonPath("$.historial[?(@.campo == 'planner')].usuario.nombre", hasItem("Melina Sifón")));
    }

    @Test
    void cambiarDePlannerAvisaALaNuevaYALaAnterior() throws Exception {
        Usuario administracion = personas.de(RolCodigo.ADMINISTRACION);
        asignar(coordinacion, contratado, ana.getId()).andExpect(status().isOk());
        asignar(coordinacion, contratado, carla.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historial[?(@.campo == 'planner' && @.valorAnterior == 'Ana Sosa')].valorNuevo", hasItem("Carla Núñez")));
        entityManager.flush();

        List<Long> destinatarios = jdbc.queryForList("""
                SELECT usuario_id FROM notificacion_destinatario WHERE notificacion_id =
                  (SELECT max(notificacion_id) FROM notificacion WHERE evento_id = ? AND tipo = 'PLANNER_ASIGNADA')""",
                Long.class, contratado);
        assertThat(destinatarios).contains(ana.getId(), carla.getId(), administracion.getId()).doesNotContain(coordinacion.getId());
    }

    @Test
    void laPlannerSeQuitaSoloAntesDeConfirmar() throws Exception {
        asignar(coordinacion, contratado, ana.getId()).andExpect(status().isOk());
        asignar(coordinacion, contratado, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planner").value(nullValue()));

        long confirmado = escenario.evento("club", FECHA, "noche", "CONFIRMADO", lucia, ana, "Confirmado");
        asignar(coordinacion, confirmado, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PLANNER_REQUERIDA"));
        asignar(coordinacion, confirmado, carla.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planner.nombre").value("Carla Núñez"));
    }

    @Test
    void reasignarLaMismaPlannerNoDejaRastro() throws Exception {
        asignar(coordinacion, contratado, ana.getId()).andExpect(status().isOk());
        asignar(coordinacion, contratado, ana.getId()).andExpect(status().isOk());
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM modificacion_evento WHERE evento_id = ? AND campo = 'planner'",
                Integer.class, contratado)).isEqualTo(1);
    }

    @Test
    void soloSeAsignanPlannersActivas() throws Exception {
        asignar(coordinacion, contratado, lucia.getId())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PLANNER_INVALIDA"));
        jdbc.update("UPDATE usuario SET activo = false, fecha_baja = now() WHERE usuario_id = ?", carla.getId());
        entityManager.clear();
        asignar(coordinacion, contratado, carla.getId())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PLANNER_INVALIDA"));
    }

    @Test
    void soloEnContratadoOConfirmado() throws Exception {
        long senado = escenario.evento("club", FECHA, "mediodia", "SENADO", lucia, "Señado");

        asignar(coordinacion, senado, ana.getId())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El evento está en Señado: la planner se asigna a eventos contratados o confirmados."));
    }

    @Test
    void lasCandidatasTraenLosOtrosEventosDeEsaFecha() throws Exception {
        escenario.evento("santa-barbara", FECHA, "mediodia", "CONFIRMADO", lucia, ana, "Quince de Delfina");
        escenario.evento("club", FECHA.plusDays(1), "noche", "CONFIRMADO", lucia, carla, "Otro día");
        escenario.evento("club", FECHA, "mediodia", "CANCELADO", lucia, carla, "Cancelado");

        mvc.perform(get("/api/v1/eventos/{id}/planners", contratado).header(HttpHeaders.AUTHORIZATION, personas.bearer(coordinacion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Ana Sosa')].otrosEventos[0]", contains(startsWith("Quince de Delfina · Santa Bárbara"))))
                .andExpect(jsonPath("$[?(@.nombre == 'Carla Núñez')].otrosEventos", contains(hasSize(0))))
                .andExpect(jsonPath("$[?(@.nombre == 'Lucía Ferreyra')]").isEmpty());
    }

    @Test
    void laFichaOfreceAsignarPlannerSoloACoordinacionYDireccion() throws Exception {
        ficha(coordinacion, contratado).andExpect(jsonPath("$.acciones.asignarPlanner").value(true));
        ficha(personas.de(RolCodigo.DIRECCION), contratado).andExpect(jsonPath("$.acciones.asignarPlanner").value(true));
        ficha(lucia, contratado).andExpect(jsonPath("$.acciones.asignarPlanner").value(false));
        long senado = escenario.evento("club", FECHA, "mediodia", "SENADO", lucia, "Señado");
        ficha(coordinacion, senado).andExpect(jsonPath("$.acciones.asignarPlanner").value(false));
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "VENDEDORA", "PLANNER", "COMPRAS", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoAsignaPlanner(RolCodigo rol) throws Exception {
        asignar(personas.de(rol), contratado, ana.getId()).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/eventos/{id}/planners", contratado).header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)))
                .andExpect(status().isForbidden());
    }

    private ResultActions asignar(Usuario quien, long id, Long plannerId) throws Exception {
        return mvc.perform(put("/api/v1/eventos/{id}/planner", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content("{\"plannerId\":%s}".formatted(plannerId)));
    }

    private ResultActions ficha(Usuario quien, long id) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }
}
