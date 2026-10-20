package ar.edu.utn.vastio.bebida.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.bebida.aplicacion.AjusteService.DatosAjuste;
import ar.edu.utn.vastio.bebida.aplicacion.AjusteService.Resultado;
import ar.edu.utn.vastio.bebida.aplicacion.BebidaService.DatosBebida;
import ar.edu.utn.vastio.bebida.aplicacion.IngresoService.DatosIngreso;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Transacciones independientes y confirmadas en el PostgreSQL de Testcontainers. La primera operación termina pero
 * retiene su transacción; pg_blocking_pids confirma la espera real de la segunda antes de liberar el commit.
 * Así, sin fila previa, la versión defectuosa lee cero y recién espera en el UPSERT, acumulando 6 + 8.
 */
@PruebaDeIntegracion
@Timeout(60)
class RecuentoConcurrenciaIT {
    @Autowired AjusteService ajustes;
    @Autowired IngresoService ingresos;
    @Autowired InventarioInicialService inventario;
    @Autowired BebidaService bebidas;
    @Autowired Personas personas;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transacciones;

    private final List<Long> bebidasCreadas = new ArrayList<>();
    private final List<Short> ubicacionesCreadas = new ArrayList<>();
    private final CountDownLatch aplicada = new CountDownLatch(1);
    private final CountDownLatch continuar = new CountDownLatch(1);
    private ExecutorService hilos;
    private long bebida;
    private long usuario;

    @BeforeEach
    void datos() {
        hilos = Executors.newFixedThreadPool(2);
        bebida = nuevaBebida();
        usuario = personas.de(RolCodigo.COMPRAS).getId();
    }

    @ParameterizedTest
    @CsvSource({"false,6,8", "false,8,6", "true,6,8", "true,8,6"})
    void dosRecuentosRespetanElOrdenEfectivoSinSumarCantidadesAbsolutas(boolean filaPrevia, int primero, int segundo) throws Exception {
        int inicial = filaPrevia ? 10 : 0;
        if (filaPrevia) saldoInicial((short) 2, inicial);
        var pidA = new CompletableFuture<Integer>();
        Future<Resultado> a = ejecutar(() -> recuento((short) 2, bebida, primero), pidA, true);
        esperar(aplicada);
        var pidB = new CompletableFuture<Integer>();
        Future<Resultado> b = ejecutar(() -> recuento((short) 2, bebida, segundo), pidB, false);
        comprobarEspera(pidA.get(10, TimeUnit.SECONDS), pidB.get(10, TimeUnit.SECONDS));
        continuar.countDown();

        Resultado rA = a.get(20, TimeUnit.SECONDS);
        Resultado rB = b.get(20, TimeUnit.SECONDS);
        assertThat(rA.saldoAnterior()).isEqualByComparingTo(String.valueOf(inicial));
        assertThat(rA.saldo()).isEqualByComparingTo(String.valueOf(primero));
        assertThat(rB.saldoAnterior()).isEqualByComparingTo(String.valueOf(primero));
        assertThat(rB.saldo()).isEqualByComparingTo(String.valueOf(segundo));
        assertThat(saldo((short) 2, bebida)).isEqualByComparingTo(String.valueOf(segundo));
        List<Map<String, Object>> movimientos = movimientos();
        assertThat(movimientos).hasSize(2);
        verificarAjuste(movimientos.get(0), primero - inicial, (short) 2);
        verificarAjuste(movimientos.get(1), segundo - primero, (short) 2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sinDiferenciaMantieneElBloqueoSinCrearMovimientosNiFilasFicticias(boolean filaPrevia) throws Exception {
        int contado = filaPrevia ? 10 : 0;
        if (filaPrevia) saldoInicial((short) 2, contado);
        var pidA = new CompletableFuture<Integer>();
        Future<Resultado> a = ejecutar(() -> recuento((short) 2, bebida, contado), pidA, true);
        esperar(aplicada);
        var pidB = new CompletableFuture<Integer>();
        Future<Resultado> b = ejecutar(() -> recuento((short) 2, bebida, contado), pidB, false);
        comprobarEspera(pidA.get(10, TimeUnit.SECONDS), pidB.get(10, TimeUnit.SECONDS));
        continuar.countDown();
        assertThat(a.get(20, TimeUnit.SECONDS).movimiento()).isNull();
        assertThat(b.get(20, TimeUnit.SECONDS).movimiento()).isNull();
        assertThat(movimientos()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_ubicacion WHERE bebida_id = ?", Integer.class, bebida))
                .isEqualTo(filaPrevia ? 1 : 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ubicacionesDistintasAvanzanEnParaleloAunqueLaBebidaSeaLaMisma(boolean otraBebida) throws Exception {
        long bebidaB = otraBebida ? nuevaBebida() : bebida;
        Future<Resultado> a = ejecutar(() -> recuento((short) 1, bebida, 6), new CompletableFuture<>(), true);
        esperar(aplicada);
        Future<Resultado> b = ejecutar(() -> recuento((short) 2, bebidaB, 8), new CompletableFuture<>(), false);
        // Debe confirmar mientras A todavía retiene su bloqueo: no alcanza una largada simultánea accidental.
        assertThat(b.get(10, TimeUnit.SECONDS).saldo()).isEqualByComparingTo("8");
        assertThat(continuar.getCount()).isEqualTo(1);
        continuar.countDown();
        a.get(20, TimeUnit.SECONDS);
        assertThat(saldo((short) 1, bebida)).isEqualByComparingTo("6");
        assertThat(saldo((short) 2, bebidaB)).isEqualByComparingTo("8");
    }

    @Test
    void bebidasDistintasDeUnaUbicacionSeSerializanPorElAlcanceElegido() throws Exception {
        long otra = nuevaBebida();
        var pidA = new CompletableFuture<Integer>();
        Future<Resultado> a = ejecutar(() -> recuento((short) 2, bebida, 6), pidA, true);
        esperar(aplicada);
        var pidB = new CompletableFuture<Integer>();
        Future<Resultado> b = ejecutar(() -> recuento((short) 2, otra, 8), pidB, false);
        comprobarEspera(pidA.get(10, TimeUnit.SECONDS), pidB.get(10, TimeUnit.SECONDS));
        continuar.countDown();
        a.get(20, TimeUnit.SECONDS);
        b.get(20, TimeUnit.SECONDS);
        assertThat(saldo((short) 2, bebida)).isEqualByComparingTo("6");
        assertThat(saldo((short) 2, otra)).isEqualByComparingTo("8");
    }

    @ParameterizedTest
    @CsvSource({"INGRESO,0,4", "MERMA,10,8", "INVENTARIO_INICIAL,0,4"})
    void elRecuentoLeeElSaldoConfirmadoPorOtroMovimiento(String tipo, int inicial, int anterior) throws Exception {
        short ubicacion = tipo.equals("INVENTARIO_INICIAL") ? nuevaTransicion() : 1;
        if (inicial != 0) saldoInicial(ubicacion, inicial);
        var pidA = new CompletableFuture<Integer>();
        Future<?> a = ejecutar(() -> { otroMovimiento(tipo, ubicacion); return null; }, pidA, true);
        esperar(aplicada);
        var pidB = new CompletableFuture<Integer>();
        Future<Resultado> b = ejecutar(() -> recuento(ubicacion, bebida, 6), pidB, false);
        comprobarEspera(pidA.get(10, TimeUnit.SECONDS), pidB.get(10, TimeUnit.SECONDS));
        continuar.countDown();
        a.get(20, TimeUnit.SECONDS);
        Resultado resultado = b.get(20, TimeUnit.SECONDS);
        assertThat(resultado.saldoAnterior()).isEqualByComparingTo(String.valueOf(anterior));
        assertThat(resultado.saldo()).isEqualByComparingTo("6");
        assertThat(saldo(ubicacion, bebida)).isEqualByComparingTo("6");
        assertThat(movimientos()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INGRESO", "MERMA"})
    void unMovimientoPosteriorAlRecuentoNoSePierde(String tipo) throws Exception {
        var pidA = new CompletableFuture<Integer>();
        Future<Resultado> a = ejecutar(() -> recuento((short) 1, bebida, 6), pidA, true);
        esperar(aplicada);
        var pidB = new CompletableFuture<Integer>();
        Future<?> b = ejecutar(() -> { otroMovimiento(tipo, (short) 1); return null; }, pidB, false);
        comprobarEspera(pidA.get(10, TimeUnit.SECONDS), pidB.get(10, TimeUnit.SECONDS));
        continuar.countDown();
        assertThat(a.get(20, TimeUnit.SECONDS).saldo()).isEqualByComparingTo("6");
        b.get(20, TimeUnit.SECONDS);
        assertThat(saldo((short) 1, bebida)).isEqualByComparingTo(tipo.equals("INGRESO") ? "10" : "4");
        assertThat(movimientos()).hasSize(2);
    }

    @Test
    void inventarioInicialEsperaYRespetaElCierreProducidoPorElRecuento() throws Exception {
        short ubicacion = nuevaTransicion();
        var pidA = new CompletableFuture<Integer>();
        Future<Resultado> a = ejecutar(() -> recuento(ubicacion, bebida, 6), pidA, true);
        esperar(aplicada);
        var pidB = new CompletableFuture<Integer>();
        Future<?> b = ejecutar(() -> { otroMovimiento("INVENTARIO_INICIAL", ubicacion); return null; }, pidB, false);
        comprobarEspera(pidA.get(10, TimeUnit.SECONDS), pidB.get(10, TimeUnit.SECONDS));
        continuar.countDown();
        a.get(20, TimeUnit.SECONDS);
        ExecutionException error = assertThrows(ExecutionException.class, () -> b.get(20, TimeUnit.SECONDS));
        assertThat(error.getCause()).isInstanceOfSatisfying(ProblemaException.class,
                e -> assertThat(e.getCodigo()).isEqualTo("CARGA_INICIAL_CERRADA"));
        assertThat(saldo(ubicacion, bebida)).isEqualByComparingTo("6");
        assertThat(movimientos()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void contarUnaCantidadNoNegativaDesdeSaldoNegativoTerminaSinSaldoNegativo(int contado) {
        saldoInicial((short) 2, -3);
        Resultado resultado = recuento((short) 2, bebida, contado);
        assertThat(resultado.saldo()).isEqualByComparingTo(String.valueOf(contado));
        assertThat(resultado.avisoSaldoNegativo()).isFalse();
    }

    private <T> Future<T> ejecutar(Supplier<T> operacion, CompletableFuture<Integer> pid, boolean retenerCommit) {
        return hilos.submit(() -> transacciones.execute(t -> {
            jdbc.execute("SET LOCAL lock_timeout = '20s'");
            jdbc.execute("SET LOCAL statement_timeout = '30s'");
            assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("read committed");
            pid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
            T resultado = operacion.get();
            if (retenerCommit) {
                aplicada.countDown();
                esperar(continuar);
            }
            return resultado;
        }));
    }

    private void comprobarEspera(int bloqueador, int bloqueado) {
        assertThat(bloqueado).isNotEqualTo(bloqueador);
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, bloqueador, bloqueado))) return;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        throw new AssertionError("No se observó la espera real entre las dos transacciones en PostgreSQL");
    }

    private static void esperar(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("Se agotó el plazo de coordinación");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Resultado recuento(short ubicacion, long articulo, int cantidad) {
        return ajustes.registrarRecuento(new DatosAjuste(ubicacion, articulo, BigDecimal.valueOf(cantidad), (short) 12, null), usuario);
    }

    private void otroMovimiento(String tipo, short ubicacion) {
        switch (tipo) {
            case "INGRESO" -> ingresos.registrar(new DatosIngreso(null, null, List.of(new IngresoService.Renglon(bebida, BigDecimal.valueOf(4)))), usuario);
            case "MERMA" -> ajustes.declararRotura(new DatosAjuste(ubicacion, bebida, BigDecimal.valueOf(2), (short) 11, null), usuario);
            case "INVENTARIO_INICIAL" -> inventario.guardar(ubicacion, List.of(new InventarioInicialService.Renglon(bebida, BigDecimal.valueOf(4))), usuario);
            default -> throw new IllegalArgumentException(tipo);
        }
    }

    private long nuevaBebida() {
        long id = bebidas.crear(new DatosBebida("Concurrencia " + UUID.randomUUID(), "750 ml", (short) 3, (short) 1,
                (short) 6, null, List.of())).getId();
        bebidasCreadas.add(id);
        return id;
    }

    private short nuevaTransicion() {
        short id = jdbc.queryForObject("INSERT INTO ubicacion (nombre, tipo) VALUES (?, 'TRANSICION') RETURNING ubicacion_id", Short.class,
                "Concurrencia " + UUID.randomUUID());
        ubicacionesCreadas.add(id);
        return id;
    }

    private void saldoInicial(short ubicacion, int cantidad) {
        jdbc.update("INSERT INTO stock_ubicacion (ubicacion_id, bebida_id, cantidad, fecha_actualizacion) VALUES (?, ?, ?, now())", ubicacion, bebida, cantidad);
    }

    private BigDecimal saldo(short ubicacion, long articulo) {
        return jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id = ? AND bebida_id = ?", BigDecimal.class, ubicacion, articulo);
    }

    private List<Map<String, Object>> movimientos() {
        return jdbc.queryForList("SELECT * FROM movimiento_stock WHERE bebida_id = ? ORDER BY movimiento_id", bebida);
    }

    private void verificarAjuste(Map<String, Object> m, int diferencia, short ubicacion) {
        assertThat(m).containsEntry("tipo", "AJUSTE").containsEntry("usuario_id", usuario).containsEntry("bebida_id", bebida);
        assertThat(((Number) m.get("motivo_id")).intValue()).isEqualTo(12);
        assertThat((BigDecimal) m.get("cantidad")).isEqualByComparingTo(String.valueOf(Math.abs(diferencia)));
        String lado = diferencia > 0 ? "ubicacion_destino_id" : "ubicacion_origen_id";
        assertThat(((Number) m.get(lado)).shortValue()).isEqualTo(ubicacion);
        assertThat(m.get(diferencia > 0 ? "ubicacion_origen_id" : "ubicacion_destino_id")).isNull();
        assertThat(m.get("fecha_hora")).isNotNull();
    }

    @AfterEach
    void limpiarSoloLosDatosCreadosEnElPostgresTemporal() throws InterruptedException {
        continuar.countDown();
        hilos.shutdownNow();
        assertThat(hilos.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        transacciones.executeWithoutResult(t -> {
            // Mismo mecanismo que IngresoConcurrenciaIT; solo nuestros IDs, nunca bases o movimientos reales.
            jdbc.execute("SET LOCAL session_replication_role = replica");
            for (long id : bebidasCreadas) {
                List<Long> cabeceras = jdbc.queryForList("SELECT DISTINCT ingreso_id FROM movimiento_stock WHERE bebida_id = ? AND ingreso_id IS NOT NULL", Long.class, id);
                jdbc.update("DELETE FROM movimiento_stock WHERE bebida_id = ?", id);
                jdbc.update("DELETE FROM stock_ubicacion WHERE bebida_id = ?", id);
                cabeceras.forEach(c -> jdbc.update("DELETE FROM ingreso WHERE ingreso_id = ?", c));
                jdbc.update("DELETE FROM bebida WHERE bebida_id = ?", id);
            }
            ubicacionesCreadas.forEach(id -> jdbc.update("DELETE FROM ubicacion WHERE ubicacion_id = ?", id));
        });
    }
}
