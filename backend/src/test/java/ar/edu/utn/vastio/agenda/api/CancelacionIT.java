package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Cancelar evento (UI-17). Motivos de V2: 1 Desistimiento del cliente, 3 Otro (cancelación); 4 es de reprogramación.
 */
@PruebaDeIntegracion
@Transactional
class CancelacionIT {

    private static final LocalDate FECHA = LocalDate.of(2032, 10, 23);

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
    Usuario coordinacion;

    @BeforeEach
    void personas() {
        lucia = personas.usuario("lucia.cancelar", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        ana = personas.usuario("ana.cancelar", "Ana Sosa", RolCodigo.PLANNER);
        coordinacion = personas.usuario("melina.cancelar", "Melina Sifón", RolCodigo.COORDINACION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SENADO", "CONTRATADO", "CONFIRMADO"})
    void coordinacionCancelaYLaFechaQuedaLibre(String estado) throws Exception {
        long evento = escenario.evento("avril", FECHA, "noche", estado, lucia, ana, "Bruno y Martina");

        cancelar(coordinacion, evento, 1, "Se mudan a Mendoza")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.cancelacion.motivo").value("Desistimiento del cliente"))
                .andExpect(jsonPath("$.cancelacion.detalle").value("Se mudan a Mendoza"))
                .andExpect(jsonPath("$.acciones.cancelar").value(false))
                .andExpect(jsonPath("$.acciones.modificar").value(false))
                .andExpect(jsonPath("$.historial[?(@.estadoNuevo == 'CANCELADO')].estadoAnterior", hasItem(estado)))
                .andExpect(jsonPath("$.historial[?(@.estadoNuevo == 'CANCELADO')].observacion",
                        hasItem("Desistimiento del cliente: Se mudan a Mendoza")));
        entityManager.flush();

        // La unidad vuelve a estar disponible: se puede pre-reservar de nuevo.
        mvc.perform(post("/api/v1/eventos").header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"salonId":1,"fecha":"%s","turnoId":2,"tipoEventoId":1,"cliente":{"nombre":"Otra pareja"}}""".formatted(FECHA)))
                .andExpect(status().isCreated());
    }

    @Test
    void avisaATodasLasAreasLaTitularYLaPlanner() throws Exception {
        Usuario direccion = personas.de(RolCodigo.DIRECCION);
        Usuario administracion = personas.de(RolCodigo.ADMINISTRACION);
        Usuario compras = personas.de(RolCodigo.COMPRAS);
        Usuario cocina = personas.de(RolCodigo.COCINA);
        Usuario barra = personas.de(RolCodigo.BARRA);
        long evento = escenario.evento("avril", FECHA, "noche", "CONFIRMADO", lucia, ana, "Bruno y Martina");

        cancelar(coordinacion, evento, 2, null).andExpect(status().isOk());
        entityManager.flush();

        List<Long> destinatarios = jdbc.queryForList("""
                SELECT d.usuario_id FROM notificacion_destinatario d JOIN notificacion n USING (notificacion_id)
                WHERE n.evento_id = ? AND n.tipo = 'CANCELACION'""", Long.class, evento);
        assertThat(destinatarios)
                .contains(direccion.getId(), administracion.getId(), compras.getId(), cocina.getId(), lucia.getId(), ana.getId())
                .doesNotContain(coordinacion.getId(), barra.getId());
    }

    @Test
    void conElMotivoOtroElDetalleEsObligatorio() throws Exception {
        long evento = escenario.evento("avril", FECHA, "noche", "SENADO", lucia, "Bruno y Martina");

        cancelar(coordinacion, evento, 3, "  ")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FALTA_DETALLE"));
        cancelar(coordinacion, evento, 3, "El salón no les queda cómodo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelacion.motivo").value("Otro"));
    }

    @Test
    void soloMotivosActivosDeCancelacion() throws Exception {
        long evento = escenario.evento("avril", FECHA, "noche", "SENADO", lucia, "Bruno y Martina");

        cancelar(coordinacion, evento, 4, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_INVALIDO"));
        jdbc.update("UPDATE motivo SET activo = false WHERE motivo_id = 2");
        entityManager.clear();
        cancelar(coordinacion, evento, 2, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_INVALIDO"));
    }

    @Test
    void validaElPedido() throws Exception {
        long evento = escenario.evento("avril", FECHA, "noche", "SENADO", lucia, "Bruno y Martina");

        mvc.perform(post("/api/v1/eventos/{id}/cancelacion", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(coordinacion))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"detalle":"%s"}""".formatted("a".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItem("motivoId")))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("detalle")));
    }

    @Test
    void unaPreReservaNoSeCancelaSeLibera() throws Exception {
        long preReserva = escenario.evento("avril", FECHA, "noche", "PRE_RESERVA", lucia, "Pre-reserva");
        long cancelado = escenario.evento("club", FECHA, "noche", "CANCELADO", lucia, "Ya cancelado");

        cancelar(coordinacion, preReserva, 1, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Una pre-reserva no se cancela: se libera con «Liberar pre-reserva»."));
        cancelar(coordinacion, cancelado, 1, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ESTADO_NO_PERMITE"));
    }

    @Test
    void laFichaOfreceCancelarSoloACoordinacionYDireccion() throws Exception {
        long evento = escenario.evento("avril", FECHA, "noche", "CONTRATADO", lucia, ana, "Bruno y Martina");

        ficha(coordinacion, evento).andExpect(jsonPath("$.acciones.cancelar").value(true));
        ficha(personas.de(RolCodigo.DIRECCION), evento).andExpect(jsonPath("$.acciones.cancelar").value(true));
        ficha(lucia, evento).andExpect(jsonPath("$.acciones.cancelar").value(false));
        ficha(ana, evento).andExpect(jsonPath("$.acciones.cancelar").value(false));
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "VENDEDORA", "PLANNER", "COMPRAS", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoCancela(RolCodigo rol) throws Exception {
        long evento = escenario.evento("avril", FECHA, "noche", "SENADO", lucia, "Bruno y Martina");

        cancelar(personas.de(rol), evento, 1, null).andExpect(status().isForbidden());
    }

    private ResultActions cancelar(Usuario quien, long id, int motivo, String detalle) throws Exception {
        return mvc.perform(post("/api/v1/eventos/{id}/cancelacion", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"motivoId":%d,"detalle":%s}""".formatted(motivo, detalle == null ? "null" : "\"" + detalle + "\"")));
    }

    private ResultActions ficha(Usuario quien, long id) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }
}
