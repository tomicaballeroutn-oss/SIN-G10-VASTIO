package ar.edu.utn.vastio.bebida.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

@PruebaDeIntegracion
@Transactional
class EstadisticaConsumoIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Escenario escenario;
    @Autowired Personas personas;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired PlatformTransactionManager transacciones;
    long bebida;
    long otra;
    long a;
    long b;
    long c;

    @BeforeEach
    void preparar() {
        bebida = bebida("Estadística A");
        otra = bebida("Estadística B");
        a = evento(1, "avril", "CERRADO", 100, 2);
        b = evento(2, "avril", "CERRADO", 200, 2); // Sin consumos: también cuenta.
        c = evento(3, "club", "CERRADO", null, 4);
        consumo(a, bebida, "10.01");
        consumo(a, otra, "2.00");
        consumo(c, bebida, "9.00");
    }

    @Test
    void totalesGruposYCocientesDeSumasSinDuplicarInvitados() throws Exception {
        consultar("", RolCodigo.COMPRAS).andExpect(status().isOk())
                .andExpect(jsonPath("$.unidad").value("BOTELLAS"))
                .andExpect(jsonPath("$.denominador").value("ASISTENTES"))
                .andExpect(jsonPath("$.resumen.eventos").value(3))
                .andExpect(jsonPath("$.resumen.eventosConConsumoRegistrado").value(2))
                .andExpect(jsonPath("$.resumen.consumoTotal").value(21.01))
                .andExpect(jsonPath("$.resumen.promedioPorEvento").value(7.0033))
                .andExpect(jsonPath("$.resumen.eventosConInvitadosValidos").value(2))
                .andExpect(jsonPath("$.resumen.invitadosRegistrados").value(300))
                .andExpect(jsonPath("$.resumen.consumoConInvitadosValidos").value(12.01))
                .andExpect(jsonPath("$.resumen.porInvitadoRegistrado").value(0.0400))
                .andExpect(jsonPath("$.porBebida[?(@.bebidaId == %d)].medidas.promedioPorEvento".formatted(bebida)).value(org.hamcrest.Matchers.hasItem(6.3367)))
                .andExpect(jsonPath("$.porTipoEvento[?(@.tipoEventoId == 2)].medidas.consumoTotal").value(org.hamcrest.Matchers.hasItem(12.01)))
                .andExpect(jsonPath("$.detalle[?(@.tipoEventoId == 2 && @.bebidaId == %d)].medidas.invitadosRegistrados".formatted(bebida)).value(org.hamcrest.Matchers.hasItem(300)));
    }

    @Test
    void verificaTodasLasMedidasDeLaMatrizTipoBebidaConCerosYSinHistorial() throws Exception {
        // Cero explícitamente registrado, distinto de una fila ausente.
        consumo(b, otra, "0.00");
        long sinConsumo = bebida("Estadística sin historial");
        long soloCero = bebida("Estadística registrada en cero");
        consumo(b, soloCero, "0.00");
        String respuesta = consultar("", RolCodigo.COMPRAS).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        verificarDetalle(respuesta, 2, bebida, 2, 1, "10.01", "5.0050", 2, 300, "10.01", "0.0334");
        verificarDetalle(respuesta, 2, otra, 2, 2, "2.00", "1.0000", 2, 300, "2.00", "0.0067");
        verificarDetalle(respuesta, 2, sinConsumo, 2, 0, "0", "0.0000", 2, 300, "0", "0.0000");
        verificarDetalle(respuesta, 2, soloCero, 2, 1, "0", "0.0000", 2, 300, "0", "0.0000");
        verificarDetalle(respuesta, 4, bebida, 1, 1, "9.00", "9.0000", 0, 0, "0", null);
        verificarDetalle(respuesta, 4, otra, 1, 0, "0", "0.0000", 0, 0, "0", null);
        verificarDetalle(respuesta, 4, sinConsumo, 1, 0, "0", "0.0000", 0, 0, "0", null);
        verificarDetalle(respuesta, 4, soloCero, 1, 0, "0", "0.0000", 0, 0, "0", null);
        // Casamiento es un tipo activo sin eventos: cero total, promedios inexistentes.
        for (long articulo : List.of(bebida, otra, sinConsumo, soloCero)) {
            verificarDetalle(respuesta, 1, articulo, 0, 0, "0", null, 0, 0, "0", null);
        }
        String filtrada = consultar("&tipoEventoId=2&bebidaId=" + sinConsumo, RolCodigo.COMPRAS)
                .andExpect(status().isOk()).andExpect(jsonPath("$.detalle.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        verificarDetalle(filtrada, 2, sinConsumo, 2, 0, "0", "0.0000", 2, 300, "0", "0.0000");
    }

    private void verificarDetalle(String respuesta, int tipo, long articulo, long eventos, long conRegistro,
            String consumo, String porEvento, long validos, long invitados, String consumoValido, String porInvitado) {
        List<Map<String, Object>> filas = JsonPath.read(respuesta,
                "$.detalle[?(@.tipoEventoId == %d && @.bebidaId == %d)].medidas".formatted(tipo, articulo));
        assertThat(filas).hasSize(1);
        Map<String, Object> medidas = filas.getFirst();
        assertThat(medidas).containsOnlyKeys("eventos", "eventosConConsumoRegistrado", "consumoTotal",
                "promedioPorEvento", "eventosConInvitadosValidos", "invitadosRegistrados",
                "consumoConInvitadosValidos", "porInvitadoRegistrado", "eventosConAsistenciaReal");
        assertThat(((Number) medidas.get("eventos")).longValue()).isEqualTo(eventos);
        assertThat(((Number) medidas.get("eventosConConsumoRegistrado")).longValue()).isEqualTo(conRegistro);
        assertThat(((Number) medidas.get("eventosConInvitadosValidos")).longValue()).isEqualTo(validos);
        assertThat(((Number) medidas.get("invitadosRegistrados")).longValue()).isEqualTo(invitados);
        verificarDecimal(medidas.get("consumoTotal"), consumo);
        verificarDecimal(medidas.get("promedioPorEvento"), porEvento);
        verificarDecimal(medidas.get("consumoConInvitadosValidos"), consumoValido);
        verificarDecimal(medidas.get("porInvitadoRegistrado"), porInvitado);
    }

    private void verificarDecimal(Object valor, String esperado) {
        if (esperado == null) assertThat(valor).isNull();
        else assertThat(new BigDecimal(valor.toString())).isEqualByComparingTo(esperado);
    }

    @Test
    void combinaFiltrosInclusivosSinEliminarEventosSinLaBebida() throws Exception {
        consultar("&desde=2038-05-01&hasta=2038-05-02&tipoEventoId=2&salonId=1&bebidaId=" + bebida, RolCodigo.ADMINISTRACION)
                .andExpect(status().isOk()).andExpect(jsonPath("$.resumen.eventos").value(2))
                .andExpect(jsonPath("$.resumen.consumoTotal").value(10.01))
                .andExpect(jsonPath("$.resumen.promedioPorEvento").value(5.0050))
                .andExpect(jsonPath("$.resumen.porInvitadoRegistrado").value(0.0334))
                .andExpect(jsonPath("$.porBebida.length()").value(1))
                .andExpect(jsonPath("$.porTipoEvento.length()").value(1));
        consultar("&desde=2038-05-02&hasta=2038-05-02&bebidaId=" + otra, RolCodigo.COMPRAS)
                .andExpect(jsonPath("$.resumen.eventos").value(1))
                .andExpect(jsonPath("$.resumen.consumoTotal").value(0))
                .andExpect(jsonPath("$.resumen.promedioPorEvento").value(0))
                .andExpect(jsonPath("$.resumen.porInvitadoRegistrado").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRE_RESERVA", "SENADO", "CONTRATADO", "CONFIRMADO", "EN_CURSO", "REALIZADO", "LIBERADA", "CANCELADO"})
    void excluyeOtrosEstadosInclusoConConsolidado(String estado) throws Exception {
        long excluido = evento(4, "avril", estado, 100, 2);
        consumo(excluido, bebida, "1000");
        consultar("", RolCodigo.COMPRAS).andExpect(jsonPath("$.resumen.eventos").value(3))
                .andExpect(jsonPath("$.resumen.consumoTotal").value(21.01));
    }

    @Test
    void sinEventosYTiposSinHistorialNoInventanPromedios() throws Exception {
        consultar("&tipoEventoId=1", RolCodigo.COMPRAS).andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.eventos").value(0))
                .andExpect(jsonPath("$.resumen.consumoTotal").value(0))
                .andExpect(jsonPath("$.resumen.promedioPorEvento").isEmpty())
                .andExpect(jsonPath("$.resumen.porInvitadoRegistrado").isEmpty())
                .andExpect(jsonPath("$.porTipoEvento[0].medidas.promedioPorEvento").isEmpty());
    }

    @Test
    void ceroYNuloExcluyenTambienElNumeradorYNoLeeAsistenciaParcial() throws Exception {
        jdbc.update("UPDATE evento SET cantidad_invitados = 0 WHERE evento_id = ?", a);
        jdbc.update("INSERT INTO asistencia_segmento(evento_id,tipo_segmento_id,cantidad_real) VALUES (?,1,999)", c);
        consultar("", RolCodigo.COMPRAS).andExpect(jsonPath("$.resumen.invitadosRegistrados").value(200))
                .andExpect(jsonPath("$.resumen.eventosConInvitadosValidos").value(1))
                .andExpect(jsonPath("$.resumen.consumoConInvitadosValidos").value(0))
                .andExpect(jsonPath("$.resumen.porInvitadoRegistrado").value(0));
        consultar("&salonId=2", RolCodigo.COMPRAS).andExpect(jsonPath("$.resumen.consumoTotal").value(9))
                .andExpect(jsonPath("$.resumen.porInvitadoRegistrado").isEmpty());
    }

    @Test
    void conservaReferenciasInactivasYBebidasSinConsumo() throws Exception {
        jdbc.update("UPDATE bebida SET activo=false WHERE bebida_id=?", bebida);
        jdbc.update("UPDATE tipo_evento SET activo=false WHERE tipo_evento_id=4");
        consultar("", RolCodigo.COMPRAS).andExpect(jsonPath("$.porBebida[?(@.bebidaId == %d)].activo".formatted(bebida)).value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$.porTipoEvento[?(@.tipoEventoId == 4)].activo").value(org.hamcrest.Matchers.hasItem(false)));
        long sinConsumo = bebida("Sin historial");
        consultar("&bebidaId=" + sinConsumo, RolCodigo.COMPRAS)
                .andExpect(jsonPath("$.resumen.eventos").value(3))
                .andExpect(jsonPath("$.porBebida[0].medidas.consumoTotal").value(0));
        consultar("&desde=2039-01-01&hasta=2039-12-31&bebidaId=" + bebida, RolCodigo.COMPRAS)
                .andExpect(jsonPath("$.porBebida[0].activo").value(false));
    }

    @Test
    void permiteHistorialCompletoYLimitesIndividuales() throws Exception {
        mvc.perform(get("/api/v1/estadisticas/consumo").header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COMPRAS)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resumen.eventos").value(3));
        mvc.perform(get("/api/v1/estadisticas/consumo?desde=2038-05-02")
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COMPRAS)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resumen.eventos").value(2));
        mvc.perform(get("/api/v1/estadisticas/consumo?hasta=2038-05-01")
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(RolCodigo.COMPRAS)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resumen.eventos").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"&desde=2038-06-01&hasta=2038-05-01", "&desde=mal", "&desde=2038-02-30", "&salonId=0", "&tipoEventoId=-1", "&bebidaId=0", "&salonId=999999", "&bebidaId=abc", "&desde=0000-01-01"})
    void rechazaFiltrosInvalidos(String filtro) throws Exception {
        consultar(filtro, RolCodigo.COMPRAS).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"&salonId=32000", "&tipoEventoId=32000", "&bebidaId=9223372036854775807"})
    void validaReferencias(String filtro) throws Exception {
        consultar(filtro, RolCodigo.COMPRAS).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"DIRECCION", "COORDINACION", "ADMINISTRACION", "COMPRAS"})
    void permitePerfiles(RolCodigo rol) throws Exception {
        consultar("", rol).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(value = RolCodigo.class, names = {"VENDEDORA", "PLANNER", "BARRA", "COCINA"})
    void deniegaPerfiles(RolCodigo rol) throws Exception {
        consultar("", rol).andExpect(status().isForbidden());
    }

    @Test
    void exigeSesion() throws Exception {
        mvc.perform(get("/api/v1/estadisticas/consumo")).andExpect(status().isUnauthorized());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void consultasRepetidasNoModificanDatosNiDependenDelStock() throws Exception {
        jdbc.update("INSERT INTO stock_ubicacion(ubicacion_id,bebida_id,cantidad,fecha_actualizacion) VALUES (2,?,999,now())", bebida);
        String autorizacion = personas.bearer(RolCodigo.COMPRAS);
        em.flush();
        // Confirma de verdad el fixture y termina la transacción exterior del test.
        // Cada petición siguiente abre y finaliza su propia transacción de aplicación.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        TransactionTemplate observador = new TransactionTemplate(transacciones);
        observador.setReadOnly(true);
        var antes = observador.execute(estado -> persistido());
        String primera = null;
        for (int intento = 0; intento < 3; intento++) {
            String respuesta = mvc.perform(get("/api/v1/estadisticas/consumo?desde=2038-05-01&hasta=2038-05-31")
                    .header(HttpHeaders.AUTHORIZATION, autorizacion)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.resumen.consumoTotal").value(21.01))
                    .andReturn().getResponse().getContentAsString();
            if (primera == null) primera = respuesta;
            else assertThat(respuesta).isEqualTo(primera);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var despues = observador.execute(estado -> persistido());
            assertThat(despues).isEqualTo(antes);
        }
        // @DirtiesContext retira la base descartable con el fixture confirmado al terminar.
    }

    @Test
    void observaHistorialSinteticoDeVeinteAniosSinConsultasPorEvento() throws Exception {
        long cliente = escenario.cliente("Historial sintético B501");
        long vendedora = personas.de(RolCodigo.VENDEDORA).getId();
        long planner = personas.de(RolCodigo.PLANNER).getId();
        jdbc.update("""
                WITH serie AS (
                    SELECT n, date '2070-01-01' + ((n-1)*7300/3000) AS fecha FROM generate_series(1,3000) n
                ), unidades AS (
                    INSERT INTO unidad_comercializable(salon_id,fecha,turno_id)
                    SELECT 1,fecha,2 FROM serie RETURNING unidad_id,fecha
                )
                INSERT INTO evento(codigo,unidad_id,cliente_id,tipo_evento_id,vendedora_id,planner_id,estado,nombre,
                    cantidad_invitados,invitados_definitivos,importe_sena,moneda_sena,fecha_sena,firmante_dni,fecha_firma_contrato,version)
                SELECT 'EV-2070-'||lpad(s.n::text,5,'0'),u.unidad_id,?,((s.n-1)%5)+1,?,?,'CERRADO','Historial B501 '||s.n,
                    CASE s.n%4 WHEN 0 THEN NULL WHEN 1 THEN 0 WHEN 2 THEN 100 ELSE 200 END,
                    true,100,'ARS',date '2069-01-01','30111222',date '2069-01-02',0
                FROM serie s JOIN unidades u USING(fecha)""", cliente, vendedora, planner);
        jdbc.update("""
                INSERT INTO bebida(nombre,presentacion,tipo_bebida_id,unidad_manipulacion_id,unidades_por_bulto,activo)
                SELECT 'Historial B501 bebida '||n,'750 ml',3,1,6,true FROM generate_series(1,10) n""");
        jdbc.update("""
                INSERT INTO consumo_evento(evento_id,bebida_id,cantidad_entregada,cantidad_devuelta,cantidad_remanente,cantidad_consumida)
                SELECT e.evento_id,b.bebida_id,10.25,0,0,10.25 FROM evento e CROSS JOIN bebida b
                WHERE e.codigo LIKE 'EV-2070-%' AND b.nombre LIKE 'Historial B501 bebida %'""");
        String autorizacion = personas.bearer(RolCodigo.COMPRAS);
        em.flush();
        var estadisticas = emf.unwrap(SessionFactory.class).getStatistics();
        boolean habilitadas = estadisticas.isStatisticsEnabled();
        estadisticas.setStatisticsEnabled(true);
        try {
            for (int muestra = 0; muestra < 4; muestra++) {
                estadisticas.clear();
                long inicio = System.nanoTime();
                var respuesta = mvc.perform(get("/api/v1/estadisticas/consumo?desde=2070-01-01&hasta=2089-12-31")
                        .header(HttpHeaders.AUTHORIZATION, autorizacion)).andExpect(status().isOk())
                        .andExpect(jsonPath("$.resumen.eventos").value(3000))
                        .andExpect(jsonPath("$.resumen.consumoTotal").value(307500))
                        .andExpect(jsonPath("$.resumen.promedioPorEvento").value(102.5))
                        .andExpect(jsonPath("$.resumen.eventosConInvitadosValidos").value(1500))
                        .andExpect(jsonPath("$.resumen.invitadosRegistrados").value(225000))
                        .andExpect(jsonPath("$.resumen.consumoConInvitadosValidos").value(153750))
                        .andExpect(jsonPath("$.resumen.porInvitadoRegistrado").value(0.6833))
                        .andReturn().getResponse();
                long milisegundos = (System.nanoTime() - inicio) / 1_000_000;
                long consultas = estadisticas.getPrepareStatementCount();
                // Incluye autenticación y catálogos: un techo holgado detecta N+1 sin depender del reloj.
                assertThat(consultas).isBetween(6L, 12L);
                System.out.printf("B501_RENDIMIENTO muestra=%d eventos=3000 consumos=30000 sql=%d ms=%d bytes=%d%n",
                        muestra, consultas, milisegundos, respuesta.getContentAsByteArray().length);
            }
        } finally {
            estadisticas.setStatisticsEnabled(habilitadas);
        }
    }

    private Map<String, List<Map<String, Object>>> persistido() {
        Map<String, List<Map<String, Object>>> tablas = new LinkedHashMap<>();
        for (String tabla : List.of("evento", "consumo_evento", "movimiento_stock", "stock_ubicacion", "preparacion_evento", "orden_preparacion", "cambio_estado_evento", "asistencia_segmento", "bebida", "cliente", "unidad_comercializable", "entrega")) {
            tablas.put(tabla, jdbc.queryForList("SELECT * FROM " + tabla + " ORDER BY 1,2"));
        }
        return tablas;
    }

    private ResultActions consultar(String filtro, RolCodigo rol) throws Exception {
        String fechas = (filtro.contains("&desde=") ? "" : "&desde=2038-05-01")
                + (filtro.contains("&hasta=") ? "" : "&hasta=2038-05-31");
        return mvc.perform(get("/api/v1/estadisticas/consumo?" + (fechas + filtro).substring(1))
                .header(HttpHeaders.AUTHORIZATION, personas.bearer(rol)));
    }

    private long evento(int dia, String salon, String estado, Integer invitados, int tipo) {
        long id = escenario.evento(salon, LocalDate.of(2038, 5, dia), "noche", estado,
                personas.de(RolCodigo.VENDEDORA), personas.de(RolCodigo.PLANNER), "Estadística " + dia);
        jdbc.update("UPDATE evento SET cantidad_invitados=?, tipo_evento_id=? WHERE evento_id=?", invitados, tipo, id);
        return id;
    }

    private long bebida(String nombre) {
        return jdbc.queryForObject("""
                INSERT INTO bebida(nombre,presentacion,tipo_bebida_id,unidad_manipulacion_id,unidades_por_bulto,activo)
                VALUES (?,'750 ml',3,1,6,true) RETURNING bebida_id""", Long.class, nombre);
    }

    private void consumo(long evento, long articulo, String cantidad) {
        jdbc.update("""
                INSERT INTO consumo_evento(evento_id,bebida_id,cantidad_entregada,cantidad_devuelta,cantidad_remanente,cantidad_consumida)
                VALUES (?,?,?,0,0,?)""", evento, articulo, new BigDecimal(cantidad), new BigDecimal(cantidad));
    }
}
