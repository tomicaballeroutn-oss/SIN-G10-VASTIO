package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Registrar cierre de evento (UI-33, docs/sprint-3-operacion.md, decisiones 12 a 14). Reloj fijo: jornada del 12/03/2031.
 * El evento (150 invitados) ya retiró 12 botellas de ron y 6 de gin a la Barra Avril (id 2); en la devolución vuelven 6
 * de ron y quedan 2, y del gin no queda nada: se consumieron 4 de ron y 6 de gin. Cada test deshace sus cambios.
 */
@PruebaDeIntegracion
@Transactional
class CierreIT {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);
    private static final LocalDate JORNADA = LocalDate.of(2031, 3, 12);
    private static final int DEPOSITO = 1;
    private static final int BARRA_AVRIL = 2;

    @MockitoBean
    Clock reloj;

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

    long evento;
    long ron;
    long gin;

    @BeforeEach
    void escenario() throws Exception {
        when(reloj.getZone()).thenReturn(ZONA);
        when(reloj.instant()).thenReturn(LocalDateTime.of(JORNADA, java.time.LocalTime.of(22, 0)).atZone(ZONA).toInstant());
        evento = escenario.evento("avril", JORNADA, "noche", "EN_CURSO", personas.de(RolCodigo.VENDEDORA),
                personas.de(RolCodigo.PLANNER), "Quince de Sofía");
        ron = crearBebida("Ron de cierre");
        gin = crearBebida("Gin de cierre");
        saldo(DEPOSITO, ron, 48);
        saldo(DEPOSITO, gin, 24);
        pedir(post("/api/v1/eventos/{id}/entregas/{barra}/retiros", evento, BARRA_AVRIL), RolCodigo.BARRA, """
                {"renglones":[{"bebidaId":%d,"cantidad":12},{"bebidaId":%d,"cantidad":6}]}""".formatted(ron, gin))
                .andExpect(status().isCreated());
    }

    // ---------- conciliación ----------

    @Test
    void muestraPorBebidaEntregadoDevueltoRemanenteConsumoYPorAsistente() throws Exception {
        devolverYTerminar();

        pedir(get("/api/v1/eventos/{id}/cierre", evento), RolCodigo.BARRA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cerrado").value(false))
                .andExpect(jsonPath("$.pendientes").isEmpty())
                .andExpect(jsonPath("$.invitados").value(150))
                .andExpect(jsonPath("$.usaSegmentos").value(false))
                .andExpect(jsonPath(renglon(ron, "entregado")).value(hasItem(12.0)))
                .andExpect(jsonPath(renglon(ron, "devuelto")).value(hasItem(6.0)))
                .andExpect(jsonPath(renglon(ron, "remanente")).value(hasItem(2.0)))
                .andExpect(jsonPath(renglon(ron, "consumo")).value(hasItem(4.0)))
                .andExpect(jsonPath(renglon(ron, "porAsistente")).value(hasItem(0.0267)))
                .andExpect(jsonPath(renglon(gin, "consumo")).value(hasItem(6.0)))
                .andExpect(jsonPath("$.consumoTotal").value(10.0))
                .andExpect(jsonPath("$.porAsistenteTotal").value(0.0667));
    }

    @Test
    void conUnTipoDeEventoConSegmentosDividePorLosInvitadosYLoAclara() throws Exception {
        devolverYTerminar();
        jdbc.update("UPDATE evento SET tipo_evento_id = 4 WHERE evento_id = ?", evento); // Egresados
        entityManager.clear();

        pedir(get("/api/v1/eventos/{id}/cierre", evento), RolCodigo.BARRA)
                .andExpect(jsonPath("$.usaSegmentos").value(true))
                .andExpect(jsonPath("$.porAsistenteTotal").value(0.0667));
    }

    // ---------- cierre ----------

    @Test
    void alCerrarCongelaElConsumoPasaACerradoYLaBarraQuedaEnElRemanente() throws Exception {
        devolverYTerminar();
        Usuario barra = personas.de(RolCodigo.BARRA);

        pedir(post("/api/v1/eventos/{id}/cierre", evento), barra, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cerrado").value(true))
                .andExpect(jsonPath("$.evento.estado").value("CERRADO"))
                .andExpect(jsonPath(renglon(ron, "consumo")).value(hasItem(4.0)));
        entityManager.flush();

        List<Map<String, Object>> congelado = jdbc.queryForList("""
                SELECT bebida_id, cantidad_entregada, cantidad_devuelta, cantidad_remanente, cantidad_consumida
                FROM consumo_evento WHERE evento_id = ? ORDER BY bebida_id""", evento);
        assertThat(congelado).hasSize(2);
        Map<String, Object> deRon = congelado.stream().filter(c -> ((Number) c.get("bebida_id")).longValue() == ron).findFirst().orElseThrow();
        assertThat((BigDecimal) deRon.get("cantidad_entregada")).isEqualByComparingTo("12");
        assertThat((BigDecimal) deRon.get("cantidad_devuelta")).isEqualByComparingTo("6");
        assertThat((BigDecimal) deRon.get("cantidad_remanente")).isEqualByComparingTo("2");
        assertThat((BigDecimal) deRon.get("cantidad_consumida")).isEqualByComparingTo("4");
        assertThat(jdbc.queryForObject("SELECT estado FROM evento WHERE evento_id = ?", String.class, evento)).isEqualTo("CERRADO");
        Map<String, Object> historial = jdbc.queryForMap("""
                SELECT estado_anterior, usuario_id FROM cambio_estado_evento WHERE evento_id = ? AND estado_nuevo = 'CERRADO'""", evento);
        assertThat(historial).containsEntry("estado_anterior", "REALIZADO");
        assertThat(((Number) historial.get("usuario_id")).longValue()).isEqualTo(barra.getId());
        // El remanente queda en la barra para el evento siguiente; lo consumido ya salió con la devolución.
        assertThat(saldoDe(BARRA_AVRIL, ron)).isEqualByComparingTo("2");
        assertThat(saldoDe(BARRA_AVRIL, gin)).isEqualByComparingTo("0");
        // Lo congelado es lo que se muestra después.
        pedir(get("/api/v1/eventos/{id}/cierre", evento), RolCodigo.COMPRAS)
                .andExpect(jsonPath("$.cerrado").value(true))
                .andExpect(jsonPath(renglon(gin, "consumo")).value(hasItem(6.0)));
    }

    @Test
    void laOrdenDePreparacionQuedaCerrada() throws Exception {
        devolverYTerminar();
        long orden = jdbc.queryForObject("""
                INSERT INTO orden_preparacion (evento_id, estado, ubicacion_origen_id, ubicacion_destino_id, responsable_id, fecha_creacion)
                VALUES (?, 'CONFIRMADA', ?, ?, ?, now()) RETURNING orden_id""", Long.class,
                evento, DEPOSITO, BARRA_AVRIL, personas.de(RolCodigo.COMPRAS).getId());

        pedir(post("/api/v1/eventos/{id}/cierre", evento), RolCodigo.COMPRAS, null).andExpect(status().isOk());
        entityManager.flush();

        assertThat(jdbc.queryForObject("SELECT estado FROM orden_preparacion WHERE orden_id = ?", String.class, orden)).isEqualTo("CERRADA");
    }

    @Test
    void siFaltaUnaDevolucionNoSeCierraYDiceQueBarra() throws Exception {
        realizado(); // sin devolución: la entrega sigue abierta

        pedir(get("/api/v1/eventos/{id}/cierre", evento), RolCodigo.BARRA)
                .andExpect(jsonPath("$.pendientes[*].nombre", hasItem("Barra Avril")));
        pedir(post("/api/v1/eventos/{id}/cierre", evento), RolCodigo.BARRA, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DEVOLUCION_PENDIENTE"))
                .andExpect(jsonPath("$.detail").value("Falta registrar la devolución de Barra Avril. Registrala antes de cerrar el evento."));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consumo_evento WHERE evento_id = ?", Integer.class, evento)).isZero();
    }

    @Test
    void soloSeCierraUnEventoRealizado() throws Exception {
        pedir(post("/api/v1/eventos/{id}/entregas/{barra}/devolucion", evento, BARRA_AVRIL), RolCodigo.BARRA, "{\"renglones\":[]}")
                .andExpect(status().isCreated());

        pedir(post("/api/v1/eventos/{id}/cierre", evento), RolCodigo.BARRA, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_NO_PERMITE"))
                .andExpect(jsonPath("$.detail").value("El evento está en En curso: se cierra un evento realizado."));
    }

    @Test
    void unEventoCerradoNoSeVuelveACerrarNiAdmiteMovimientos() throws Exception {
        devolverYTerminar();
        pedir(post("/api/v1/eventos/{id}/cierre", evento), RolCodigo.BARRA, null).andExpect(status().isOk());

        pedir(post("/api/v1/eventos/{id}/cierre", evento), RolCodigo.BARRA, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EVENTO_CERRADO"));
        pedir(post("/api/v1/eventos/{id}/entregas/{barra}/retiros", evento, BARRA_AVRIL), RolCodigo.COMPRAS,
                "{\"renglones\":[{\"bebidaId\":%d,\"cantidad\":6}]}".formatted(ron))
                .andExpect(status().isConflict());
        pedir(post("/api/v1/eventos/{id}/entregas/{barra}/devolucion", evento, BARRA_AVRIL), RolCodigo.COMPRAS, "{\"renglones\":[]}")
                .andExpect(status().isConflict());
    }

    @Test
    void unEventoSinRetirosSeCierraSinConsumo() throws Exception {
        long sinBarra = escenario.evento("club", JORNADA, "mediodia", "REALIZADO", personas.de(RolCodigo.VENDEDORA),
                personas.de(RolCodigo.PLANNER), "Almuerzo sin barra");

        pedir(post("/api/v1/eventos/{id}/cierre", sinBarra), RolCodigo.COMPRAS, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.renglones").isEmpty())
                .andExpect(jsonPath("$.consumoTotal").value(0));
    }

    @Test
    void losRealizadosEsperanSuCierre() throws Exception {
        devolverYTerminar();

        pedir(get("/api/v1/cierres/pendientes"), RolCodigo.BARRA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].evento.id", hasItem((int) evento)));
    }

    // ---------- quién puede ----------

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"BARRA", "COMPRAS", "COORDINACION", "DIRECCION"})
    void loRegistranBarraComprasCoordinacionYDireccion(RolCodigo rol) throws Exception {
        devolverYTerminar();
        pedir(post("/api/v1/eventos/{id}/cierre", evento), rol, null).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "VENDEDORA", "PLANNER", "COCINA"})
    void elRestoRecibe403(RolCodigo rol) throws Exception {
        devolverYTerminar();
        pedir(post("/api/v1/eventos/{id}/cierre", evento), rol, null).andExpect(status().isForbidden());
        pedir(get("/api/v1/eventos/{id}/cierre", evento), rol).andExpect(status().isForbidden());
        pedir(get("/api/v1/cierres/pendientes"), rol).andExpect(status().isForbidden());
    }

    // ---------- auxiliares ----------

    /** Vuelven 6 de ron y quedan 2; del gin no queda nada. Después termina el turno. */
    private void devolverYTerminar() throws Exception {
        pedir(post("/api/v1/eventos/{id}/entregas/{barra}/devolucion", evento, BARRA_AVRIL), RolCodigo.BARRA,
                "{\"renglones\":[{\"bebidaId\":%d,\"devuelto\":6,\"remanente\":2}]}".formatted(ron))
                .andExpect(status().isCreated());
        realizado();
    }

    /** Como si el proceso automático lo hubiera pasado a Realizado en otro pedido. */
    private void realizado() {
        entityManager.flush();
        jdbc.update("UPDATE evento SET estado = 'REALIZADO' WHERE evento_id = ?", evento);
        entityManager.clear();
    }

    private static String renglon(long bebida, String campo) {
        return "$.renglones[?(@.bebidaId == %d)].%s".formatted(bebida, campo);
    }

    private ResultActions pedir(MockHttpServletRequestBuilder pedido, RolCodigo rol) throws Exception {
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)));
    }

    private ResultActions pedir(MockHttpServletRequestBuilder pedido, RolCodigo rol, String json) throws Exception {
        return pedir(pedido, personas.de(rol), json);
    }

    private ResultActions pedir(MockHttpServletRequestBuilder pedido, Usuario quien, String json) throws Exception {
        pedido.header(HttpHeaders.AUTHORIZATION, personas.bearer(quien));
        if (json != null) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(pedido);
    }

    private void saldo(int ubicacion, long bebida, int cantidad) {
        jdbc.update("""
                INSERT INTO stock_ubicacion (ubicacion_id, bebida_id, cantidad, fecha_actualizacion) VALUES (?, ?, ?, now())
                ON CONFLICT (ubicacion_id, bebida_id) DO UPDATE SET cantidad = stock_ubicacion.cantidad + EXCLUDED.cantidad""",
                ubicacion, bebida, cantidad);
    }

    private BigDecimal saldoDe(int ubicacion, long bebida) {
        return jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id = ? AND bebida_id = ?",
                BigDecimal.class, ubicacion, bebida);
    }

    private long crearBebida(String nombre) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/bebidas").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.ADMINISTRACION))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombre":"%s","presentacion":"750 ml","tipoId":3,"unidadId":1,"unidadesPorBulto":6}"""
                                .formatted(nombre)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }
}
