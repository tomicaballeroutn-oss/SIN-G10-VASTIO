package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
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

@PruebaDeIntegracion
@Transactional
class ConsultaConsumoIT {
    @Autowired MockMvc mvc;
    @Autowired Personas personas;
    @Autowired Escenario escenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    long evento;
    long bebida;

    @BeforeEach
    void preparar() {
        evento = evento("avril", "CERRADO", "Consulta consumo");
        bebida = jdbc.queryForObject("""
                INSERT INTO bebida (nombre, presentacion, tipo_bebida_id, unidad_manipulacion_id, unidades_por_bulto, activo)
                VALUES ('Bebida consulta', '750 ml', 3, 1, 6, true) RETURNING bebida_id""", Long.class);
        congelar(evento, 12, 6, 2, 4);
    }

    @Test
    void leeCantidadesCongeladasYCalculaSobreInvitados() throws Exception {
        consultar(evento, RolCodigo.ADMINISTRACION).andExpect(status().isOk())
                .andExpect(jsonPath("$.denominador").value("INVITADOS"))
                .andExpect(jsonPath("$.invitados").value(150))
                .andExpect(jsonPath("$.renglones[0].entregado").value(12))
                .andExpect(jsonPath("$.renglones[0].devuelto").value(6))
                .andExpect(jsonPath("$.renglones[0].remanente").value(2))
                .andExpect(jsonPath("$.renglones[0].consumo").value(4))
                .andExpect(jsonPath("$.renglones[0].porInvitado").value(0.0267))
                .andExpect(jsonPath("$.consumoTotal").value(4))
                .andExpect(jsonPath("$.porInvitadoTotal").value(0.0267));
    }

    @Test
    void sumaVariasBebidasSinIncluirOtrosEventos() throws Exception {
        long otra = jdbc.queryForObject("""
                INSERT INTO bebida (nombre, presentacion, tipo_bebida_id, unidad_manipulacion_id, unidades_por_bulto, activo)
                VALUES ('Otra bebida consulta', '1 l', 3, 1, 6, true) RETURNING bebida_id""", Long.class);
        jdbc.update("""
                INSERT INTO consumo_evento (evento_id, bebida_id, cantidad_entregada, cantidad_devuelta, cantidad_remanente, cantidad_consumida)
                VALUES (?, ?, 10, 3, 1, 6)""", evento, otra);
        consultar(evento, RolCodigo.COMPRAS).andExpect(status().isOk())
                .andExpect(jsonPath("$.renglones.length()").value(2))
                .andExpect(jsonPath("$.renglones[0].bebidaId").value(bebida))
                .andExpect(jsonPath("$.renglones[1].bebidaId").value(otra))
                .andExpect(jsonPath("$.consumoTotal").value(10))
                .andExpect(jsonPath("$.porInvitadoTotal").value(0.0667));
    }

    @Test
    void cerradoSinRegistrosYConConsumoCeroSonConsultables() throws Exception {
        long vacio = evento("club", "CERRADO", "Sin bebida");
        consultar(vacio, RolCodigo.COMPRAS).andExpect(status().isOk())
                .andExpect(jsonPath("$.renglones").isEmpty())
                .andExpect(jsonPath("$.consumoTotal").value(0))
                .andExpect(jsonPath("$.porInvitadoTotal").value(0));
        congelar(vacio, 12, 10, 2, 0);
        consultar(vacio, RolCodigo.COMPRAS).andExpect(status().isOk())
                .andExpect(jsonPath("$.renglones.length()").value(1))
                .andExpect(jsonPath("$.consumoTotal").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRE_RESERVA", "SENADO", "CONTRATADO", "CONFIRMADO", "EN_CURSO", "REALIZADO", "CANCELADO", "LIBERADA"})
    void noConfundeUnEventoNoCerradoConConsumoCero(String estado) throws Exception {
        consultar(evento("club", estado, "Sin cierre"), RolCodigo.COMPRAS)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("EVENTO_NO_CERRADO"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0})
    void sinDenominadorNoDivide(Integer invitados) throws Exception {
        jdbc.update("UPDATE evento SET cantidad_invitados = ? WHERE evento_id = ?", invitados, evento);
        em.clear();
        consultar(evento, RolCodigo.COMPRAS).andExpect(status().isOk())
                .andExpect(jsonPath("$.porInvitadoTotal").isEmpty())
                .andExpect(jsonPath("$.renglones[0].porInvitado").isEmpty());
    }

    @Test
    void segmentosUsanInvitadosDisponibles() throws Exception {
        jdbc.update("UPDATE evento SET tipo_evento_id = 4 WHERE evento_id = ?", evento);
        em.clear();
        consultar(evento, RolCodigo.COMPRAS).andExpect(status().isOk())
                .andExpect(jsonPath("$.usaSegmentos").value(true))
                .andExpect(jsonPath("$.denominador").value("INVITADOS"))
                .andExpect(jsonPath("$.porInvitadoTotal").value(0.0267));
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"DIRECCION", "COORDINACION", "ADMINISTRACION", "COMPRAS"})
    void permitePerfilesDeConsulta(RolCodigo rol) throws Exception {
        consultar(evento, rol).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"BARRA", "VENDEDORA", "PLANNER", "COCINA"})
    void deniegaOtrosPerfilesAunquePuedanCerrar(RolCodigo rol) throws Exception {
        consultar(evento, rol).andExpect(status().isForbidden());
    }

    @Test
    void requiereAutenticacionYValidaExistencia() throws Exception {
        mvc.perform(get("/api/v1/eventos/{id}/consumo", evento)).andExpect(status().isUnauthorized());
        consultar(Long.MAX_VALUE, RolCodigo.ADMINISTRACION).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("EVENTO_INEXISTENTE"));
    }

    @Test
    void aislaEventosYNoRecalculaDesdeStockNiPreparacionNiModificaHistoria() throws Exception {
        long otro = evento("club", "CERRADO", "Otro evento");
        congelar(otro, 100, 10, 0, 90);
        jdbc.update("""
                INSERT INTO stock_ubicacion (ubicacion_id, bebida_id, cantidad, fecha_actualizacion)
                VALUES (2, ?, 999, now())""", bebida);
        jdbc.update("""
                INSERT INTO orden_preparacion (evento_id, estado, ubicacion_origen_id, ubicacion_destino_id, responsable_id, fecha_creacion)
                VALUES (?, 'CERRADA', 1, 2, ?, now())""", evento, personas.de(RolCodigo.COMPRAS).getId());
        Map<String, List<Map<String, Object>>> antes = persistido();
        String primera = consultar(evento, RolCodigo.ADMINISTRACION).andExpect(status().isOk())
                .andExpect(jsonPath("$.consumoTotal").value(4)).andReturn().getResponse().getContentAsString();
        String segunda = consultar(evento, RolCodigo.ADMINISTRACION).andReturn().getResponse().getContentAsString();
        em.flush();
        assertThat(segunda).isEqualTo(primera);
        assertThat(persistido()).isEqualTo(antes);
    }

    @Test
    void integraCierreDeVariasBarrasYConservaElConsolidadoAunqueCambienSusSaldos() throws Exception {
        long conBarras = escenario.evento("avril", LocalDate.of(2035, 5, 21), "noche", "EN_CURSO",
                personas.de(RolCodigo.VENDEDORA), personas.de(RolCodigo.PLANNER), "Dos barras");
        short segunda = jdbc.queryForObject("""
                INSERT INTO ubicacion (nombre, tipo, salon_id, ubicacion_abastecimiento_id)
                VALUES ('Segunda barra consulta', 'BARRA', 1, 1) RETURNING ubicacion_id""", Short.class);
        // Compras opera fuera de la jornada, sin depender del reloj de la máquina.
        for (int barra : new int[] {2, segunda}) {
            operar("/api/v1/eventos/" + conBarras + "/entregas/" + barra + "/retiros",
                    "{\"renglones\":[{\"bebidaId\":" + bebida + ",\"cantidad\":12}]}")
                    .andExpect(status().isCreated());
            operar("/api/v1/eventos/" + conBarras + "/entregas/" + barra + "/devolucion",
                    "{\"renglones\":[{\"bebidaId\":" + bebida + ",\"devuelto\":6,\"remanente\":2}]}")
                    .andExpect(status().isCreated());
        }
        em.flush();
        jdbc.update("UPDATE evento SET estado = 'REALIZADO' WHERE evento_id = ?", conBarras);
        em.clear();
        operar("/api/v1/eventos/" + conBarras + "/cierre", null).andExpect(status().isOk());
        em.flush();
        consultar(conBarras, RolCodigo.ADMINISTRACION).andExpect(status().isOk())
                .andExpect(jsonPath("$.renglones.length()").value(1))
                .andExpect(jsonPath("$.renglones[0].entregado").value(24))
                .andExpect(jsonPath("$.renglones[0].devuelto").value(12))
                .andExpect(jsonPath("$.renglones[0].remanente").value(4))
                .andExpect(jsonPath("$.consumoTotal").value(8));
        jdbc.update("UPDATE stock_ubicacion SET cantidad = 123 WHERE bebida_id = ?", bebida);
        em.clear();
        consultar(conBarras, RolCodigo.COMPRAS).andExpect(jsonPath("$.consumoTotal").value(8));
        consultar(evento, RolCodigo.COMPRAS).andExpect(jsonPath("$.renglones[*].consumo", hasItem(4.0)));
    }

    private Map<String, List<Map<String, Object>>> persistido() {
        return Map.of("consumo", jdbc.queryForList("SELECT * FROM consumo_evento ORDER BY evento_id, bebida_id"),
                "stock", jdbc.queryForList("SELECT * FROM stock_ubicacion ORDER BY ubicacion_id, bebida_id"),
                "movimientos", jdbc.queryForList("SELECT * FROM movimiento_stock ORDER BY movimiento_id"),
                "historial", jdbc.queryForList("SELECT * FROM cambio_estado_evento ORDER BY cambio_id"),
                "ordenes", jdbc.queryForList("SELECT * FROM orden_preparacion ORDER BY orden_id"));
    }

    private long evento(String salon, String estado, String nombre) {
        return escenario.evento(salon, LocalDate.of(2035, 5, 20), "noche", estado,
                personas.de(RolCodigo.VENDEDORA), personas.de(RolCodigo.PLANNER), nombre);
    }

    private void congelar(long id, int entregado, int devuelto, int remanente, int consumo) {
        jdbc.update("""
                INSERT INTO consumo_evento (evento_id, bebida_id, cantidad_entregada, cantidad_devuelta, cantidad_remanente, cantidad_consumida)
                VALUES (?, ?, ?, ?, ?, ?)""", id, bebida, entregado, devuelto, remanente, consumo);
    }

    private ResultActions consultar(long id, RolCodigo rol) throws Exception {
        return mvc.perform(get("/api/v1/eventos/{id}/consumo", id).header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)));
    }

    private ResultActions operar(String ruta, String cuerpo) throws Exception {
        var pedido = post(ruta).header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COMPRAS));
        if (cuerpo != null) pedido.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        return mvc.perform(pedido);
    }
}
