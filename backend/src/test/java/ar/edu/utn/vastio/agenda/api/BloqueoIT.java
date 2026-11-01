package ar.edu.utn.vastio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;

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
import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Bloqueo de unidades (UI-21, A1-02). Abril de 2033: lejos de cualquier otro dato.
 * Salones: 1 Avril, 2 Club de Campo, 3 Santa Bárbara. Turnos: 1 Mediodía, 2 Noche. Motivos de bloqueo: 7, 8 y 9.
 * Cada test que termina en error hace un solo pedido fallido: el error deja marcada la transacción del test.
 */
@PruebaDeIntegracion
@Transactional
class BloqueoIT {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of(VastioApplication.ZONA_HORARIA));
    private static final LocalDate LUNES = LocalDate.of(2033, 4, 4);
    private static final String MANTENIMIENTO = "7";
    private static final String AMBOS_TURNOS = "[1, 2]";

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

    Usuario gabriela;
    Usuario lucia;

    @BeforeEach
    void usuarios() {
        gabriela = personas.usuario("gabriela.bloqueo", "Gabriela Soria", RolCodigo.ADMINISTRACION);
        lucia = personas.usuario("lucia.bloqueo", "Lucía Ferreyra", RolCodigo.VENDEDORA);
    }

    // ---------- quién puede ----------

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/v1/bloqueos")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"VENDEDORA", "PLANNER", "COMPRAS", "BARRA", "COCINA"})
    void losOtrosPerfilesNoVenNiRegistranNiDesbloquean(RolCodigo rol) throws Exception {
        long existente = escenario.bloqueo("avril", LUNES, "noche", gabriela, null);

        mvc.perform(get("/api/v1/bloqueos").header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)))
                .andExpect(status().isForbidden());
        registrar(personas.bearer(rol), cuerpo("[1]", LUNES, null, "[2]", MANTENIMIENTO, null)).andExpect(status().isForbidden());
        desbloquear(personas.bearer(rol), existente).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT activo FROM bloqueo WHERE bloqueo_id = ?", Boolean.class, existente)).isTrue();
        assertThat(cantidadDeBloqueosDeAbril2033()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "COORDINACION", "DIRECCION"})
    void administracionCoordinacionYDireccionRegistran(RolCodigo rol) throws Exception {
        registrar(personas.bearer(rol), cuerpo("[1]", LUNES, null, "[2]", MANTENIMIENTO, null)).andExpect(status().isCreated());
    }

    // ---------- registrar ----------

    @Test
    void registraUnDiaConSuMotivoYQuienLoHizo() throws Exception {
        registrar(gabriela, cuerpo("[3]", LUNES, null, "[2]", "8", "Feriado puente"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.motivo.nombre").value("Feriado"))
                .andExpect(jsonPath("$.detalle").value("Feriado puente"))
                .andExpect(jsonPath("$.salones[0].nombre").value("Santa Bárbara"))
                .andExpect(jsonPath("$.desde").value("2033-04-04"))
                .andExpect(jsonPath("$.hasta").value("2033-04-04"))
                .andExpect(jsonPath("$.turnos[0].nombre").value("Noche"))
                .andExpect(jsonPath("$.cantidadUnidades").value(1))
                .andExpect(jsonPath("$.registradoPor").value("Gabriela Soria"));
        entityManager.flush();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bloqueo b JOIN bloqueo_unidad bu USING (bloqueo_id)
                JOIN unidad_comercializable u USING (unidad_id)
                WHERE b.activo AND b.usuario_id = ? AND u.fecha = ? AND u.salon_id = 3 AND u.turno_id = 2""",
                Integer.class, gabriela.getId(), LUNES)).isEqualTo(1);
    }

    @Test
    void unRangoSeExpandeEnUnaUnidadPorSalonFechaYTurno() throws Exception {
        registrar(gabriela, cuerpo("[1, 2]", LUNES, LUNES.plusDays(2), AMBOS_TURNOS, MANTENIMIENTO, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidadUnidades").value(12))
                .andExpect(jsonPath("$.salones", hasSize(2)))
                .andExpect(jsonPath("$.turnos", hasSize(2)))
                .andExpect(jsonPath("$.desde").value("2033-04-04"))
                .andExpect(jsonPath("$.hasta").value("2033-04-06"));
        entityManager.flush();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bloqueo_unidad bu JOIN bloqueo b USING (bloqueo_id) WHERE b.usuario_id = ?""",
                Integer.class, gabriela.getId())).isEqualTo(12);
    }

    @Test
    void sinFechaFinalBloqueaUnSoloDia() throws Exception {
        registrar(gabriela, cuerpo("[1]", LUNES, null, AMBOS_TURNOS, MANTENIMIENTO, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidadUnidades").value(2))
                .andExpect(jsonPath("$.hasta").value("2033-04-04"));
    }

    @Test
    void elDetalleEnBlancoSeGuardaVacio() throws Exception {
        registrar(gabriela, cuerpo("[1]", LUNES, null, "[1]", MANTENIMIENTO, "   "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.detalle").doesNotExist());
    }

    @Test
    void laAgendaMuestraLasUnidadesBloqueadasConSuMotivo() throws Exception {
        registrar(gabriela, cuerpo("[2]", LUNES, LUNES.plusDays(1), "[1]", MANTENIMIENTO, "Pintura")).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/agenda").param("mes", "2033-04").header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidades", hasSize(2)))
                .andExpect(jsonPath("$.unidades[0].estado").value("BLOQUEADO"))
                .andExpect(jsonPath("$.unidades[0].salonId").value(2))
                .andExpect(jsonPath("$.unidades[0].fecha").value("2033-04-04"))
                .andExpect(jsonPath("$.unidades[0].bloqueo.motivo").value("Mantenimiento"))
                .andExpect(jsonPath("$.unidades[0].bloqueo.detalle").value("Pintura"));
    }

    @Test
    void unEventoLiberadoOCanceladoNoImpideBloquear() throws Exception {
        escenario.evento("avril", LUNES, "noche", "LIBERADA", lucia, "No prosperó");
        escenario.evento("avril", LUNES, "mediodia", "CANCELADO", lucia, "Cancelado");

        registrar(gabriela, cuerpo("[1]", LUNES, null, AMBOS_TURNOS, MANTENIMIENTO, null)).andExpect(status().isCreated());
    }

    // ---------- conflictos ----------

    @Test
    void conUnEventoActivoNoSeBloqueaYElMensajeNombraElEvento() throws Exception {
        escenario.evento("club", LUNES.plusDays(1), "noche", "SENADO", lucia, "Casamiento Gómez-Paz");
        String codigo = jdbc.queryForObject("SELECT codigo FROM evento WHERE nombre = 'Casamiento Gómez-Paz'", String.class);

        registrar(gabriela, cuerpo("[1, 2]", LUNES, LUNES.plusDays(2), AMBOS_TURNOS, MANTENIMIENTO, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("UNIDAD_OCUPADA"))
                .andExpect(jsonPath("$.detail").value(containsString("Club de Campo el 05/04/2033 (turno noche)")))
                .andExpect(jsonPath("$.detail").value(containsString(codigo)))
                .andExpect(jsonPath("$.detail").value(containsString("Casamiento Gómez-Paz")));

        assertThat(cantidadDeBloqueosDeAbril2033()).isZero();
    }

    @Test
    void conVariosEventosAvisaCuantosMasHay() throws Exception {
        escenario.evento("avril", LUNES, "noche", "PRE_RESERVA", lucia, "Quince de Delfina");
        escenario.evento("club", LUNES, "noche", "SENADO", lucia, "Bodas de oro");

        registrar(gabriela, cuerpo("[1, 2]", LUNES, null, "[2]", MANTENIMIENTO, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("UNIDAD_OCUPADA"))
                .andExpect(jsonPath("$.detail").value(containsString("Hay otras 1 en la misma situación.")));
    }

    @Test
    void unaUnidadYaBloqueadaNoSeBloqueaDeNuevo() throws Exception {
        escenario.bloqueo("santa-barbara", LUNES.plusDays(1), "mediodia", gabriela, null);

        registrar(gabriela, cuerpo("[3]", LUNES, LUNES.plusDays(2), "[1]", "9", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("YA_BLOQUEADA"))
                .andExpect(jsonPath("$.detail").value(containsString("Santa Bárbara el 05/04/2033 (turno mediodía) ya está bloqueado (mantenimiento)")));
    }

    @Test
    void unaVezDesbloqueadaLaUnidadSePuedeBloquearOtraVez() throws Exception {
        long anterior = escenario.bloqueo("avril", LUNES, "noche", gabriela, null);
        desbloquear(gabriela, anterior).andExpect(status().isNoContent());

        registrar(gabriela, cuerpo("[1]", LUNES, null, "[2]", "9", null)).andExpect(status().isCreated());
    }

    // ---------- reglas ----------

    @Test
    void sinSalonesNiTurnosNiFechaNiMotivoMarcaLosCampos() throws Exception {
        registrar(gabriela, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[?(@.campo == 'salonIds')].mensaje").value(hasItem("Elegí al menos un salón.")))
                .andExpect(jsonPath("$.errores[?(@.campo == 'turnoIds')].mensaje").value(hasItem("Elegí al menos un turno.")))
                .andExpect(jsonPath("$.errores[?(@.campo == 'desde')].mensaje").value(hasItem("Elegí la fecha desde la que se bloquea.")))
                .andExpect(jsonPath("$.errores[?(@.campo == 'motivoId')].mensaje").value(hasItem("Elegí el motivo.")));
    }

    @Test
    void elDetalleTieneTopeDe255() throws Exception {
        registrar(gabriela, cuerpo("[1]", LUNES, null, "[1]", MANTENIMIENTO, "x".repeat(256))).andExpect(status().isBadRequest());
    }

    @Test
    void noSeBloqueaUnaFechaPasada() throws Exception {
        registrar(gabriela, cuerpo("[1]", HOY.minusDays(1), null, "[1]", MANTENIMIENTO, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("FECHA_PASADA"));
    }

    @Test
    void laFechaFinalNoPuedeSerAnteriorALaInicial() throws Exception {
        registrar(gabriela, cuerpo("[1]", LUNES, LUNES.minusDays(1), "[1]", MANTENIMIENTO, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RANGO_INVALIDO"));
    }

    @Test
    void elRangoTieneTopeDe180Dias() throws Exception {
        registrar(gabriela, cuerpo("[1]", LUNES, LUNES.plusDays(180), "[1]", MANTENIMIENTO, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("RANGO_MUY_LARGO"))
                .andExpect(jsonPath("$.detail").value(containsString("180 días")));
    }

    @Test
    void conExactamente180DiasAlcanza() throws Exception {
        registrar(gabriela, cuerpo("[1]", LUNES, LUNES.plusDays(179), "[1]", MANTENIMIENTO, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidadUnidades").value(180));
    }

    @Test
    void elMotivoTieneQueSerDeBloqueo() throws Exception {
        registrar(gabriela, cuerpo("[1]", LUNES, null, "[1]", "1", null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_INVALIDO"));
    }

    @Test
    void unMotivoDadoDeBajaNoSirve() throws Exception {
        jdbc.update("UPDATE motivo SET activo = false WHERE motivo_id = 9");

        registrar(gabriela, cuerpo("[1]", LUNES, null, "[1]", "9", null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_INVALIDO"));
    }

    @Test
    void unMotivoInexistenteDevuelve404() throws Exception {
        registrar(gabriela, cuerpo("[1]", LUNES, null, "[1]", "999", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_INEXISTENTE"));
    }

    @Test
    void unSalonInexistenteDevuelve404() throws Exception {
        registrar(gabriela, cuerpo("[99]", LUNES, null, "[1]", MANTENIMIENTO, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SALON_INEXISTENTE"));
    }

    @Test
    void unSalonDadoDeBajaNoSeBloquea() throws Exception {
        jdbc.update("UPDATE salon SET activo = false WHERE codigo = 'club'");

        registrar(gabriela, cuerpo("[1, 2]", LUNES, null, "[1]", MANTENIMIENTO, null))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SALON_DADO_DE_BAJA"));
    }

    @Test
    void unTurnoInexistenteDevuelve404() throws Exception {
        registrar(gabriela, cuerpo("[1]", LUNES, null, "[7]", MANTENIMIENTO, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TURNO_INEXISTENTE"));
    }

    // ---------- lista ----------

    @Test
    void laListaMuestraLosActivosElMasProximoPrimero() throws Exception {
        registrar(gabriela, cuerpo("[3]", LUNES.plusDays(20), null, "[2]", "8", null)).andExpect(status().isCreated());
        registrar(gabriela, cuerpo("[1, 2]", LUNES, LUNES.plusDays(4), AMBOS_TURNOS, MANTENIMIENTO, "Pintura")).andExpect(status().isCreated());

        listar(personas.bearer(RolCodigo.COORDINACION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].desde").value("2033-04-04"))
                .andExpect(jsonPath("$[0].hasta").value("2033-04-08"))
                .andExpect(jsonPath("$[0].salones[*].nombre").value(hasItem("Club de Campo")))
                .andExpect(jsonPath("$[0].turnos", hasSize(2)))
                .andExpect(jsonPath("$[0].motivo.nombre").value("Mantenimiento"))
                .andExpect(jsonPath("$[0].detalle").value("Pintura"))
                .andExpect(jsonPath("$[1].desde").value("2033-04-24"))
                .andExpect(jsonPath("$[1].motivo.nombre").value("Feriado"));
    }

    @Test
    void laListaNoMuestraLosDesbloqueadosNiLosQueYaPasaron() throws Exception {
        long desbloqueado = escenario.bloqueo("avril", LUNES, "noche", gabriela, null);
        escenario.bloqueo("club", HOY.minusDays(3), "noche", gabriela, "Ya pasó");
        long vigente = escenario.bloqueo("santa-barbara", LUNES, "noche", gabriela, "Sigue");
        desbloquear(gabriela, desbloqueado).andExpect(status().isNoContent());

        listar(personas.bearer(gabriela))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(vigente))
                .andExpect(jsonPath("$[0].detalle").value("Sigue"));
    }

    // ---------- desbloquear ----------

    @Test
    void desbloquearLiberaLasUnidadesYDejaLaHistoria() throws Exception {
        long id = JsonPath.parse(registrar(gabriela, cuerpo("[1]", LUNES, null, "[2]", MANTENIMIENTO, null))
                .andReturn().getResponse().getContentAsString()).read("$.id", Long.class);
        Usuario coordinacion = personas.de(RolCodigo.COORDINACION);

        desbloquear(coordinacion, id).andExpect(status().isNoContent());
        entityManager.flush();

        var fila = jdbc.queryForMap("SELECT activo, usuario_desbloqueo_id, fecha_desbloqueo FROM bloqueo WHERE bloqueo_id = ?", id);
        assertThat(fila).containsEntry("activo", false).containsEntry("usuario_desbloqueo_id", coordinacion.getId());
        assertThat(fila.get("fecha_desbloqueo")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo_unidad WHERE bloqueo_id = ?", Integer.class, id)).isEqualTo(1);
        listar(personas.bearer(gabriela)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/agenda").param("mes", "2033-04").header(HttpHeaders.AUTHORIZATION, personas.bearer(lucia)))
                .andExpect(jsonPath("$.unidades", hasSize(0)));
    }

    @Test
    void conLaUnidadBloqueadaNoSePreReserva() throws Exception {
        escenario.bloqueo("santa-barbara", LUNES, "noche", gabriela, null);

        preReservar(lucia, 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("UNIDAD_BLOQUEADA"));
    }

    @Test
    void desbloqueadaLaUnidadSePuedePreReservar() throws Exception {
        long id = escenario.bloqueo("santa-barbara", LUNES, "noche", gabriela, null);
        desbloquear(gabriela, id).andExpect(status().isNoContent());

        preReservar(lucia, 3).andExpect(status().isCreated());
    }

    @Test
    void desbloquearUnBloqueoInexistenteDevuelve404() throws Exception {
        desbloquear(gabriela, 987654321L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("BLOQUEO_INEXISTENTE"));
    }

    @Test
    void desbloquearDosVecesDevuelve409() throws Exception {
        long id = escenario.bloqueo("avril", LUNES, "noche", gabriela, null);
        desbloquear(gabriela, id).andExpect(status().isNoContent());

        desbloquear(gabriela, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("BLOQUEO_YA_DESBLOQUEADO"));
    }

    // ---------- ayudas ----------

    private int cantidadDeBloqueosDeAbril2033() {
        return jdbc.queryForObject("SELECT count(*) FROM bloqueo b JOIN bloqueo_unidad bu USING (bloqueo_id) JOIN unidad_comercializable u USING (unidad_id) WHERE u.fecha BETWEEN ? AND ?",
                Integer.class, LUNES.minusDays(1), LUNES.plusDays(400));
    }

    private ResultActions registrar(Usuario quien, String json) throws Exception {
        return registrar(personas.bearer(quien), json);
    }

    private ResultActions registrar(String bearer, String json) throws Exception {
        return mvc.perform(post("/api/v1/bloqueos").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions listar(String bearer) throws Exception {
        return mvc.perform(get("/api/v1/bloqueos").header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions desbloquear(Usuario quien, long id) throws Exception {
        return desbloquear(personas.bearer(quien), id);
    }

    private ResultActions desbloquear(String bearer, long id) throws Exception {
        return mvc.perform(post("/api/v1/bloqueos/{id}/desbloqueo", id).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions preReservar(Usuario quien, int salonId) throws Exception {
        return mvc.perform(post("/api/v1/eventos").header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"salonId":%d,"fecha":"%s","turnoId":2,"tipoEventoId":2,"cliente":{"nombre":"Cliente de prueba"}}"""
                        .formatted(salonId, LUNES)));
    }

    private static String cuerpo(String salones, LocalDate desde, LocalDate hasta, String turnos, String motivo, String detalle) {
        return """
                {"salonIds":%s,"desde":"%s","hasta":%s,"turnoIds":%s,"motivoId":%s,"detalle":%s}"""
                .formatted(salones, desde, hasta == null ? "null" : "\"" + hasta + "\"", turnos, motivo,
                        detalle == null ? "null" : "\"" + detalle + "\"");
    }
}
