package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Sin transacción de test: cada petición confirma o revierte su propia transacción y se observa lo persistido por JDBC.
 * El contexto propio y descartable evita compartir estos datos confirmados con las demás suites.
 */
@PruebaDeIntegracion
@TestPropertySource(properties = "spring.application.name=vastio-regresion-devolucion")
@DirtiesContext
class DevolucionSaldoNegativoIT {
    @Autowired MockMvc mvc;
    @Autowired Personas personas;
    @Autowired Escenario escenario;
    @Autowired JdbcTemplate jdbc;

    long evento;
    long bebida;
    long otra;
    short barra;
    short transicion;
    String autorizacion;

    @BeforeEach
    void datos() throws Exception {
        String unico = UUID.randomUUID().toString();
        autorizacion = personas.bearer(RolCodigo.COMPRAS);
        evento = escenario.evento("avril", LocalDate.of(2040, 1, 1).plusDays(
                jdbc.queryForObject("SELECT count(*) FROM evento", Integer.class)), "noche", "EN_CURSO",
                personas.de(RolCodigo.VENDEDORA), personas.de(RolCodigo.PLANNER), "Regresión " + unico);
        bebida = crearBebida("A conciliable " + unico);
        otra = crearBebida("Z negativa " + unico);
        transicion = jdbc.queryForObject("""
                INSERT INTO ubicacion(nombre,tipo) VALUES (?,'TRANSICION') RETURNING ubicacion_id""",
                Short.class, "Transición " + unico);
        barra = jdbc.queryForObject("""
                INSERT INTO ubicacion(nombre,tipo,salon_id,ubicacion_abastecimiento_id)
                VALUES (?,'BARRA',1,?) RETURNING ubicacion_id""", Short.class, "Barra " + unico, transicion);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "1,0", "0,1", "1,1"})
    void saldoNegativoRechazaCerosYPositivosSinConsumoNiEfectos(int devuelto, int remanente) throws Exception {
        retirar(otra, 3);
        rotura(otra, 6);
        assertThat(saldo(barra, otra)).isEqualByComparingTo("-3");
        Map<String, List<Map<String, Object>>> antes = persistido();

        devolver(renglon(otra, devuelto, remanente))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("SUPERA_LO_DE_LA_BARRA"))
                .andExpect(jsonPath("$.detail", containsString("Z negativa")))
                .andExpect(jsonPath("$.detail", not(containsString("-3"))));

        assertThat(persistido()).isEqualTo(antes);
        assertThat(estadoEntrega()).isEqualTo("ABIERTA");
        assertThat(cantidadConsumos()).isZero();
    }

    @Test
    void omitirLaBebidaNoEvitaLaValidacionNiHabilitaElCierre() throws Exception {
        retirar(otra, 3);
        rotura(otra, 6);
        devolver("").andExpect(status().isUnprocessableContent());
        jdbc.update("UPDATE evento SET estado='REALIZADO' WHERE evento_id=?", evento);
        long orden = jdbc.queryForObject("""
                INSERT INTO orden_preparacion(evento_id,estado,ubicacion_origen_id,ubicacion_destino_id,responsable_id,fecha_creacion)
                VALUES (?,'CONFIRMADA',1,?,?,now()) RETURNING orden_id""", Long.class,
                evento, transicion, personas.de(RolCodigo.COMPRAS).getId());
        Map<String, List<Map<String, Object>>> antes = persistido();

        operar("eventos/" + evento + "/cierre", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DEVOLUCION_PENDIENTE"));

        // Incluye rollback de la transición y su historial, que CierreService intenta antes de validar las entregas.
        assertThat(persistido()).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT estado FROM evento WHERE evento_id=?", String.class, evento)).isEqualTo("REALIZADO");
        assertThat(jdbc.queryForObject("SELECT estado FROM orden_preparacion WHERE orden_id=?", String.class, orden)).isEqualTo("CONFIRMADA");
        assertThat(estadoEntrega()).isEqualTo("ABIERTA");
        assertThat(cantidadConsumos()).isZero();
    }

    @Test
    void revierteLosMovimientosDeUnRenglonAnteriorSiOtroTieneSaldoNegativo() throws Exception {
        retirar(bebida, 6);
        retirar(otra, 3);
        rotura(otra, 6);
        Map<String, List<Map<String, Object>>> antes = persistido();

        // Se procesa A antes de Z: había que deshacer DEVOLUCION, REMANENTE, CONSUMO y los saldos del primer renglón.
        devolver(renglon(bebida, 2, 1) + "," + renglon(otra, 0, 0))
                .andExpect(status().isUnprocessableContent());

        assertThat(persistido()).isEqualTo(antes);
        assertThat(saldo(barra, bebida)).isEqualByComparingTo("6");
        assertThat(saldo(barra, otra)).isEqualByComparingTo("-3");
        assertThat(estadoEntrega()).isEqualTo("ABIERTA");
    }

    @ParameterizedTest
    @CsvSource({"6,0,0,0", "0,3,3,0", "0,2,1,3", "0,0,0,6"})
    void saldosNoNegativosPermitenConsumoCeroOPositivo(int rotas, int devuelto, int remanente, int consumo) throws Exception {
        retirar(bebida, 6);
        if (rotas > 0) rotura(bebida, rotas);

        devolver(renglon(bebida, devuelto, remanente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consumo[0].consumo").value(consumo))
                .andExpect(jsonPath("$.entrega.estadoEntrega").value("CERRADA"));

        assertThat(saldo(barra, bebida)).isEqualByComparingTo(Integer.toString(remanente));
        assertThat(cantidadConsumos()).isEqualTo(consumo == 0 ? 0 : 1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM movimiento_stock WHERE evento_id=? AND tipo='CONSUMO' AND cantidad<=0""",
                Integer.class, evento)).isZero();
    }

    @Test
    void preparacionDevolucionCierreYConsultaConservanStockEIdempotencia() throws Exception {
        jdbc.update("UPDATE evento SET estado='CONFIRMADO' WHERE evento_id=?", evento);
        String preparacion = """
                {"barraId":%d,"destinoId":%d,"renglones":[{"bebidaId":%d,"cantidad":12}]}"""
                .formatted(barra, transicion, bebida);
        for (int intento = 0; intento < 2; intento++) {
            mvc.perform(post("/api/v1/eventos/{id}/preparaciones", evento).header("Authorization", autorizacion)
                            .header("Idempotency-Key", "preparacion-unica").contentType(MediaType.APPLICATION_JSON).content(preparacion))
                    .andExpect(intento == 0 ? status().isCreated() : status().isOk());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preparacion_evento WHERE evento_id=?", Integer.class, evento)).isEqualTo(1);
        jdbc.update("UPDATE evento SET estado='EN_CURSO' WHERE evento_id=?", evento);
        retirar(bebida, 6);
        // El saldo del depósito madre puede ser negativo: la corrección no prohíbe ni altera ese comportamiento.
        assertThat(saldo((short) 1, bebida)).isEqualByComparingTo("-12");
        devolver(renglon(bebida, 2, 1), "{\"bebidaId\":%d,\"cantidad\":6}".formatted(bebida))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.consumo[0].consumo").value(3));
        assertThat(cantidadConsumos()).isEqualTo(1);
        List<Map<String, Object>> stockAntes = stock();
        List<Map<String, Object>> movimientosAntes = movimientos();
        jdbc.update("UPDATE evento SET estado='REALIZADO' WHERE evento_id=?", evento);

        operar("eventos/" + evento + "/cierre", null).andExpect(status().isOk()).andExpect(jsonPath("$.consumoTotal").value(3));
        assertThat(stock()).isEqualTo(stockAntes);
        assertThat(movimientos()).isEqualTo(movimientosAntes);
        Map<String, List<Map<String, Object>>> antesConsulta = persistido();
        mvc.perform(get("/api/v1/eventos/{id}/consumo", evento)
                        .header("Authorization", personas.bearer(RolCodigo.ADMINISTRACION)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consumoTotal").value(3))
                .andExpect(jsonPath("$.renglones[0].entregado").value(6))
                .andExpect(jsonPath("$.renglones[0].devuelto").value(2))
                .andExpect(jsonPath("$.renglones[0].remanente").value(1));
        assertThat(persistido()).isEqualTo(antesConsulta);
        assertThat(saldo(barra, bebida)).isEqualByComparingTo("1");
    }

    private long crearBebida(String nombre) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/bebidas").header("Authorization", personas.bearer(RolCodigo.ADMINISTRACION))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombre":"%s","presentacion":"750 ml","tipoId":3,"unidadId":1,"unidadesPorBulto":6}""".formatted(nombre)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(respuesta, "$.id")).longValue();
    }

    private void retirar(long id, int cantidad) throws Exception {
        operar("eventos/" + evento + "/entregas/" + barra + "/retiros",
                "{\"renglones\":[{\"bebidaId\":%d,\"cantidad\":%d}]}".formatted(id, cantidad)).andExpect(status().isCreated());
    }

    private void rotura(long id, int cantidad) throws Exception {
        operar("ajustes/rotura", "{\"ubicacionId\":%d,\"bebidaId\":%d,\"cantidad\":%d,\"motivoId\":11}".formatted(barra, id, cantidad))
                .andExpect(status().isOk());
    }

    private String renglon(long id, int devuelto, int remanente) {
        return "{\"bebidaId\":%d,\"devuelto\":%d,\"remanente\":%d}".formatted(id, devuelto, remanente);
    }

    private ResultActions devolver(String renglones) throws Exception { return devolver(renglones, ""); }

    private ResultActions devolver(String renglones, String desdeTransicion) throws Exception {
        return operar("eventos/" + evento + "/entregas/" + barra + "/devolucion",
                "{\"renglones\":[%s],\"transicion\":[%s]}".formatted(renglones, desdeTransicion));
    }

    private ResultActions operar(String ruta, String cuerpo) throws Exception {
        var pedido = post("/api/v1/" + ruta).header("Authorization", autorizacion);
        if (cuerpo != null) pedido.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        return mvc.perform(pedido);
    }

    private BigDecimal saldo(short ubicacion, long id) {
        return jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id=? AND bebida_id=?", BigDecimal.class, ubicacion, id);
    }

    private String estadoEntrega() {
        return jdbc.queryForObject("SELECT estado FROM entrega WHERE evento_id=? AND ubicacion_destino_id=?", String.class, evento, barra);
    }

    private int cantidadConsumos() {
        return jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE evento_id=? AND tipo='CONSUMO'", Integer.class, evento);
    }

    private List<Map<String, Object>> stock() { return jdbc.queryForList("SELECT * FROM stock_ubicacion ORDER BY ubicacion_id,bebida_id"); }
    private List<Map<String, Object>> movimientos() { return jdbc.queryForList("SELECT * FROM movimiento_stock ORDER BY movimiento_id"); }

    private Map<String, List<Map<String, Object>>> persistido() {
        Map<String, List<Map<String, Object>>> resultado = new LinkedHashMap<>();
        resultado.put("stock", stock());
        resultado.put("movimientos", movimientos());
        for (String tabla : List.of("evento", "entrega", "consumo_evento", "cambio_estado_evento", "orden_preparacion", "notificacion")) {
            resultado.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY 1"));
        }
        return resultado;
    }
}
