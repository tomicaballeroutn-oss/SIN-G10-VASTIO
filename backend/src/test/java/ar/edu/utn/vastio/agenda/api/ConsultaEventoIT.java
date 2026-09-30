package ar.edu.utn.vastio.agenda.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Consultar evento (UI-09): ficha, historial y lista de próximos.
 */
@PruebaDeIntegracion
@Transactional
class ConsultaEventoIT {

    private static final LocalDate FECHA = LocalDate.of(2031, 8, 16);

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
    Usuario sofia;
    Usuario ana;
    long senado;

    @BeforeEach
    void eventos() {
        lucia = personas.usuario("lucia.ficha", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        sofia = personas.usuario("sofia.ficha", "Sofía Méndez", RolCodigo.VENDEDORA);
        ana = personas.usuario("ana.ficha", "Ana Sosa", RolCodigo.PLANNER);
        senado = escenario.evento("club", FECHA, "noche", "SENADO", lucia, "Casamiento Gómez-Paz");
    }

    @Test
    void laVendedoraTitularVeLaFichaCompletaConElImporte() throws Exception {
        ficha(senado, personas.bearer(lucia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Casamiento Gómez-Paz"))
                .andExpect(jsonPath("$.estado").value("SENADO"))
                .andExpect(jsonPath("$.tipo.nombre").value("Quince"))
                .andExpect(jsonPath("$.salon.codigo").value("club"))
                .andExpect(jsonPath("$.fecha").value("2031-08-16"))
                .andExpect(jsonPath("$.turno.nombre").value("Noche"))
                .andExpect(jsonPath("$.cliente.nombre").value("Cliente de Casamiento Gómez-Paz"))
                .andExpect(jsonPath("$.vendedora.nombre").value("Lucía Ferreyra"))
                .andExpect(jsonPath("$.sena.importe").value(100000))
                .andExpect(jsonPath("$.sena.firmanteDni").value("30111222"))
                .andExpect(jsonPath("$.acciones.modificar").value(true))
                .andExpect(jsonPath("$.acciones.liberar").value(false))
                .andExpect(jsonPath("$.acciones.registrarSena").value(false));
    }

    @Test
    void laVendedoraNoAbreEventosDeOtra() throws Exception {
        ficha(senado, personas.bearer(sofia))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("EVENTO_DE_OTRA_VENDEDORA"));
    }

    @Test
    void elImporteDeLaSenaNoLlegaALaPlannerNiACompras() throws Exception {
        for (String bearer : new String[] {personas.bearer(ana), personas.bearer(RolCodigo.COMPRAS)}) {
            ficha(senado, bearer)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sena.fecha").exists())
                    .andExpect(jsonPath("$.sena.importe").doesNotExist())
                    .andExpect(jsonPath("$.acciones.modificar").value(false));
        }
        ficha(senado, personas.bearer(RolCodigo.ADMINISTRACION)).andExpect(jsonPath("$.sena.importe").value(100000));
    }

    @Test
    void laPlannerAsignadaPuedeModificar() throws Exception {
        jdbc.update("UPDATE evento SET planner_id = ? WHERE evento_id = ?", ana.getId(), senado);

        ficha(senado, personas.bearer(ana))
                .andExpect(jsonPath("$.planner.nombre").value("Ana Sosa"))
                .andExpect(jsonPath("$.acciones.modificar").value(true));
    }

    @Test
    void elHistorialMuestraQuienYCuandoYLasAccionesDeUnaPreReserva() throws Exception {
        String respuesta = mvc.perform(post("/api/v1/eventos").header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"salonId":1,"fecha":"2031-08-16","turnoId":1,"tipoEventoId":1,"cliente":{"nombre":"Paula Gómez"}}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(respuesta, "$.id")).longValue();

        ficha(id, personas.bearer(lucia))
                .andExpect(jsonPath("$.historial[0].tipo").value("ESTADO"))
                .andExpect(jsonPath("$.historial[0].estadoAnterior").doesNotExist())
                .andExpect(jsonPath("$.historial[0].estadoNuevo").value("PRE_RESERVA"))
                .andExpect(jsonPath("$.historial[0].usuario.nombre").value("Lucía Ferreyra"))
                .andExpect(jsonPath("$.historial[0].fechaHora").exists())
                .andExpect(jsonPath("$.sena").doesNotExist())
                .andExpect(jsonPath("$.acciones.liberar").value(true))
                .andExpect(jsonPath("$.acciones.registrarSena").value(true));
        ficha(id, personas.bearer(RolCodigo.COORDINACION)).andExpect(jsonPath("$.acciones.liberar").value(true));
        ficha(id, personas.bearer(RolCodigo.ADMINISTRACION))
                .andExpect(jsonPath("$.acciones.liberar").value(false))
                .andExpect(jsonPath("$.acciones.modificar").value(false));
    }

    @Test
    void unEventoInexistenteDevuelve404() throws Exception {
        ficha(999_999, personas.bearer(RolCodigo.DIRECCION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("EVENTO_INEXISTENTE"));
    }

    // ---------- lista ----------

    @Test
    void laVendedoraVeSoloSusProximosEventos() throws Exception {
        escenario.evento("avril", FECHA, "noche", "PRE_RESERVA", sofia, "Quince de Sofi");
        escenario.evento("avril", FECHA, "mediodia", "LIBERADA", lucia, "No prosperó");
        escenario.evento("santa-barbara", LocalDate.now().minusDays(3), "noche", "SENADO", lucia, "Ya pasó");

        lista(personas.bearer(lucia))
                .andExpect(jsonPath("$[*].nombre", hasItem("Casamiento Gómez-Paz")))
                .andExpect(jsonPath("$[*].nombre", not(hasItem("Quince de Sofi"))))
                .andExpect(jsonPath("$[*].nombre", not(hasItem("No prosperó"))))
                .andExpect(jsonPath("$[*].nombre", not(hasItem("Ya pasó"))));
        lista(personas.bearer(RolCodigo.ADMINISTRACION))
                .andExpect(jsonPath("$[*].nombre", hasItem("Casamiento Gómez-Paz")))
                .andExpect(jsonPath("$[*].nombre", hasItem("Quince de Sofi")));
    }

    @Test
    void laPlannerVeTodosLosEventosAunqueNoLosTengaAsignados() throws Exception {
        escenario.evento("avril", FECHA, "noche", "PRE_RESERVA", sofia, "Quince de Sofi");
        lista(personas.bearer(ana))
                .andExpect(jsonPath("$[*].nombre", hasItem("Casamiento Gómez-Paz")))
                .andExpect(jsonPath("$[*].nombre", hasItem("Quince de Sofi")));

        jdbc.update("UPDATE evento SET planner_id = ? WHERE evento_id = ?", ana.getId(), senado);
        entityManager.clear(); // la primera consulta dejó el evento en memoria sin planner
        lista(personas.bearer(ana))
                .andExpect(jsonPath("$[*].nombre", hasItem("Casamiento Gómez-Paz")))
                .andExpect(jsonPath("$[?(@.nombre == 'Casamiento Gómez-Paz')].planner.nombre", hasItem("Ana Sosa")));
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"BARRA", "COCINA"})
    void barraYCocinaNoConsultanEventos(RolCodigo rol) throws Exception {
        String bearer = personas.bearer(rol);
        ficha(senado, bearer).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/eventos").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isForbidden());
    }

    private ResultActions ficha(long id, String bearer) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions lista(String bearer) throws Exception {
        return mvc.perform(get("/api/v1/eventos").header(HttpHeaders.AUTHORIZATION, bearer)).andExpect(status().isOk());
    }
}
