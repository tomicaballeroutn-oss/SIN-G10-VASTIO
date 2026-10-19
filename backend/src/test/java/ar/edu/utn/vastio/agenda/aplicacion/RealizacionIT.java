package ar.edu.utn.vastio.agenda.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Registrar realización de evento (docs/sprint-3-operacion.md, decisión 1). El reloj está fijo: los eventos del test
 * son del 14/05/2031 (mediodía 12:00 a 18:00, noche 20:00 a 06:00 del día siguiente). Cada test deshace sus cambios.
 */
@PruebaDeIntegracion
@Transactional
class RealizacionIT {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);
    private static final LocalDate FECHA = LocalDate.of(2031, 5, 14);

    @MockitoBean
    Clock reloj;

    @Autowired
    RealizacionService realizacion;

    @Autowired
    Escenario escenario;

    @Autowired
    Personas personas;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @Autowired
    MockMvc mvc;

    Usuario lucia;
    Usuario ana;

    @BeforeEach
    void personas() {
        lucia = personas.usuario("lucia.realizacion", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        ana = personas.usuario("ana.realizacion", "Ana Sosa", RolCodigo.PLANNER);
    }

    @Test
    void elTurnoMediodiaEntraEnCursoAlEmpezarYQuedaRealizadoAlTerminar() {
        long evento = escenario.evento("club", FECHA, "mediodia", "CONFIRMADO", lucia, ana, "Almuerzo corporativo");

        assertThat(estadoA(evento, FECHA, "11:59")).isEqualTo("CONFIRMADO");
        assertThat(estadoA(evento, FECHA, "12:00")).isEqualTo("EN_CURSO");
        assertThat(estadoA(evento, FECHA, "17:59")).isEqualTo("EN_CURSO");
        assertThat(estadoA(evento, FECHA, "18:00")).isEqualTo("REALIZADO");
        assertThat(historial(evento)).containsExactly(
                tuple("CONFIRMADO", "EN_CURSO", null),
                tuple("EN_CURSO", "REALIZADO", null));
    }

    @Test
    void elTurnoNocheSigueEnCursoDespuesDeLaMedianocheYTerminaElDiaSiguiente() {
        long evento = escenario.evento("avril", FECHA, "noche", "CONFIRMADO", lucia, ana, "15 de Sofía");
        LocalDate siguiente = FECHA.plusDays(1);

        assertThat(estadoA(evento, FECHA, "19:59")).isEqualTo("CONFIRMADO");
        assertThat(estadoA(evento, FECHA, "20:00")).isEqualTo("EN_CURSO");
        assertThat(estadoA(evento, FECHA, "23:59")).isEqualTo("EN_CURSO");
        assertThat(estadoA(evento, siguiente, "01:00")).isEqualTo("EN_CURSO");
        assertThat(estadoA(evento, siguiente, "05:59")).isEqualTo("EN_CURSO");
        assertThat(estadoA(evento, siguiente, "06:00")).isEqualTo("REALIZADO");
        assertThat(historial(evento)).containsExactly(
                tuple("CONFIRMADO", "EN_CURSO", null),
                tuple("EN_CURSO", "REALIZADO", null));
    }

    @Test
    void siEstuvoApagadoPoneAlDiaPasandoPorEnCursoYDespuesRealizado() {
        long evento = escenario.evento("santa-barbara", FECHA, "noche", "CONFIRMADO", lucia, ana, "Casamiento atrasado");

        assertThat(estadoA(evento, FECHA.plusDays(3), "10:00")).isEqualTo("REALIZADO");
        assertThat(historial(evento)).containsExactly(
                tuple("CONFIRMADO", "EN_CURSO", null),
                tuple("EN_CURSO", "REALIZADO", null));
    }

    @Test
    void esIdempotente() {
        long evento = escenario.evento("avril", FECHA, "noche", "CONFIRMADO", lucia, ana, "Dos pasadas");

        estadoA(evento, FECHA, "21:00");
        estadoA(evento, FECHA, "21:00");
        estadoA(evento, FECHA, "21:01");

        assertThat(historial(evento)).containsExactly(tuple("CONFIRMADO", "EN_CURSO", null));
    }

    @Test
    void noGeneraAvisos() {
        long evento = escenario.evento("avril", FECHA, "noche", "CONFIRMADO", lucia, ana, "Sin avisos");

        assertThat(estadoA(evento, FECHA.plusDays(1), "07:00")).isEqualTo("REALIZADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE evento_id = ?", Integer.class, evento))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRE_RESERVA", "SENADO", "CONTRATADO", "CANCELADO", "LIBERADA", "CERRADO"})
    void unEventoQueNoLlegoAConfirmadoOYaTerminoNoCambiaAunqueLlegueSuFecha(String estado) {
        long evento = escenario.evento("club", FECHA, "mediodia", estado, lucia, ana, "No avanza");

        assertThat(estadoA(evento, FECHA, "13:00")).isEqualTo(estado);
        assertThat(estadoA(evento, FECHA.plusDays(1), "13:00")).isEqualTo(estado);
        assertThat(historial(evento)).isEmpty();
    }

    @Test
    void enCursoLaFichaLoMuestraYYaNoSeModificanSusDatos() throws Exception {
        long evento = escenario.evento("avril", FECHA, "noche", "CONFIRMADO", lucia, ana, "Quince de Delfina");
        assertThat(estadoA(evento, FECHA, "20:30")).isEqualTo("EN_CURSO");
        int version = jdbc.queryForObject("SELECT version FROM evento WHERE evento_id = ?", Integer.class, evento);
        Usuario coordinacion = personas.de(RolCodigo.COORDINACION);

        mvc.perform(get("/api/v1/eventos/{id}", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(coordinacion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CURSO"));
        mvc.perform(put("/api/v1/eventos/{id}", evento).header(HttpHeaders.AUTHORIZATION, personas.bearer(coordinacion))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":%d,"nombre":"Otro nombre","tipoEventoId":2,
                                 "cliente":{"nombre":"Cliente de Quince de Delfina"},"contactos":[]}""".formatted(version)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("ESTADO_NO_PERMITE"))
                .andExpect(jsonPath("$.detail").value("El evento está en En curso: no se puede modificar sus datos."));
    }

    /** Corre la pasada a esa hora de Córdoba y devuelve el estado del evento en la base. */
    private String estadoA(long evento, LocalDate dia, String hora) {
        when(reloj.getZone()).thenReturn(ZONA);
        when(reloj.instant()).thenReturn(dia.atTime(LocalTime.parse(hora)).atZone(ZONA).toInstant());
        realizacion.ponerAlDia();
        entityManager.flush();
        return jdbc.queryForObject("SELECT estado FROM evento WHERE evento_id = ?", String.class, evento);
    }

    /** Transiciones del evento, en orden: estado anterior, nuevo y usuario (null = Sistema). */
    private List<org.assertj.core.groups.Tuple> historial(long evento) {
        entityManager.flush();
        return jdbc.query("""
                SELECT estado_anterior, estado_nuevo, usuario_id FROM cambio_estado_evento
                WHERE evento_id = ? ORDER BY cambio_id""",
                (rs, n) -> tuple(rs.getString(1), rs.getString(2), (Long) rs.getObject(3)), evento);
    }
}
