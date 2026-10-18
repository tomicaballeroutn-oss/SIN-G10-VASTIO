package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.junit.jupiter.params.provider.ValueSource;
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
 * Registrar devolución de bebida (UI-32, docs/sprint-3-operacion.md, decisiones 8 a 11). El reloj está fijo: la jornada
 * del test es el 12/03/2031 a las 22:00, con un evento en curso en Avril al que ya se le entregaron 12 botellas de ron
 * (2 cajas) y 6 de gin (1 caja) a la Barra Avril (id 2), que se abastece del depósito madre (id 1). Cada test deshace
 * sus cambios.
 */
@PruebaDeIntegracion
@Transactional
class DevolucionIT {

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
        ahora(JORNADA.atTime(22, 0));
        evento = escenario.evento("avril", JORNADA, "noche", "EN_CURSO", personas.de(RolCodigo.VENDEDORA),
                personas.de(RolCodigo.PLANNER), "Quince de Sofía");
        ron = crearBebida("Ron de devolución");
        gin = crearBebida("Gin de devolución");
        saldo(DEPOSITO, ron, 48);
        saldo(DEPOSITO, gin, 24);
    }

    // ---------- la cuenta del consumo ----------

    @Test
    void registraLoQueVuelveYLoQueQuedaCierraLaEntregaYRecienAhoraDaElConsumo() throws Exception {
        Usuario barra = personas.de(RolCodigo.BARRA);
        retirar(ron, 12, gin, 6);

        // Cuenta de control: salieron 12, vuelven 6, quedan 2 → se consumieron 4. Del gin no se declara nada: se consumió todo.
        devolver(barra, "{\"renglones\":[{\"bebidaId\":%d,\"devuelto\":6,\"remanente\":2}]}".formatted(ron))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entrega.estadoEntrega").value("CERRADA"))
                .andExpect(jsonPath("$.consumo[?(@.bebidaId == %d)].entregado".formatted(ron)).value(hasItem(12.0)))
                .andExpect(jsonPath("$.consumo[?(@.bebidaId == %d)].devuelto".formatted(ron)).value(hasItem(6.0)))
                .andExpect(jsonPath("$.consumo[?(@.bebidaId == %d)].remanente".formatted(ron)).value(hasItem(2.0)))
                .andExpect(jsonPath("$.consumo[?(@.bebidaId == %d)].consumo".formatted(ron)).value(hasItem(4.0)))
                .andExpect(jsonPath("$.consumo[?(@.bebidaId == %d)].consumo".formatted(gin)).value(hasItem(6.0)))
                .andExpect(jsonPath("$.entrega.aDevolver").isEmpty());
        entityManager.flush();

        List<Map<String, Object>> asientos = jdbc.queryForList("""
                SELECT tipo, cantidad, ubicacion_origen_id, ubicacion_destino_id, usuario_id, entrega_id
                FROM movimiento_stock WHERE evento_id = ? AND bebida_id = ? AND tipo IN ('DEVOLUCION', 'REMANENTE')
                ORDER BY movimiento_id""", evento, ron);
        assertThat(asientos).hasSize(2);
        assertThat(asientos.get(0)).containsEntry("tipo", "DEVOLUCION");
        assertThat(((Number) asientos.get(0).get("ubicacion_origen_id")).intValue()).isEqualTo(BARRA_AVRIL);
        assertThat(((Number) asientos.get(0).get("ubicacion_destino_id")).intValue()).isEqualTo(DEPOSITO);
        assertThat((BigDecimal) asientos.get(0).get("cantidad")).isEqualByComparingTo("6");
        assertThat(asientos.get(1)).containsEntry("tipo", "REMANENTE");
        assertThat(((Number) asientos.get(1).get("ubicacion_origen_id")).intValue()).isEqualTo(BARRA_AVRIL);
        assertThat(((Number) asientos.get(1).get("ubicacion_destino_id")).intValue()).isEqualTo(BARRA_AVRIL);
        assertThat((BigDecimal) asientos.get(1).get("cantidad")).isEqualByComparingTo("2");
        assertThat(asientos).allSatisfy(m -> assertThat(((Number) m.get("usuario_id")).longValue()).isEqualTo(barra.getId()));
        Map<String, Object> entrega = jdbc.queryForMap("SELECT estado, usuario_cierre_id, fecha_cierre FROM entrega WHERE evento_id = ?", evento);
        assertThat(entrega).containsEntry("estado", "CERRADA");
        assertThat(((Number) entrega.get("usuario_cierre_id")).longValue()).isEqualTo(barra.getId());
        assertThat(entrega.get("fecha_cierre")).isNotNull();
        // Lo devuelto vuelve al madre; en la barra queda lo no devuelto hasta el cierre (remanente + consumo).
        assertThat(saldoDe(DEPOSITO, ron)).isEqualByComparingTo("42");
        assertThat(saldoDe(BARRA_AVRIL, ron)).isEqualByComparingTo("6");
    }

    @Test
    void elRemanenteHeredadoEntraEnLoEntregado() throws Exception {
        saldo(BARRA_AVRIL, ron, 3); // quedó del evento anterior
        retirar(ron, 12);

        devolver(RolCodigo.BARRA, "{\"renglones\":[{\"bebidaId\":%d,\"devuelto\":6,\"remanente\":4}]}".formatted(ron))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consumo[?(@.bebidaId == %d)].entregado".formatted(ron)).value(hasItem(15.0)))
                .andExpect(jsonPath("$.consumo[?(@.bebidaId == %d)].consumo".formatted(ron)).value(hasItem(5.0)));
    }

    @Test
    void siLoQueVuelveYQuedaSuperaLoDeLaBarraSeRechazaNombrandoLaBebida() throws Exception {
        retirar(ron, 12);

        devolver(RolCodigo.BARRA, "{\"renglones\":[{\"bebidaId\":%d,\"devuelto\":10,\"remanente\":3}]}".formatted(ron))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SUPERA_LO_DE_LA_BARRA"))
                .andExpect(jsonPath("$.detail").value(
                        "Ron de devolución 750 ml: lo que vuelve y lo que queda suman más de lo que hay en la barra. Revisá el conteo."))
                // No dice cuánto hay.
                .andExpect(jsonPath("$.detail", not(containsString("12"))));
        assertThat(jdbc.queryForObject("SELECT estado FROM entrega WHERE evento_id = ?", String.class, evento)).isEqualTo("ABIERTA");
    }

    // ---------- antes de registrar: a ciegas ----------

    @Test
    void antesDeRegistrarListaLasBebidasDeLaBarraSinNingunaCantidad() throws Exception {
        saldo(BARRA_AVRIL, gin, 1);
        retirar(ron, 12);

        pedir(get("/api/v1/eventos/{id}/entregas/{barra}", evento, BARRA_AVRIL), RolCodigo.BARRA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aDevolver[*].id", hasItems((int) ron, (int) gin)))
                .andExpect(jsonPath("$.aDevolver[*].cantidad").isEmpty())
                .andExpect(content().string(not(containsString("saldo"))))
                .andExpect(content().string(not(containsString("entregado\""))));
    }

    // ---------- estado del evento y de la entrega ----------

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMADO", "CERRADO"})
    void soloConElEventoEnCursoORealizado(String estado) throws Exception {
        retirar(ron, 12);
        entityManager.flush();
        jdbc.update("UPDATE evento SET estado = ? WHERE evento_id = ?", estado, evento);
        entityManager.clear();

        devolver(RolCodigo.COMPRAS, "{\"renglones\":[]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_NO_PERMITE"))
                .andExpect(jsonPath("$.detail", containsString("la devolución se registra con el evento en curso o realizado")));
    }

    @Test
    void conElEventoRealizadoSeRegistra() throws Exception {
        retirar(ron, 12);
        entityManager.flush();
        jdbc.update("UPDATE evento SET estado = 'REALIZADO' WHERE evento_id = ?", evento);
        entityManager.clear();

        devolver(RolCodigo.BARRA, "{\"renglones\":[{\"bebidaId\":%d,\"devuelto\":12,\"remanente\":0}]}".formatted(ron))
                .andExpect(status().isCreated());
    }

    @Test
    void sinRetirosNoHayNadaQueDevolver() throws Exception {
        devolver(RolCodigo.BARRA, "{\"renglones\":[]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SIN_ENTREGA"));
    }

    @Test
    void laDevolucionSeRegistraUnaSolaVezYDespuesNoAdmiteRetiros() throws Exception {
        retirar(ron, 12);
        devolver(RolCodigo.BARRA, "{\"renglones\":[]}").andExpect(status().isCreated());

        devolver(RolCodigo.BARRA, "{\"renglones\":[]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DEVOLUCION_REGISTRADA"));
        mvc.perform(post("/api/v1/eventos/{id}/entregas/{barra}/retiros", evento, BARRA_AVRIL)
                        .header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.BARRA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"renglones\":[{\"bebidaId\":%d,\"cantidad\":6}]}".formatted(ron)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENTREGA_CERRADA"));
    }

    @Test
    void validaLosRenglones() throws Exception {
        retirar(ron, 12);
        long otra = crearBebida("Vodka que no está en la barra");

        devolver(RolCodigo.BARRA, "{\"renglones\":[{\"bebidaId\":%d,\"devuelto\":1,\"remanente\":0}]}".formatted(otra))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("BEBIDA_FUERA_DE_LA_BARRA"));
        devolver(RolCodigo.BARRA, "{\"renglones\":[{\"bebidaId\":%d,\"devuelto\":1.5,\"remanente\":0}]}".formatted(ron))
                .andExpect(status().isBadRequest());
        devolver(RolCodigo.BARRA, "{\"renglones\":[{\"bebidaId\":%d,\"devuelto\":-1,\"remanente\":0}]}".formatted(ron))
                .andExpect(status().isBadRequest());
        devolver(RolCodigo.BARRA, "{\"renglones\":[],\"transicion\":[{\"bebidaId\":%d,\"cantidad\":1}]}".formatted(ron))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SIN_TRANSICION"));
    }

    // ---------- depósito de transición ----------

    @Test
    void loQueQuedaEnLaTransicionVuelveAlDepositoMadre() throws Exception {
        int transicion = crearTransicion("Transición Avril");
        jdbc.update("UPDATE ubicacion SET ubicacion_abastecimiento_id = ? WHERE ubicacion_id = ?", transicion, BARRA_AVRIL);
        saldo(transicion, ron, 24);
        retirar(ron, 12); // sale de la transición: quedan 12 ahí

        pedir(get("/api/v1/eventos/{id}/entregas/{barra}", evento, BARRA_AVRIL), RolCodigo.BARRA)
                .andExpect(jsonPath("$.enTransicion[*].id", hasItem((int) ron)));
        devolver(RolCodigo.BARRA, """
                {"renglones":[{"bebidaId":%d,"devuelto":6,"remanente":0}],"transicion":[{"bebidaId":%d,"cantidad":10}]}"""
                .formatted(ron, ron))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.desdeTransicion[?(@.bebidaId == %d)].cantidad".formatted(ron)).value(hasItem(10.0)));
        entityManager.flush();

        assertThat(saldoDe(transicion, ron)).isEqualByComparingTo("2");
        assertThat(saldoDe(DEPOSITO, ron)).isEqualByComparingTo("64"); // 48 + 6 de la barra + 10 de la transición
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM movimiento_stock WHERE evento_id = ? AND tipo = 'DEVOLUCION' AND ubicacion_origen_id = ?""",
                Integer.class, evento, transicion)).isOne();
    }

    // ---------- quién puede ----------

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"BARRA", "COMPRAS", "COORDINACION", "DIRECCION"})
    void laRegistranBarraComprasCoordinacionYDireccion(RolCodigo rol) throws Exception {
        retirar(ron, 12);
        devolver(rol, "{\"renglones\":[]}").andExpect(status().isCreated());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "VENDEDORA", "PLANNER", "COCINA"})
    void elRestoRecibe403(RolCodigo rol) throws Exception {
        retirar(ron, 12);
        devolver(rol, "{\"renglones\":[]}").andExpect(status().isForbidden());
    }

    @Test
    void pasadaLaJornadaLaBarraTodaviaDevuelveSiLaEntregaSigueAbierta() throws Exception {
        retirar(ron, 12);
        ahora(JORNADA.plusDays(1).atTime(7, 30)); // ya es otra jornada

        pedir(get("/api/v1/entregas/jornada"), RolCodigo.BARRA)
                .andExpect(jsonPath("$[*].evento.id", hasItem((int) evento)));
        devolver(RolCodigo.BARRA, "{\"renglones\":[]}").andExpect(status().isCreated());
        // Cerrada la entrega y fuera de la jornada, ya no la opera.
        pedir(get("/api/v1/eventos/{id}/entregas/{barra}", evento, BARRA_AVRIL), RolCodigo.BARRA).andExpect(status().isForbidden());
    }

    // ---------- auxiliares ----------

    /** Retiro de la barra en pares bebida, botellas. */
    private void retirar(long... bebidaYCantidad) throws Exception {
        StringBuilder lista = new StringBuilder();
        for (int i = 0; i < bebidaYCantidad.length; i += 2) {
            lista.append(lista.isEmpty() ? "" : ",")
                    .append("{\"bebidaId\":%d,\"cantidad\":%d}".formatted(bebidaYCantidad[i], bebidaYCantidad[i + 1]));
        }
        mvc.perform(post("/api/v1/eventos/{id}/entregas/{barra}/retiros", evento, BARRA_AVRIL)
                        .header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.BARRA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"renglones\":[%s]}".formatted(lista)))
                .andExpect(status().isCreated());
    }

    private ResultActions devolver(RolCodigo rol, String json) throws Exception {
        return devolver(personas.de(rol), json);
    }

    private ResultActions devolver(Usuario quien, String json) throws Exception {
        return mvc.perform(post("/api/v1/eventos/{id}/entregas/{barra}/devolucion", evento, BARRA_AVRIL)
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions pedir(MockHttpServletRequestBuilder pedido, RolCodigo rol) throws Exception {
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)));
    }

    private void ahora(LocalDateTime momento) {
        when(reloj.getZone()).thenReturn(ZONA);
        when(reloj.instant()).thenReturn(momento.atZone(ZONA).toInstant());
    }

    /** Suma al saldo (crea la fila si no está). */
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

    private int crearTransicion(String nombre) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/ubicaciones").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.ADMINISTRACION))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombre":"%s","tipo":"TRANSICION","salonId":1}""".formatted(nombre)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.id")).intValue();
    }
}
