package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.bebida.dominio.TipoUbicacion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

@PruebaDeIntegracion
@Transactional
class RecuentoSinSaldoIT {
    @Autowired MockMvc mvc;
    @Autowired Personas personas;
    @Autowired JdbcTemplate jdbc;
    long bebida;

    @BeforeEach
    void bebidaActiva() throws Exception {
        String respuesta = mvc.perform(post("/api/v1/bebidas")
                        .header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.ADMINISTRACION))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Fernet sin saldo","presentacion":"750 ml","tipoId":3,"unidadId":1,"unidadesPorBulto":6}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        bebida = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    @ParameterizedTest
    @CsvSource({"DEPOSITO,false", "DEPOSITO,true", "TRANSICION,false", "TRANSICION,true", "BARRA,false", "BARRA,true"})
    void desdeCeroRegistraLaDiferenciaConTrazabilidadEnCadaTipoDeUbicacion(TipoUbicacion tipo, boolean filaPrevia) throws Exception {
        short ubicacion = ubicacion(tipo);
        if (filaPrevia) saldoCero(ubicacion);
        if (tipo == TipoUbicacion.BARRA) {
            mvc.perform(get("/api/v1/stock").param("ubicacionId", String.valueOf(ubicacion))
                            .header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COMPRAS)))
                    .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)]".formatted(bebida)).isEmpty());
        }

        recuento(ubicacion, 6, RolCodigo.COMPRAS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.saldoAnterior").value(0))
                .andExpect(jsonPath("$.diferencia").value(6))
                .andExpect(jsonPath("$.saldo").value(6));

        Map<String, Object> movimiento = jdbc.queryForMap("""
                SELECT tipo, cantidad, ubicacion_origen_id, ubicacion_destino_id, motivo_id, usuario_id, fecha_hora
                FROM movimiento_stock WHERE bebida_id = ?""", bebida);
        assertThat(movimiento).containsEntry("tipo", "AJUSTE").containsEntry("usuario_id", personas.de(RolCodigo.COMPRAS).getId());
        assertThat(((Number) movimiento.get("ubicacion_destino_id")).shortValue()).isEqualTo(ubicacion);
        assertThat(((Number) movimiento.get("motivo_id")).intValue()).isEqualTo(12);
        assertThat((BigDecimal) movimiento.get("cantidad")).isEqualByComparingTo("6");
        assertThat(movimiento.get("ubicacion_origen_id")).isNull();
        assertThat(movimiento.get("fecha_hora")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id = ? AND bebida_id = ?",
                BigDecimal.class, ubicacion, bebida)).isEqualByComparingTo("6");
        mvc.perform(get("/api/v1/stock").param("ubicacionId", String.valueOf(ubicacion))
                        .header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.ADMINISTRACION)))
                .andExpect(jsonPath("$.renglones[?(@.bebida.id == %d)].cantidad".formatted(bebida)).value(hasItem(6.0)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void contarCeroSobreCeroNoCreaMovimientosNiFilasFicticias(boolean filaPrevia) throws Exception {
        if (filaPrevia) saldoCero((short) 2);
        recuento((short) 2, 0, RolCodigo.ADMINISTRACION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrado").value(false))
                .andExpect(jsonPath("$.diferencia").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE bebida_id = ?", Integer.class, bebida)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_ubicacion WHERE bebida_id = ?", Integer.class, bebida))
                .isEqualTo(filaPrevia ? 1 : 0);
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"BARRA", "VENDEDORA", "PLANNER", "COCINA"})
    void sinPermisoNoSePuedeRegistrarNiCrearSaldo(RolCodigo rol) throws Exception {
        recuento((short) 2, 6, rol).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE bebida_id = ?", Integer.class, bebida)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_ubicacion WHERE bebida_id = ?", Integer.class, bebida)).isZero();
    }

    private short ubicacion(TipoUbicacion tipo) {
        return switch (tipo) {
            case DEPOSITO -> 1;
            case BARRA -> 2;
            case TRANSICION -> jdbc.queryForObject("INSERT INTO ubicacion (nombre, tipo) VALUES ('Transición de recuento', 'TRANSICION') RETURNING ubicacion_id", Short.class);
        };
    }

    private void saldoCero(short ubicacion) {
        jdbc.update("INSERT INTO stock_ubicacion (ubicacion_id, bebida_id, cantidad, fecha_actualizacion) VALUES (?, ?, 0, now())", ubicacion, bebida);
    }

    private ResultActions recuento(short ubicacion, int contado, RolCodigo rol) throws Exception {
        return mvc.perform(post("/api/v1/ajustes/recuento").header(HttpHeaders.AUTHORIZATION, personas.bearer(rol))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"ubicacionId":%d,"bebidaId":%d,"cantidadContada":%d,"motivoId":12}""".formatted(ubicacion, bebida, contado)));
    }
}
