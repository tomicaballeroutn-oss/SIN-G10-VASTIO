package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Registrar ingreso de bebida (UI-29). Las bebidas se crean en cada test; el depósito madre es el de V2 (id 1).
 * Cada test deshace sus cambios.
 */
@PruebaDeIntegracion
@Transactional
class IngresoIT {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of(VastioApplication.ZONA_HORARIA));

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
        ron = crearBebida("Ron de prueba", 6);
        gin = crearBebida("Gin de prueba", 12);
    }

    // ---------- autorización ----------

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"DIRECCION", "COORDINACION", "ADMINISTRACION", "COMPRAS"})
    void administracionComprasYAccesoTotalRegistranIngresos(RolCodigo rol) throws Exception {
        registrar(rol, """
                {"renglones":[{"bebidaId":%d,"cantidad":6}]}""".formatted(ron)).andExpect(status().isCreated());
        pedir(get("/api/v1/ingresos"), rol).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"VENDEDORA", "PLANNER", "BARRA", "COCINA"})
    void elRestoNoRegistraIngresos(RolCodigo rol) throws Exception {
        registrar(rol, """
                {"renglones":[{"bebidaId":%d,"cantidad":6}]}""".formatted(ron)).andExpect(status().isForbidden());
        pedir(get("/api/v1/ingresos"), rol).andExpect(status().isForbidden());
    }

    // ---------- registro ----------

    @Test
    void entraAlDepositoMadreYSumaAlSaldo() throws Exception {
        Usuario nadir = personas.usuario("nadir.prueba", "Nadir de prueba", RolCodigo.COMPRAS);

        registrar(nadir, """
                {"fecha":"%s","numeroRemito":" R-0001-00001234 ","renglones":[{"bebidaId":%d,"cantidad":27},{"bebidaId":%d,"cantidad":12}]}"""
                .formatted(HOY.minusDays(2), ron, gin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaIngreso").value(HOY.minusDays(2).toString()))
                .andExpect(jsonPath("$.numeroRemito").value("R-0001-00001234"))
                .andExpect(jsonPath("$.destino.id").value(1))
                .andExpect(jsonPath("$.renglones[0].nombre").value("Ron de prueba"))
                .andExpect(jsonPath("$.renglones[0].unidad").value("Caja"))
                .andExpect(jsonPath("$.renglones[0].unidadesPorBulto").value(6))
                .andExpect(jsonPath("$.renglones[0].cantidad").value(27))
                .andExpect(jsonPath("$.usuario.nombre").value("Nadir de prueba"))
                .andExpect(jsonPath("$.fechaRegistro").isNotEmpty());
        registrar(nadir, """
                {"renglones":[{"bebidaId":%d,"cantidad":3}]}""".formatted(ron))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaIngreso").value(HOY.toString()))
                .andExpect(jsonPath("$.numeroRemito").doesNotExist());

        assertThat(saldo(1, ron)).isEqualByComparingTo("30");
        assertThat(saldo(1, gin)).isEqualByComparingTo("12");
        Map<String, Object> movimiento = jdbc.queryForMap("""
                SELECT tipo, cantidad, ubicacion_origen_id, ubicacion_destino_id, usuario_id, ingreso_id, fecha_hora
                FROM movimiento_stock WHERE bebida_id = ? ORDER BY movimiento_id LIMIT 1""", gin);
        assertThat(movimiento).containsEntry("tipo", "INGRESO").containsEntry("usuario_id", nadir.getId());
        assertThat(((Number) movimiento.get("ubicacion_destino_id")).intValue()).isEqualTo(1);
        assertThat(movimiento.get("ubicacion_origen_id")).isNull();
        assertThat(movimiento.get("ingreso_id")).isNotNull();
        assertThat(movimiento.get("fecha_hora")).isNotNull();
    }

    @Test
    void losUltimosIngresosVanPrimeroConQuienLosRegistro() throws Exception {
        registrar(RolCodigo.COMPRAS, """
                {"numeroRemito":"primero","renglones":[{"bebidaId":%d,"cantidad":6}]}""".formatted(ron)).andExpect(status().isCreated());
        registrar(RolCodigo.COMPRAS, """
                {"numeroRemito":"segundo","renglones":[{"bebidaId":%d,"cantidad":12}]}""".formatted(gin)).andExpect(status().isCreated());

        pedir(get("/api/v1/ingresos?limite=2"), RolCodigo.ADMINISTRACION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].numeroRemito").value("segundo"))
                .andExpect(jsonPath("$[0].renglones[0].nombre").value("Gin de prueba"))
                .andExpect(jsonPath("$[0].usuario.nombre").value("Prueba compras"))
                .andExpect(jsonPath("$[1].numeroRemito").value("primero"));
    }

    // ---------- validaciones ----------

    @Test
    void laFechaNoPuedeSerFutura() throws Exception {
        registrar(RolCodigo.COMPRAS, """
                {"fecha":"%s","renglones":[{"bebidaId":%d,"cantidad":6}]}""".formatted(HOY.plusDays(1), ron))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("La fecha de ingreso no puede ser posterior a hoy."));
    }

    @Test
    void lasCantidadesSonBotellasEnterasMayoresACero() throws Exception {
        registrar(RolCodigo.COMPRAS, """
                {"renglones":[{"bebidaId":%d,"cantidad":2.5},{"bebidaId":%d,"cantidad":0}]}""".formatted(ron, gin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[?(@.campo == 'renglones[0].cantidad')].mensaje").value("Cargá botellas enteras."))
                .andExpect(jsonPath("$.errores[?(@.campo == 'renglones[1].cantidad')].mensaje").value("La cantidad tiene que ser mayor a 0."));
        registrar(RolCodigo.COMPRAS, """
                {"renglones":[]}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unaBebidaNoSeRepiteNiPuedeEstarDadaDeBaja() throws Exception {
        registrar(RolCodigo.COMPRAS, """
                {"renglones":[{"bebidaId":%d,"cantidad":6},{"bebidaId":%d,"cantidad":6}]}""".formatted(ron, ron))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("BEBIDA_REPETIDA_EN_INGRESO"));

        pedir(post("/api/v1/bebidas/" + gin + "/baja"), RolCodigo.COMPRAS).andExpect(status().isOk());
        registrar(RolCodigo.COMPRAS, """
                {"renglones":[{"bebidaId":%d,"cantidad":6}]}""".formatted(gin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Gin de prueba 750 ml está dada de baja. Sacala del ingreso o reactivala en el catálogo."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ingreso WHERE ingreso_id IN (SELECT ingreso_id FROM movimiento_stock WHERE bebida_id IN (?, ?))",
                Integer.class, ron, gin)).isZero();
    }

    @Test
    void sinDepositoMadreNoHayIngreso() throws Exception {
        jdbc.update("UPDATE ubicacion SET activo = false WHERE tipo = 'DEPOSITO'");

        registrar(RolCodigo.COMPRAS, """
                {"renglones":[{"bebidaId":%d,"cantidad":6}]}""".formatted(ron))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SIN_DEPOSITO_MADRE"));
    }

    private BigDecimal saldo(int ubicacion, long bebida) {
        return jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id = ? AND bebida_id = ?", BigDecimal.class,
                ubicacion, bebida);
    }

    private long crearBebida(String nombre, int porBulto) throws Exception {
        String respuesta = pedir(post("/api/v1/bebidas").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"%s","presentacion":"750 ml","tipoId":3,"unidadId":1,"unidadesPorBulto":%d}""".formatted(nombre, porBulto)),
                RolCodigo.ADMINISTRACION).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    private ResultActions registrar(RolCodigo rol, String cuerpo) throws Exception {
        return registrar(personas.de(rol), cuerpo);
    }

    private ResultActions registrar(Usuario usuario, String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/ingresos").header(HttpHeaders.AUTHORIZATION, personas.bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private ResultActions pedir(MockHttpServletRequestBuilder pedido, RolCodigo rol) throws Exception {
        return mvc.perform(pedido.header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)));
    }
}
