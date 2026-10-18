package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
 * Registrar salida a barra (UI-31) y retiro adicional (docs/sprint-3-operacion.md, decisiones 3 a 7). El reloj está
 * fijo: la jornada del test es el 12/03/2031 a las 22:00, con un evento en curso en Avril (Barra Avril, id 2, se
 * abastece del depósito madre, id 1). Cada test deshace sus cambios.
 */
@PruebaDeIntegracion
@Transactional
class SalidaBarraIT {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);
    private static final LocalDate JORNADA = LocalDate.of(2031, 3, 12);
    private static final int DEPOSITO = 1;
    private static final int BARRA_AVRIL = 2;
    private static final int BARRA_CLUB = 3;

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

    Usuario vendedora;
    Usuario planner;
    long evento;
    long ron;
    long gin;

    @BeforeEach
    void escenario() throws Exception {
        ahora(JORNADA.atTime(22, 0));
        vendedora = personas.de(RolCodigo.VENDEDORA);
        planner = personas.de(RolCodigo.PLANNER);
        evento = escenario.evento("avril", JORNADA, "noche", "EN_CURSO", vendedora, planner, "Quince de Sofía");
        ron = crearBebida("Ron de salida");
        gin = crearBebida("Gin de salida");
        saldo(DEPOSITO, ron, 48);
        saldo(DEPOSITO, gin, 24);
    }

    // ---------- entrega y retiro adicional ----------

    @Test
    void elPrimerRetiroAbreLaEntregaYRegistraSalidaABarra() throws Exception {
        Usuario barra = personas.de(RolCodigo.BARRA);

        retirar(barra, evento, BARRA_AVRIL, false, renglon(ron, 12), renglon(gin, 6))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoEntrega").value("ABIERTA"))
                .andExpect(jsonPath("$.barra.nombre").value("Barra Avril"))
                .andExpect(jsonPath("$.origen.nombre").value("Depósito principal"))
                .andExpect(jsonPath("$.retiros", hasSize(1)))
                .andExpect(jsonPath("$.retiros[0].tipo").value("SALIDA_BARRA"))
                .andExpect(jsonPath("$.retiros[0].usuario.nombre").value("Prueba barra"))
                .andExpect(jsonPath("$.retiros[0].renglones", hasSize(2)))
                .andExpect(jsonPath("$.yaEntregado[?(@.bebidaId == %d)].cantidad".formatted(ron)).value(hasItem(12.0)));

        List<Map<String, Object>> asientos = jdbc.queryForList("""
                SELECT m.tipo, m.cantidad, m.ubicacion_origen_id, m.ubicacion_destino_id, m.usuario_id, m.entrega_id, e.estado
                FROM movimiento_stock m JOIN entrega e USING (entrega_id)
                WHERE m.evento_id = ? AND m.bebida_id = ?""", evento, ron);
        assertThat(asientos).singleElement().satisfies(m -> {
            assertThat(m.get("tipo")).isEqualTo("SALIDA_BARRA");
            assertThat((BigDecimal) m.get("cantidad")).isEqualByComparingTo("12");
            assertThat(((Number) m.get("ubicacion_origen_id")).intValue()).isEqualTo(DEPOSITO);
            assertThat(((Number) m.get("ubicacion_destino_id")).intValue()).isEqualTo(BARRA_AVRIL);
            assertThat(((Number) m.get("usuario_id")).longValue()).isEqualTo(barra.getId());
            assertThat(m.get("estado")).isEqualTo("ABIERTA");
        });
        assertThat(saldoDe(DEPOSITO, ron)).isEqualByComparingTo("36");
        assertThat(saldoDe(BARRA_AVRIL, ron)).isEqualByComparingTo("12");
    }

    @Test
    void losSiguientesSonRetirosAdicionalesEnLaMismaEntrega() throws Exception {
        retirar(RolCodigo.BARRA, evento, BARRA_AVRIL, renglon(ron, 12)).andExpect(status().isCreated());

        retirar(RolCodigo.BARRA, evento, BARRA_AVRIL, renglon(ron, 6), renglon(gin, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retiros[*].tipo", contains("SALIDA_BARRA", "RETIRO_ADICIONAL")))
                .andExpect(jsonPath("$.yaEntregado[?(@.bebidaId == %d)].cantidad".formatted(ron)).value(hasItem(18.0)))
                .andExpect(jsonPath("$.yaEntregado[?(@.bebidaId == %d)].cantidad".formatted(gin)).value(hasItem(1.0)));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM entrega WHERE evento_id = ?", Integer.class, evento)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(DISTINCT entrega_id) FROM movimiento_stock WHERE evento_id = ? AND tipo = 'RETIRO_ADICIONAL'""",
                Integer.class, evento)).isOne();
        pedir(get("/api/v1/eventos/{id}/entregas/{barra}", evento, BARRA_AVRIL), RolCodigo.BARRA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retiros", hasSize(2)));
    }

    @Test
    void laBarraNuncaRecibeElSaldoDelOrigen() throws Exception {
        retirar(RolCodigo.BARRA, evento, BARRA_AVRIL, renglon(ron, 12))
                .andExpect(status().isCreated())
                .andExpect(content().string(not(containsString("saldo"))))
                .andExpect(content().string(not(containsString("stockMinimo"))))
                // Lo único con cantidad es lo entregado (12): ni lo que quedó en el depósito (36) ni otro saldo.
                .andExpect(jsonPath("$..cantidad", not(hasItem(36.0))));
        pedir(get("/api/v1/eventos/{id}/entregas/{barra}", evento, BARRA_AVRIL), RolCodigo.BARRA)
                .andExpect(content().string(not(containsString("saldo"))))
                .andExpect(content().string(not(containsString("stockMinimo"))))
                .andExpect(jsonPath("$..cantidad", not(hasItem(36.0))));
    }

    @Test
    void unSaldoNegativoSeAceptaYAvisaAComprasYAdministracion() throws Exception {
        Usuario barra = personas.de(RolCodigo.BARRA);
        Usuario compras = personas.de(RolCodigo.COMPRAS);
        Usuario administracion = personas.de(RolCodigo.ADMINISTRACION);
        saldo(DEPOSITO, ron, -46); // queda en 2

        retirar(barra, evento, BARRA_AVRIL, false, renglon(ron, 6)).andExpect(status().isCreated());
        entityManager.flush();

        assertThat(saldoDe(DEPOSITO, ron)).isEqualByComparingTo("-4");
        Long aviso = jdbc.queryForObject("""
                SELECT notificacion_id FROM notificacion WHERE tipo = 'ALERTA_STOCK' AND mensaje LIKE '%Ron de salida%'""", Long.class);
        List<Long> destinatarios = jdbc.queryForList("SELECT usuario_id FROM notificacion_destinatario WHERE notificacion_id = ?",
                Long.class, aviso);
        assertThat(destinatarios).contains(compras.getId(), administracion.getId()).doesNotContain(barra.getId());
        assertThat(jdbc.queryForObject("SELECT mensaje FROM notificacion WHERE notificacion_id = ?", String.class, aviso))
                .isEqualTo("Saldo negativo en Depósito principal después de una salida a Barra Avril: Ron de salida 750 ml. "
                        + "Falta registrar un movimiento.");
    }

    @Test
    void sinSaldoNegativoNoAvisa() throws Exception {
        retirar(RolCodigo.BARRA, evento, BARRA_AVRIL, renglon(ron, 6)).andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo = 'ALERTA_STOCK' AND mensaje LIKE '%Ron de salida%'",
                Integer.class)).isZero();
    }

    // ---------- estado del evento y de la barra ----------

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMADO", "REALIZADO", "CERRADO"})
    void soloConElEventoEnCurso(String estado) throws Exception {
        jdbc.update("UPDATE evento SET estado = ? WHERE evento_id = ?", estado, evento);
        String nombre = Map.of("CONFIRMADO", "Confirmado", "REALIZADO", "Realizado", "CERRADO", "Cerrado").get(estado);

        retirar(RolCodigo.COMPRAS, evento, BARRA_AVRIL, renglon(ron, 6))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_NO_PERMITE"))
                .andExpect(jsonPath("$.detail").value("El evento está en %s: la salida a barra se registra con el evento en curso."
                        .formatted(nombre)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE evento_id = ?", Integer.class, evento)).isZero();
    }

    @Test
    void unaBarraNoRecibeAOtroEventoHastaLaDevolucionDelAnterior() throws Exception {
        long almuerzo = escenario.evento("avril", JORNADA, "mediodia", "EN_CURSO", vendedora, planner, "Almuerzo de Arcor");
        retirar(RolCodigo.BARRA, almuerzo, BARRA_AVRIL, renglon(ron, 6)).andExpect(status().isCreated());

        retirar(RolCodigo.BARRA, evento, BARRA_AVRIL, renglon(ron, 6))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENTREGA_ABIERTA_EN_LA_BARRA"))
                .andExpect(jsonPath("$.detail").value("Primero registrá la devolución de Almuerzo de Arcor."));
    }

    @Test
    void unaEntregaCerradaNoAdmiteMasRetiros() throws Exception {
        retirar(RolCodigo.BARRA, evento, BARRA_AVRIL, renglon(ron, 6)).andExpect(status().isCreated());
        // Como si la devolución la hubiera registrado otro pedido: la entrega se vuelve a leer de la base.
        entityManager.flush();
        jdbc.update("UPDATE entrega SET estado = 'CERRADA' WHERE evento_id = ?", evento);
        entityManager.clear();

        retirar(RolCodigo.BARRA, evento, BARRA_AVRIL, renglon(ron, 6))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENTREGA_CERRADA"))
                .andExpect(jsonPath("$.detail").value("La entrega a Barra Avril ya se cerró con la devolución: no admite más retiros."));
    }

    // ---------- origen: abastecimiento y retiro directo ----------

    @Test
    void saleDeLaTransicionYConRetiroDirectoDelDepositoMadre() throws Exception {
        int transicion = crearTransicion("Transición Avril");
        jdbc.update("UPDATE ubicacion SET ubicacion_abastecimiento_id = ?, permite_retiro_directo = true WHERE ubicacion_id = ?",
                transicion, BARRA_AVRIL);
        saldo(transicion, ron, 12);

        pedir(get("/api/v1/eventos/{id}/entregas/{barra}", evento, BARRA_AVRIL), RolCodigo.BARRA)
                .andExpect(jsonPath("$.origen.nombre").value("Transición Avril"))
                .andExpect(jsonPath("$.depositoMadre.nombre").value("Depósito principal"));
        retirar(RolCodigo.BARRA, evento, BARRA_AVRIL, renglon(ron, 6)).andExpect(status().isCreated());
        retirar(personas.de(RolCodigo.BARRA), evento, BARRA_AVRIL, true, renglon(ron, 12))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.retiros[1].origen.nombre").value("Depósito principal"));

        assertThat(saldoDe(transicion, ron)).isEqualByComparingTo("6");
        assertThat(saldoDe(DEPOSITO, ron)).isEqualByComparingTo("36");
        assertThat(saldoDe(BARRA_AVRIL, ron)).isEqualByComparingTo("18");
    }

    @Test
    void sinPermisoDeRetiroDirectoNoSaleDelDepositoMadre() throws Exception {
        pedir(get("/api/v1/eventos/{id}/entregas/{barra}", evento, BARRA_AVRIL), RolCodigo.BARRA)
                .andExpect(jsonPath("$.depositoMadre").doesNotExist());

        retirar(personas.de(RolCodigo.BARRA), evento, BARRA_AVRIL, true, renglon(ron, 6))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SIN_RETIRO_DIRECTO"));
    }

    // ---------- renglones ----------

    @Test
    void validaLosRenglones() throws Exception {
        retirar(RolCodigo.BARRA, evento, BARRA_AVRIL, renglon(ron, 6), renglon(ron, 6))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("BEBIDA_REPETIDA_EN_RETIRO"));
        retirar(RolCodigo.BARRA, evento, BARRA_AVRIL, renglon(ron, 0)).andExpect(status().isBadRequest());
        retirarJson(RolCodigo.BARRA, evento, BARRA_AVRIL, """
                {"renglones":[{"bebidaId":%d,"cantidad":1.5}]}""".formatted(ron)).andExpect(status().isBadRequest());
        retirarJson(RolCodigo.BARRA, evento, BARRA_AVRIL, "{\"renglones\":[]}").andExpect(status().isBadRequest());

        pedir(post("/api/v1/bebidas/" + gin + "/baja"), RolCodigo.COMPRAS).andExpect(status().isOk());
        retirar(RolCodigo.BARRA, evento, BARRA_AVRIL, renglon(gin, 6))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Gin de salida 750 ml está dada de baja: sacala del retiro."));
    }

    @Test
    void laBarraTieneQueSerDelSalonDelEvento() throws Exception {
        retirar(RolCodigo.COMPRAS, evento, BARRA_CLUB, renglon(ron, 6))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("BARRA_DE_OTRO_SALON"));
        retirar(RolCodigo.COMPRAS, evento, DEPOSITO, renglon(ron, 6))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("NO_ES_BARRA"));
    }

    // ---------- quién puede ----------

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"BARRA", "COMPRAS", "COORDINACION", "DIRECCION"})
    void laRegistranBarraComprasCoordinacionYDireccion(RolCodigo rol) throws Exception {
        retirar(rol, evento, BARRA_AVRIL, renglon(ron, 6)).andExpect(status().isCreated());
        pedir(get("/api/v1/entregas/jornada"), rol).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"ADMINISTRACION", "VENDEDORA", "PLANNER", "COCINA"})
    void elRestoRecibe403(RolCodigo rol) throws Exception {
        retirar(rol, evento, BARRA_AVRIL, renglon(ron, 6)).andExpect(status().isForbidden());
        pedir(get("/api/v1/eventos/{id}/entregas/{barra}", evento, BARRA_AVRIL), rol).andExpect(status().isForbidden());
        pedir(get("/api/v1/entregas/jornada"), rol).andExpect(status().isForbidden());
    }

    @Test
    void laBarraSoloOperaLosEventosDeLaJornadaEnLasBarrasDeSuSalon() throws Exception {
        long otroDia = escenario.evento("avril", JORNADA.plusDays(5), "noche", "EN_CURSO", vendedora, planner, "Otro día");

        retirar(RolCodigo.BARRA, otroDia, BARRA_AVRIL, renglon(ron, 6)).andExpect(status().isForbidden());
        pedir(get("/api/v1/eventos/{id}/entregas/{barra}", otroDia, BARRA_AVRIL), RolCodigo.BARRA).andExpect(status().isForbidden());
        retirar(RolCodigo.BARRA, evento, BARRA_CLUB, renglon(ron, 6)).andExpect(status().isForbidden());
        // Compras opera cualquier evento en curso.
        retirar(RolCodigo.COMPRAS, otroDia, BARRA_AVRIL, renglon(ron, 6)).andExpect(status().isCreated());
    }

    @Test
    void laJornadaListaLosEventosConLasBarrasDeSuSalon() throws Exception {
        pedir(get("/api/v1/entregas/jornada"), RolCodigo.BARRA)
                .andExpect(jsonPath("$[?(@.evento.id == %d)].evento.estado".formatted(evento)).value(hasItem("EN_CURSO")))
                .andExpect(jsonPath("$[?(@.evento.id == %d)].salon.codigo".formatted(evento)).value(hasItem("avril")))
                .andExpect(jsonPath("$[?(@.evento.id == %d)].barras[*].nombre".formatted(evento)).value(hasItem("Barra Avril")));

        // Antes de las 06:00 sigue la jornada anterior.
        ahora(JORNADA.plusDays(1).atTime(3, 0));
        pedir(get("/api/v1/entregas/jornada"), RolCodigo.BARRA)
                .andExpect(jsonPath("$[*].evento.id", hasItem((int) evento)));
    }

    // ---------- auxiliares ----------

    private record Renglon(long bebidaId, int cantidad) {
    }

    private static Renglon renglon(long bebidaId, int cantidad) {
        return new Renglon(bebidaId, cantidad);
    }

    private ResultActions retirar(RolCodigo rol, long eventoId, int barraId, Renglon... renglones) throws Exception {
        return retirar(personas.de(rol), eventoId, barraId, false, renglones);
    }

    private ResultActions retirar(Usuario quien, long eventoId, int barraId, boolean directo, Renglon... renglones)
            throws Exception {
        StringBuilder lista = new StringBuilder();
        for (Renglon r : renglones) {
            lista.append(lista.isEmpty() ? "" : ",").append("{\"bebidaId\":%d,\"cantidad\":%d}".formatted(r.bebidaId(), r.cantidad()));
        }
        return mvc.perform(post("/api/v1/eventos/{id}/entregas/{barra}/retiros", eventoId, barraId)
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(quien)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"directo\":%s,\"renglones\":[%s]}".formatted(directo, lista)));
    }

    private ResultActions retirarJson(RolCodigo rol, long eventoId, int barraId, String json) throws Exception {
        return mvc.perform(post("/api/v1/eventos/{id}/entregas/{barra}/retiros", eventoId, barraId)
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)).contentType(MediaType.APPLICATION_JSON).content(json));
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
