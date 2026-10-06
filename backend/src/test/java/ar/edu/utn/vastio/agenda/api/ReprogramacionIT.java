package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
 * Reprogramar evento (UI-18). Salones: 1 Avril, 2 Club de Campo, 3 Santa Bárbara; turnos: 1 mediodía, 2 noche.
 * Motivos de V2: 4 Pedido del cliente (reprogramación), 1 Desistimiento del cliente (cancelación).
 */
@PruebaDeIntegracion
@Transactional
class ReprogramacionIT {

    private static final LocalDate FECHA = LocalDate.of(2033, 5, 14);
    private static final LocalDate NUEVA = LocalDate.of(2033, 5, 21);

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
    long contratado;

    @BeforeEach
    void evento() {
        lucia = personas.usuario("lucia.reprogramar", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        ana = personas.usuario("ana.reprogramar", "Ana Sosa", RolCodigo.PLANNER);
        contratado = escenario.evento("avril", FECHA, "noche", "CONTRATADO", lucia, ana, "Bruno y Martina");
    }

    @Test
    void laTitularReprogramaSinCambiarElEstadoYSeConservaLaFechaOriginal() throws Exception {
        reprogramar(lucia, contratado, 2, NUEVA, 2, 4, "Se casa el hermano ese día")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONTRATADO"))
                .andExpect(jsonPath("$.salon.nombre").value("Club de Campo"))
                .andExpect(jsonPath("$.fecha").value(NUEVA.toString()))
                .andExpect(jsonPath("$.reprogramadoDesde", startsWith("Avril · sáb 14/5 · Noche")))
                .andExpect(jsonPath("$.historial[?(@.tipo == 'REPROGRAMACION')].valorAnterior", hasItem("Avril · sáb 14/5 · Noche")))
                .andExpect(jsonPath("$.historial[?(@.tipo == 'REPROGRAMACION')].valorNuevo", hasItem("Club de Campo · sáb 21/5 · Noche")))
                .andExpect(jsonPath("$.historial[?(@.tipo == 'REPROGRAMACION')].observacion", hasItem("Pedido del cliente: Se casa el hermano ese día")))
                .andExpect(jsonPath("$.historial[?(@.tipo == 'REPROGRAMACION')].usuario.nombre", hasItem("Lucía Ferreyra")));
        entityManager.flush();

        Map<String, Object> fila = jdbc.queryForMap("SELECT unidad_anterior_id, unidad_nueva_id, motivo_id FROM reprogramacion WHERE evento_id = ?", contratado);
        assertThat(fila.get("unidad_anterior_id")).isEqualTo(escenario.unidad("avril", FECHA, "noche"));
        assertThat(fila.get("unidad_nueva_id")).isEqualTo(escenario.unidad("club", NUEVA, "noche"));
    }

    @Test
    void laFechaOriginalQuedaLibreYAvisaATodasLasAreas() throws Exception {
        Usuario cocina = personas.de(RolCodigo.COCINA);
        Usuario compras = personas.de(RolCodigo.COMPRAS);
        Usuario coordinacion = personas.de(RolCodigo.COORDINACION);

        reprogramar(coordinacion, contratado, 1, NUEVA, 2, 4, null).andExpect(status().isOk());
        entityManager.flush();

        List<Long> destinatarios = jdbc.queryForList("""
                SELECT d.usuario_id FROM notificacion_destinatario d JOIN notificacion n USING (notificacion_id)
                WHERE n.evento_id = ? AND n.tipo = 'REPROGRAMACION'""", Long.class, contratado);
        assertThat(destinatarios).contains(cocina.getId(), compras.getId(), lucia.getId(), ana.getId()).doesNotContain(coordinacion.getId());
        assertThat(jdbc.queryForObject("SELECT mensaje FROM notificacion WHERE evento_id = ? AND tipo = 'REPROGRAMACION'", String.class, contratado))
                .startsWith("Evento reprogramado: Bruno y Martina · Avril · sáb 21/5 · Noche").endsWith("(antes Avril · sáb 14/5 · Noche)");

        mvc.perform(post("/api/v1/eventos").header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"salonId":1,"fecha":"%s","turnoId":2,"tipoEventoId":1,"cliente":{"nombre":"Otra pareja"}}""".formatted(FECHA)))
                .andExpect(status().isCreated());
    }

    @Test
    void enPreReservaCambiarLaFechaEsUnaModificacionSinMotivo() throws Exception {
        long preReserva = escenario.evento("santa-barbara", FECHA, "mediodia", "PRE_RESERVA", lucia, "Pre-reserva");

        reprogramar(lucia, preReserva, 3, NUEVA, 1, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(NUEVA.toString()))
                .andExpect(jsonPath("$.historial[?(@.campo == 'unidad' && @.tipo == 'MODIFICACION')].valorNuevo",
                        hasItem("Santa Bárbara · sáb 21/5 · Mediodía")));
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM reprogramacion WHERE evento_id = ?", Integer.class, preReserva)).isZero();
    }

    @Test
    void unaUnidadOcupadaOBloqueadaNoSeElige() throws Exception {
        escenario.evento("club", NUEVA, "noche", "SENADO", lucia, "Ocupa la fecha");
        escenario.bloqueo("santa-barbara", NUEVA, "noche", lucia, "Pintura");

        reprogramar(lucia, contratado, 2, NUEVA, 2, 4, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esa fecha ya está tomada. Elegí otro salón, otra fecha u otro turno."));
        reprogramar(lucia, contratado, 3, NUEVA, 2, 4, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("UNIDAD_BLOQUEADA"));
    }

    @Test
    void validaLaUnidadNuevaYElMotivo() throws Exception {
        reprogramar(lucia, contratado, 1, FECHA, 2, 4, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("MISMA_UNIDAD"));
        reprogramar(lucia, contratado, 1, LocalDate.now().minusDays(1), 2, 4, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FECHA_PASADA"));
        reprogramar(lucia, contratado, 1, NUEVA, 2, null, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FALTA_MOTIVO"));
        reprogramar(lucia, contratado, 1, NUEVA, 2, 1, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_INVALIDO"));
        jdbc.update("UPDATE salon SET activo = false WHERE salon_id = 3");
        entityManager.clear();
        reprogramar(lucia, contratado, 3, NUEVA, 2, 4, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SALON_DADO_DE_BAJA"));
    }

    @Test
    void unEventoCanceladoNoSeReprograma() throws Exception {
        long cancelado = escenario.evento("club", FECHA, "noche", "CANCELADO", lucia, "Cancelado");

        reprogramar(lucia, cancelado, 1, NUEVA, 2, 4, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("El evento está en Cancelado: no se puede reprogramar."));
    }

    @Test
    void quienesPuedenReprogramar() throws Exception {
        reprogramar(ana, contratado, 1, NUEVA, 2, 4, null).andExpect(status().isForbidden());
        reprogramar(personas.usuario("sofia.reprogramar", "Sofía Méndez", RolCodigo.VENDEDORA), contratado, 1, NUEVA, 2, 4, null)
                .andExpect(status().isForbidden());
        ficha(ana, contratado).andExpect(jsonPath("$.acciones.reprogramar").value(false));
        ficha(lucia, contratado).andExpect(jsonPath("$.acciones.reprogramar").value(true));
        reprogramar(personas.de(RolCodigo.DIRECCION), contratado, 1, NUEVA, 2, 4, null).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "PLANNER", "COMPRAS", "BARRA", "COCINA"})
    void elRestoDeLosPerfilesNoReprograma(RolCodigo rol) throws Exception {
        reprogramar(personas.de(rol), contratado, 1, NUEVA, 2, 4, null).andExpect(status().isForbidden());
    }

    private ResultActions reprogramar(Usuario quien, long id, int salon, LocalDate fecha, int turno, Integer motivo, String detalle)
            throws Exception {
        return mvc.perform(post("/api/v1/eventos/{id}/reprogramacion", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"salonId":%d,"fecha":"%s","turnoId":%d,"motivoId":%s,"detalle":%s}""".formatted(
                        salon, fecha, turno, motivo, detalle == null ? "null" : "\"" + detalle + "\"")));
    }

    private ResultActions ficha(Usuario quien, long id) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)));
    }
}
