package ar.edu.utn.vastio.bebida.aplicacion;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import ar.edu.utn.vastio.*;
import ar.edu.utn.vastio.bebida.aplicacion.PreparacionService.*;
import ar.edu.utn.vastio.bebida.aplicacion.BebidaService.DatosBebida;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/** Retiene commits y comprueba con pg_blocking_pids la espera efectiva entre operaciones. */
@PruebaDeIntegracion
@Timeout(60)
class PreparacionConcurrenciaIT {
    @Autowired PreparacionService preparaciones;
    @Autowired BebidaService bebidas;
    @Autowired Personas personas;
    @Autowired Escenario escenario;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transacciones;
    @Autowired AjusteService ajustes;
    @Autowired EntregaService entregas;
    long evento, bebida, usuario;
    short barra, destino;
    ExecutorService hilos;
    CountDownLatch aplicada, continuar;
    @BeforeEach void datos() {
        hilos=Executors.newFixedThreadPool(2); aplicada=new CountDownLatch(1); continuar=new CountDownLatch(1);
        usuario=personas.usuario("prep."+UUID.randomUUID(),"Preparador concurrente",RolCodigo.COMPRAS).getId();
        personas.de(RolCodigo.ADMINISTRACION);
        bebida=bebidas.crear(new DatosBebida("Preparación concurrente "+UUID.randomUUID(),"750 ml",(short)3,(short)1,(short)6,null,List.of())).getId();
        evento=escenario.evento("avril",LocalDate.of(2036,3,2),"noche","CONFIRMADO",personas.de(RolCodigo.VENDEDORA),personas.de(RolCodigo.PLANNER),"Evento concurrente");
        destino=jdbc.queryForObject("INSERT INTO ubicacion(nombre,tipo,activo) VALUES (?,'TRANSICION',true) RETURNING ubicacion_id",Short.class,"Transición "+UUID.randomUUID());
        barra=jdbc.queryForObject("INSERT INTO ubicacion(nombre,tipo,activo,salon_id,ubicacion_abastecimiento_id) VALUES (?,'BARRA',true,1,?) RETURNING ubicacion_id",Short.class,"Barra "+UUID.randomUUID(),destino);
    }
    Resultado preparar(String clave) { return preparaciones.registrar(evento,clave,new Datos(barra,destino,List.of(new Renglon(bebida,BigDecimal.valueOf(6)))),usuario); }
    <T> Future<T> ejecutar(Supplier<T> accion, CompletableFuture<Integer> pid, boolean retener) {
        return hilos.submit(() -> transacciones.execute(t -> {
            jdbc.execute("SET LOCAL statement_timeout='15s'");
            pid.complete(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
            T r=accion.get();
            if(retener) { aplicada.countDown(); esperar(continuar); }
            return r;
        }));
    }
    void esperar(CountDownLatch latch) {
        try { assertThat(latch.await(15,TimeUnit.SECONDS)).isTrue(); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    void bloqueado(int a,int b) {
        long fin=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<fin) {
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))",Boolean.class,a,b))) return;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        fail("La segunda transacción no esperó a la primera");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void preparacionesSimultaneas(boolean mismaClave) throws Exception {
        var pidA=new CompletableFuture<Integer>(); var pidB=new CompletableFuture<Integer>();
        var a=ejecutar(() -> preparar("uno"),pidA,true); esperar(aplicada);
        var b=ejecutar(() -> preparar(mismaClave?"uno":"dos"),pidB,false);
        bloqueado(pidA.get(10,TimeUnit.SECONDS),pidB.get(10,TimeUnit.SECONDS)); continuar.countDown();
        assertThat(a.get(20,TimeUnit.SECONDS).repetida()).isFalse();
        assertThat(b.get(20,TimeUnit.SECONDS).repetida()).isEqualTo(mismaClave);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preparacion_evento WHERE evento_id=?",Integer.class,evento)).isEqualTo(mismaClave?1:2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE evento_id=?",Integer.class,evento)).isEqualTo(mismaClave?1:2);
        assertThat(jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id=? AND bebida_id=?",BigDecimal.class,destino,bebida)).isEqualByComparingTo(mismaClave?"6":"12");
        assertThat(jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id=1 AND bebida_id=?",BigDecimal.class,bebida)).isEqualByComparingTo(mismaClave?"-6":"-12");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE usuario_origen_id=? AND tipo='ALERTA_STOCK'",Integer.class,usuario)).isEqualTo(mismaClave?1:2);
    }
    @Test void cambioDeEstadoDuranteLaEsperaSeVuelveAValidar() throws Exception {
        var pidA=new CompletableFuture<Integer>(); var pidB=new CompletableFuture<Integer>();
        var a=ejecutar(() -> jdbc.update("UPDATE evento SET estado='REALIZADO' WHERE evento_id=?",evento),pidA,true); esperar(aplicada);
        var b=ejecutar(() -> preparar("uno"),pidB,false);
        bloqueado(pidA.get(10,TimeUnit.SECONDS),pidB.get(10,TimeUnit.SECONDS)); continuar.countDown(); a.get(20,TimeUnit.SECONDS);
        assertThatThrownBy(() -> b.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(ProblemaException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preparacion_evento WHERE evento_id=?",Integer.class,evento)).isZero();
    }
    @Test void cambioDeCircuitoDuranteLaEsperaSeVuelveAValidar() throws Exception {
        var pidA=new CompletableFuture<Integer>(); var pidB=new CompletableFuture<Integer>();
        var a=ejecutar(() -> jdbc.update("UPDATE ubicacion SET ubicacion_abastecimiento_id=1 WHERE ubicacion_id=?",barra),pidA,true); esperar(aplicada);
        var b=ejecutar(() -> preparar("uno"),pidB,false);
        bloqueado(pidA.get(10,TimeUnit.SECONDS),pidB.get(10,TimeUnit.SECONDS)); continuar.countDown(); a.get(20,TimeUnit.SECONDS);
        assertThatThrownBy(() -> b.get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(ProblemaException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE evento_id=?",Integer.class,evento)).isZero();
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void recuentoYPreparacionCompartenElBloqueoSinFilaPrevia(boolean primeroPreparacion) throws Exception {
        Supplier<Object> prep=() -> preparar("uno");
        Supplier<Object> recuento=() -> ajustes.registrarRecuento(new AjusteService.DatosAjuste((short)1,bebida,BigDecimal.TEN,(short)12,null),usuario);
        var pidA=new CompletableFuture<Integer>(); var pidB=new CompletableFuture<Integer>();
        var a=ejecutar(primeroPreparacion?prep:recuento,pidA,true); esperar(aplicada);
        var b=ejecutar(primeroPreparacion?recuento:prep,pidB,false);
        bloqueado(pidA.get(10,TimeUnit.SECONDS),pidB.get(10,TimeUnit.SECONDS)); continuar.countDown();
        a.get(20,TimeUnit.SECONDS); b.get(20,TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id=1 AND bebida_id=?",BigDecimal.class,bebida)).isEqualByComparingTo(primeroPreparacion?"10":"4");
        assertThat(jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id=? AND bebida_id=?",BigDecimal.class,destino,bebida)).isEqualByComparingTo("6");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void retiroYPreparacionConservanLosSaldos(boolean primeroPreparacion) throws Exception {
        jdbc.update("UPDATE evento SET estado='EN_CURSO' WHERE evento_id=?",evento);
        Supplier<Object> prep=() -> preparar("uno");
        Supplier<Object> retiro=() -> entregas.retirar(evento,barra,new EntregaService.DatosRetiro(false,
                List.of(new EntregaService.Renglon(bebida,BigDecimal.valueOf(4)))),
                new ar.edu.utn.vastio.comun.seguridad.UsuarioActual(usuario,java.util.Set.of("COMPRAS")));
        var pidA=new CompletableFuture<Integer>(); var pidB=new CompletableFuture<Integer>();
        var a=ejecutar(primeroPreparacion?prep:retiro,pidA,true); esperar(aplicada);
        var b=ejecutar(primeroPreparacion?retiro:prep,pidB,false);
        bloqueado(pidA.get(10,TimeUnit.SECONDS),pidB.get(10,TimeUnit.SECONDS)); continuar.countDown();
        a.get(20,TimeUnit.SECONDS); b.get(20,TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id=? AND bebida_id=?",BigDecimal.class,destino,bebida)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id=? AND bebida_id=?",BigDecimal.class,barra,bebida)).isEqualByComparingTo("4");
    }
    @Test void rollbackIncluyeCabeceraSaldosMovimientosYAlertas() {
        assertThatThrownBy(() -> transacciones.executeWithoutResult(t -> { preparar("rollback"); throw new IllegalStateException("fallo posterior"); }))
                .hasMessage("fallo posterior");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM preparacion_evento WHERE evento_id=?",Integer.class,evento)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimiento_stock WHERE evento_id=?",Integer.class,evento)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_ubicacion WHERE bebida_id=?",Integer.class,bebida)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE usuario_origen_id=?",Integer.class,usuario)).isZero();
        assertThat(preparar("rollback").repetida()).isFalse();
    }
    @AfterEach void limpiar() throws Exception {
        continuar.countDown(); hilos.shutdown(); assertThat(hilos.awaitTermination(25,TimeUnit.SECONDS)).isTrue();
        transacciones.executeWithoutResult(t -> {
            jdbc.execute("SET LOCAL session_replication_role=replica");
            jdbc.update("DELETE FROM notificacion_destinatario WHERE notificacion_id IN (SELECT notificacion_id FROM notificacion WHERE usuario_origen_id=?)",usuario);
            jdbc.update("DELETE FROM notificacion WHERE usuario_origen_id=?",usuario);
            jdbc.update("DELETE FROM movimiento_stock WHERE evento_id=?",evento);
            jdbc.update("DELETE FROM movimiento_stock WHERE bebida_id=?",bebida);
            jdbc.update("DELETE FROM entrega WHERE evento_id=?",evento);
            jdbc.update("DELETE FROM preparacion_evento WHERE evento_id=?",evento);
            jdbc.update("DELETE FROM stock_ubicacion WHERE bebida_id=?",bebida);
            jdbc.update("DELETE FROM bebida WHERE bebida_id=?",bebida);
            jdbc.update("DELETE FROM ubicacion WHERE ubicacion_id IN (?,?)",barra,destino);
            jdbc.update("WITH borrado AS (DELETE FROM evento WHERE evento_id=? RETURNING cliente_id,unidad_id), clientes AS (DELETE FROM cliente WHERE cliente_id IN (SELECT cliente_id FROM borrado)) DELETE FROM unidad_comercializable WHERE unidad_id IN (SELECT unidad_id FROM borrado)",evento);
        });
    }
}
