package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Registrar ajuste de stock (UI-38): recuento físico y rotura. Usa la Barra Santa Bárbara (4). Motivos de V2: 11 Rotura,
 * 12 Recuento físico, 13 Otro (ámbito Ajuste); 1 es de Cancelación. Cada test deshace sus cambios.
 */
@PruebaDeIntegracion
@Transactional
class AjusteIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    Personas personas;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    long ron;

    @BeforeEach
    void bebida() throws Exception {
        String respuesta = mvc.perform(post("/api/v1/bebidas").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.ADMINISTRACION))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombre":"Ron de prueba","presentacion":"750 ml","tipoId":3,"unidadId":1,"unidadesPorBulto":6}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        ron = ((Number) JsonPath.read(respuesta, "$.id")).longValue();
    }

    // ---------- autorización ----------

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"DIRECCION", "COORDINACION", "ADMINISTRACION", "COMPRAS"})
    void administracionComprasYAccesoTotalRegistranAjustes(RolCodigo rol) throws Exception {
        recuento(personas.de(rol), 6, 12, null).andExpect(status().isOk());
        rotura(personas.de(rol), 1, 11, null).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"VENDEDORA", "PLANNER", "BARRA", "COCINA"})
    void elRestoNoRegistraAjustes(RolCodigo rol) throws Exception {
        recuento(personas.de(rol), 6, 12, null).andExpect(status().isForbidden());
        rotura(personas.de(rol), 1, 11, null).andExpect(status().isForbidden());
    }

    // ---------- recuento ----------

    @Test
    void elRecuentoRegistraLaDiferenciaHaciaArribaYHaciaAbajo() throws Exception {
        saldo(10);
        recuento(compras(), 14, 12, null)
                .andExpect(jsonPath("$.registrado").value(true))
                .andExpect(jsonPath("$.tipo").value("AJUSTE"))
                .andExpect(jsonPath("$.saldoAnterior").value(10.0))
                .andExpect(jsonPath("$.saldo").value(14.0))
                .andExpect(jsonPath("$.diferencia").value(4.0));
        recuento(compras(), 7, 12, null).andExpect(jsonPath("$.diferencia").value(-7.0));

        assertThat(saldoActual()).isEqualByComparingTo("7");
        List<Map<String, Object>> ajustes = jdbc.queryForList("""
                SELECT cantidad, ubicacion_origen_id, ubicacion_destino_id, motivo_id, movimiento_corregido_id
                FROM movimiento_stock WHERE bebida_id = ? AND tipo = 'AJUSTE' ORDER BY movimiento_id""", ron);
        assertThat((BigDecimal) ajustes.get(0).get("cantidad")).isEqualByComparingTo("4");
        assertThat(((Number) ajustes.get(0).get("ubicacion_destino_id")).intValue()).isEqualTo(4);
        assertThat((BigDecimal) ajustes.get(1).get("cantidad")).isEqualByComparingTo("7");
        assertThat(((Number) ajustes.get(1).get("ubicacion_origen_id")).intValue()).isEqualTo(4);
        assertThat(ajustes).allSatisfy(a -> {
            assertThat(((Number) a.get("motivo_id")).intValue()).isEqualTo(12);
            assertThat(a.get("movimiento_corregido_id")).isNull();
        });
    }

    @Test
    void sinSaldoPrevioElRecuentoEntraCompleto() throws Exception {
        recuento(compras(), 6, 12, null).andExpect(jsonPath("$.diferencia").value(6.0));
        assertThat(saldoActual()).isEqualByComparingTo("6");
    }

    @Test
    void siCoincideConElSaldoNoRegistraNada() throws Exception {
        saldo(10);
        recuento(compras(), 10, 12, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrado").value(false))
                .andExpect(jsonPath("$.diferencia").value(0.0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE bebida_id = ?", Integer.class, ron)).isZero();
    }

    @Test
    void unaBebidaDadaDeBajaSePuedeDejarEnCero() throws Exception {
        saldo(5);
        mvc.perform(post("/api/v1/bebidas/" + ron + "/baja").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COMPRAS)))
                .andExpect(status().isOk());
        recuento(compras(), 0, 12, null).andExpect(jsonPath("$.saldo").value(0.0));
    }

    // ---------- rotura ----------

    @Test
    void unaRoturaQueDejaSaldoNegativoSeAceptaYAvisaACompras() throws Exception {
        Usuario quienRegistra = compras();
        Usuario otraCompras = personas.usuario("otra.compras", "Otra persona de Compras", RolCodigo.COMPRAS);
        Usuario administracion = personas.de(RolCodigo.ADMINISTRACION);
        saldo(2);

        rotura(quienRegistra, 5, 11, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("MERMA"))
                .andExpect(jsonPath("$.saldo").value(-3.0))
                .andExpect(jsonPath("$.avisoSaldoNegativo").value(true));

        entityManager.flush(); // los destinatarios se insertan al bajar a la base
        Long aviso = jdbc.queryForObject("""
                SELECT notificacion_id FROM notificacion WHERE tipo = 'ALERTA_STOCK' AND mensaje = ?""", Long.class,
                "Saldo negativo de Ron de prueba 750 ml en Barra Santa Bárbara: falta registrar un movimiento.");
        List<Long> destinatarios = jdbc.queryForList("SELECT usuario_id FROM notificacion_destinatario WHERE notificacion_id = ?",
                Long.class, aviso);
        assertThat(destinatarios).contains(otraCompras.getId(), administracion.getId()).doesNotContain(quienRegistra.getId());
        Map<String, Object> merma = jdbc.queryForMap("SELECT ubicacion_origen_id, ubicacion_destino_id FROM movimiento_stock WHERE bebida_id = ? AND tipo = 'MERMA'", ron);
        assertThat(((Number) merma.get("ubicacion_origen_id")).intValue()).isEqualTo(4);
        assertThat(merma.get("ubicacion_destino_id")).isNull();
    }

    @Test
    void unaRoturaConSaldoSuficienteNoAvisa() throws Exception {
        saldo(12);
        rotura(compras(), 1, 11, null).andExpect(jsonPath("$.saldo").value(11.0)).andExpect(jsonPath("$.avisoSaldoNegativo").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo = 'ALERTA_STOCK' AND mensaje LIKE '%Ron de prueba%'",
                Integer.class)).isZero();
    }

    // ---------- motivo y validaciones ----------

    @Test
    void elMotivoEsDeAjusteYConOtroPideDetalle() throws Exception {
        recuento(compras(), 6, 1, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("MOTIVO_INVALIDO"));
        rotura(compras(), 1, 13, " ")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Con el motivo «Otro», contá qué pasó en el detalle."));
        rotura(compras(), 1, 13, "Se cayó una caja al descargar").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT observacion FROM movimiento_stock WHERE bebida_id = ? AND tipo = 'MERMA'", String.class, ron))
                .isEqualTo("Se cayó una caja al descargar");
    }

    @Test
    void validaLasCantidades() throws Exception {
        rotura(compras(), 0, 11, null).andExpect(status().isBadRequest());
        recuento(compras(), -1, 12, null).andExpect(status().isBadRequest());
        enviar(compras(), "/api/v1/ajustes/recuento", """
                {"ubicacionId":4,"bebidaId":%d,"cantidadContada":2.5,"motivoId":12}""".formatted(ron))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("Cargá botellas enteras."));
    }

    @Test
    void unaUbicacionDadaDeBajaNoSeAjusta() throws Exception {
        jdbc.update("UPDATE ubicacion SET activo = false WHERE ubicacion_id = 4");
        recuento(compras(), 6, 12, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("UBICACION_INACTIVA"));
    }

    private Usuario compras() {
        return personas.de(RolCodigo.COMPRAS);
    }

    private void saldo(int cantidad) {
        jdbc.update("INSERT INTO stock_ubicacion (ubicacion_id, bebida_id, cantidad, fecha_actualizacion) VALUES (4, ?, ?, now())", ron, cantidad);
    }

    private BigDecimal saldoActual() {
        return jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id = 4 AND bebida_id = ?", BigDecimal.class, ron);
    }

    private ResultActions recuento(Usuario quien, int contado, int motivo, String detalle) throws Exception {
        return enviar(quien, "/api/v1/ajustes/recuento", """
                {"ubicacionId":4,"bebidaId":%d,"cantidadContada":%d,"motivoId":%d,"detalle":%s}"""
                .formatted(ron, contado, motivo, detalle == null ? "null" : "\"" + detalle + "\""));
    }

    private ResultActions rotura(Usuario quien, int cantidad, int motivo, String detalle) throws Exception {
        return enviar(quien, "/api/v1/ajustes/rotura", """
                {"ubicacionId":4,"bebidaId":%d,"cantidad":%d,"motivoId":%d,"detalle":%s}"""
                .formatted(ron, cantidad, motivo, detalle == null ? "null" : "\"" + detalle + "\""));
    }

    private ResultActions enviar(Usuario quien, String ruta, String cuerpo) throws Exception {
        return mvc.perform(post(ruta).header(HttpHeaders.AUTHORIZATION, personas.bearer(quien))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }
}
