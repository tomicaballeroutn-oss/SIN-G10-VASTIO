package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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
 * Liberar pre-reserva (UI-10).
 */
@PruebaDeIntegracion
@Transactional
class LiberarPreReservaIT {

    private static final LocalDate FECHA = LocalDate.of(2032, 3, 13);
    private static final String UNIDAD = """
            {"salonId":2,"fecha":"2032-03-13","turnoId":2,"tipoEventoId":1,"cliente":{"nombre":"%s"}}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    Escenario escenario;

    @Autowired
    JdbcTemplate jdbc;

    Usuario lucia;
    long preReserva;

    @BeforeEach
    void preReserva() throws Exception {
        lucia = personas.usuario("lucia.libera", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        String respuesta = mvc.perform(post("/api/v1/eventos").header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia))
                        .contentType(MediaType.APPLICATION_JSON).content(UNIDAD.formatted("Paula Gómez")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        preReserva = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    @Test
    void laTitularLiberaYLaFechaVuelveAEstarDisponible() throws Exception {
        liberar(lucia, preReserva, "{\"observacion\":\"El cliente eligió otro salón.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LIBERADA"))
                .andExpect(jsonPath("$.acciones.liberar").value(false))
                .andExpect(jsonPath("$.acciones.modificar").value(false))
                .andExpect(jsonPath("$.historial[1].estadoAnterior").value("PRE_RESERVA"))
                .andExpect(jsonPath("$.historial[1].estadoNuevo").value("LIBERADA"))
                .andExpect(jsonPath("$.historial[1].usuario.nombre").value("Lucía Ferreyra"))
                .andExpect(jsonPath("$.historial[1].observacion").value("El cliente eligió otro salón."));

        mvc.perform(get("/api/v1/agenda?mes=2032-03").header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia)))
                .andExpect(jsonPath("$.unidades[?(@.fecha == '2032-03-13')]").isEmpty());
        mvc.perform(post("/api/v1/eventos").header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia))
                        .contentType(MediaType.APPLICATION_JSON).content(UNIDAD.formatted("Otro cliente")))
                .andExpect(status().isCreated());
        // No es una cancelación: no hay motivo de cancelación ni aviso de cancelación.
        assertThat(jdbc.queryForObject("SELECT motivo_cancelacion_id FROM evento WHERE evento_id = ?", Short.class, preReserva)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id = ? AND tipo = 'CANCELACION'", Integer.class, preReserva)).isZero();
    }

    @Test
    void sinComentarioTambienSeLibera() throws Exception {
        liberar(lucia, preReserva, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historial[1].observacion").doesNotExist());
    }

    @Test
    void coordinacionLiberaPreReservasDeCualquierVendedora() throws Exception {
        liberar(personas.de(RolCodigo.COORDINACION), preReserva, null).andExpect(status().isOk());
    }

    @Test
    void otraVendedoraNoLibera() throws Exception {
        liberar(personas.usuario("sofia.libera", "Sofía Méndez", RolCodigo.VENDEDORA), preReserva, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("EVENTO_DE_OTRA_VENDEDORA"));
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "PLANNER", "COMPRAS", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoLibera(RolCodigo rol) throws Exception {
        liberar(personas.de(rol), preReserva, null).andExpect(status().isForbidden());
    }

    @Test
    void soloSeLiberanPreReservas() throws Exception {
        long senado = escenario.evento("avril", FECHA, "noche", "SENADO", lucia, "Casamiento señado");

        liberar(lucia, senado, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ESTADO_NO_PERMITE"))
                .andExpect(jsonPath("$.detail").value("El evento está en Señado: solo se liberan pre-reservas."));

        liberar(lucia, preReserva, null).andExpect(status().isOk());
        liberar(lucia, preReserva, null).andExpect(status().isUnprocessableContent());
    }

    private ResultActions liberar(Usuario quien, long id, String json) throws Exception {
        var pedido = post("/api/v1/eventos/{id}/liberacion", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien));
        if (json != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(pedido);
    }
}
