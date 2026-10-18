package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Registrar inventario inicial (UI-28, paso 1). Usa la Barra Santa Bárbara (4), sin movimientos en la base de prueba.
 * Cada test deshace sus cambios.
 */
@PruebaDeIntegracion
@Transactional
class InventarioInicialIT {

    private static final String RUTA = "/api/v1/inventario-inicial/4";

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    JdbcTemplate jdbc;

    long ron;
    long gin;

    @BeforeEach
    void bebidas() throws Exception {
        ron = crearBebida("Ron de prueba");
        gin = crearBebida("Gin de prueba");
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"DIRECCION", "COORDINACION", "ADMINISTRACION", "COMPRAS"})
    void administracionComprasYAccesoTotalCarganElInventario(RolCodigo rol) throws Exception {
        guardar(rol, "[{\"bebidaId\":%d,\"cantidad\":6}]".formatted(ron)).andExpect(status().isOk());
        pedir(get(RUTA), rol).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"VENDEDORA", "PLANNER", "BARRA", "COCINA"})
    void elRestoNoCargaElInventario(RolCodigo rol) throws Exception {
        pedir(get(RUTA), rol).andExpect(status().isForbidden());
        guardar(rol, "[]").andExpect(status().isForbidden());
    }

    @Test
    void laPrimeraCargaRegistraInventarioInicialYSumaAlSaldo() throws Exception {
        guardar(RolCodigo.ADMINISTRACION, "[{\"bebidaId\":%d,\"cantidad\":27},{\"bebidaId\":%d,\"cantidad\":0}]".formatted(ron, gin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ubicacion.nombre").value("Barra Santa Bárbara"))
                .andExpect(jsonPath("$.cerrada").value(false))
                .andExpect(jsonPath("$.renglones[?(@.bebidaId == %d)].cantidad".formatted(ron)).value(hasItem(27)))
                .andExpect(jsonPath("$.renglones[?(@.bebidaId == %d)]".formatted(gin)).isEmpty())
                .andExpect(jsonPath("$.usuario.nombre").value("Prueba administracion"))
                .andExpect(jsonPath("$.ultimaModificacion").isNotEmpty());

        assertThat(saldo(ron)).isEqualByComparingTo("27");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM movimiento_stock m JOIN ingreso i USING (ingreso_id)
                WHERE m.bebida_id = ? AND m.tipo = 'INVENTARIO_INICIAL' AND i.es_inventario_inicial AND i.ubicacion_destino_id = 4""",
                Integer.class, ron)).isEqualTo(1);
    }

    @Test
    void cambiarLoCargadoRegistraUnAjusteQueReferenciaAlOriginal() throws Exception {
        guardar(RolCodigo.COMPRAS, "[{\"bebidaId\":%d,\"cantidad\":27}]".formatted(ron)).andExpect(status().isOk());
        guardar(RolCodigo.COMPRAS, "[{\"bebidaId\":%d,\"cantidad\":20},{\"bebidaId\":%d,\"cantidad\":12}]".formatted(ron, gin))
                .andExpect(jsonPath("$.renglones[?(@.bebidaId == %d)].cantidad".formatted(ron)).value(hasItem(20)));
        guardar(RolCodigo.COMPRAS, "[{\"bebidaId\":%d,\"cantidad\":24}]".formatted(ron))
                .andExpect(jsonPath("$.renglones[?(@.bebidaId == %d)].cantidad".formatted(ron)).value(hasItem(24)))
                .andExpect(jsonPath("$.renglones[?(@.bebidaId == %d)].cantidad".formatted(gin)).value(hasItem(12)));

        assertThat(saldo(ron)).isEqualByComparingTo("24");
        List<Map<String, Object>> ajustes = jdbc.queryForList("""
                SELECT m.cantidad, m.ubicacion_origen_id, m.ubicacion_destino_id, m.motivo_id, o.tipo AS corregido
                FROM movimiento_stock m JOIN movimiento_stock o ON o.movimiento_id = m.movimiento_corregido_id
                WHERE m.bebida_id = ? AND m.tipo = 'AJUSTE' ORDER BY m.movimiento_id""", ron);
        assertThat(ajustes).hasSize(2);
        // −7 sale de la barra; +4 entra.
        assertThat((BigDecimal) ajustes.get(0).get("cantidad")).isEqualByComparingTo("7");
        assertThat(((Number) ajustes.get(0).get("ubicacion_origen_id")).intValue()).isEqualTo(4);
        assertThat((BigDecimal) ajustes.get(1).get("cantidad")).isEqualByComparingTo("4");
        assertThat(((Number) ajustes.get(1).get("ubicacion_destino_id")).intValue()).isEqualTo(4);
        assertThat(ajustes).allSatisfy(a -> {
            assertThat(((Number) a.get("motivo_id")).intValue()).isEqualTo(10);
            assertThat(a.get("corregido")).isEqualTo("INVENTARIO_INICIAL");
        });
    }

    @Test
    void conOtroMovimientoLaCargaQuedaCerrada() throws Exception {
        guardar(RolCodigo.COMPRAS, "[{\"bebidaId\":%d,\"cantidad\":6}]".formatted(ron)).andExpect(status().isOk());
        // Un ingreso al depósito madre (1) no cierra la carga de la barra (4), pero sí la del depósito.
        pedir(post("/api/v1/ingresos").contentType(MediaType.APPLICATION_JSON).content("""
                {"renglones":[{"bebidaId":%d,"cantidad":12}]}""".formatted(gin)), RolCodigo.COMPRAS).andExpect(status().isCreated());
        pedir(get(RUTA), RolCodigo.COMPRAS).andExpect(jsonPath("$.cerrada").value(false));
        pedir(get("/api/v1/inventario-inicial/1"), RolCodigo.COMPRAS).andExpect(jsonPath("$.cerrada").value(true));

        mvc.perform(put("/api/v1/inventario-inicial/1").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COMPRAS))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"renglones\":[{\"bebidaId\":%d,\"cantidad\":6}]}".formatted(ron)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CARGA_INICIAL_CERRADA"));
    }

    @Test
    void unaBebidaDadaDeBajaNoSeCargaPorPrimeraVezPeroSeCorrige() throws Exception {
        guardar(RolCodigo.COMPRAS, "[{\"bebidaId\":%d,\"cantidad\":6}]".formatted(ron)).andExpect(status().isOk());
        pedir(post("/api/v1/bebidas/" + ron + "/baja"), RolCodigo.COMPRAS).andExpect(status().isOk());
        pedir(post("/api/v1/bebidas/" + gin + "/baja"), RolCodigo.COMPRAS).andExpect(status().isOk());

        guardar(RolCodigo.COMPRAS, "[{\"bebidaId\":%d,\"cantidad\":0}]".formatted(ron)).andExpect(status().isOk());
        guardar(RolCodigo.COMPRAS, "[{\"bebidaId\":%d,\"cantidad\":6}]".formatted(gin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("BEBIDA_DADA_DE_BAJA"));
    }

    @Test
    void validaLasCantidades() throws Exception {
        guardar(RolCodigo.COMPRAS, "[{\"bebidaId\":%d,\"cantidad\":-1},{\"bebidaId\":%d,\"cantidad\":1.5}]".formatted(ron, gin))
                .andExpect(status().isBadRequest());
        guardar(RolCodigo.COMPRAS, "[{\"bebidaId\":%d,\"cantidad\":1},{\"bebidaId\":%d,\"cantidad\":2}]".formatted(ron, ron))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("BEBIDA_REPETIDA_EN_CARGA"));
    }

    private BigDecimal saldo(long bebida) {
        return jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id = 4 AND bebida_id = ?", BigDecimal.class, bebida);
    }

    private long crearBebida(String nombre) throws Exception {
        String respuesta = pedir(post("/api/v1/bebidas").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"%s","presentacion":"750 ml","tipoId":3,"unidadId":1,"unidadesPorBulto":6}""".formatted(nombre)),
                RolCodigo.ADMINISTRACION).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private ResultActions guardar(RolCodigo rol, String renglones) throws Exception {
        return pedir(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"renglones\":" + renglones + "}"), rol);
    }

    private ResultActions pedir(MockHttpServletRequestBuilder pedido, RolCodigo rol) throws Exception {
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)));
    }
}
