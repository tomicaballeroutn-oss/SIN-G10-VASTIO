package ar.edu.utn.vastio.agenda.aplicacion;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import ar.edu.utn.vastio.*;
import ar.edu.utn.vastio.agenda.aplicacion.ClienteService.DatosCliente;
import ar.edu.utn.vastio.agenda.aplicacion.PreReservaService.PreReserva;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.dominio.*;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

/** Barreras de commit y espera comprobada por PostgreSQL, no por casualidad del planificador. */
@PruebaDeIntegracion
@Timeout(60)
class BloqueoProtocoloIT {
    static final AtomicInteger CASO = new AtomicInteger();
    @Autowired BloqueoService bloqueos;
    @Autowired PreReservaService reservas;
    @Autowired ReprogramacionService reprogramaciones;
    @Autowired Personas personas;
    @Autowired UsuarioRepository usuarios;
    @Autowired Escenario escenario;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    ExecutorService hilos;
    CountDownLatch lista, liberar;
    LocalDate fecha;
    Usuario admin, vendedora;
    UsuarioActual quien, titular;
    long evento, bloqueo;
    String cliente;

    @BeforeEach void datos() {
        fecha = LocalDate.of(2080, 1, 1).plusDays(CASO.incrementAndGet() * 10L);
        String sufijo = UUID.randomUUID().toString().substring(0, 12);
        admin = personas.usuario("protocolo.a." + sufijo, "Administración protocolo", RolCodigo.ADMINISTRACION);
        vendedora = personas.usuario("protocolo.v." + sufijo, "Vendedora protocolo", RolCodigo.VENDEDORA);
        admin.setDebeCambiarContrasena(false);
        vendedora.setDebeCambiarContrasena(false);
        usuarios.save(admin); usuarios.save(vendedora);
        quien = new UsuarioActual(admin.getId(), Set.of("ADMINISTRACION"));
        titular = new UsuarioActual(vendedora.getId(), Set.of("VENDEDORA"));
        cliente = "Auditoría protocolo " + sufijo;
        hilos = Executors.newFixedThreadPool(2);
        lista = new CountDownLatch(1); liberar = new CountDownLatch(1);
    }

    String operar(String tipo) {
        switch (tipo) {
            case "BLOQUEO" -> bloqueos.registrar(new BloqueoService.Pedido(Set.of((short) 1), fecha, null, Set.of((short) 2), (short) 7, null), quien);
            case "PRERESERVA" -> reservas.registrar(new PreReserva((short) 1, fecha, (short) 2, (short) 2, null, null,
                    new DatosCliente(null, cliente, null, null, null)), titular);
            case "REPROGRAMACION" -> reprogramaciones.reprogramar(evento,
                    new ReprogramacionService.Pedido((short) 1, fecha, (short) 2, null, null), titular);
            case "DESBLOQUEO" -> bloqueos.desbloquear(bloqueo, quien);
            default -> throw new IllegalArgumentException(tipo);
        }
        return "OK";
    }

    Future<String> ejecutar(Callable<String> accion, CompletableFuture<Integer> pid, boolean retener, boolean rollback) {
        return hilos.submit(() -> {
            try {
                return tx.execute(t -> {
                    jdbc.execute("SET LOCAL statement_timeout='15s'");
                    pid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    String r;
                    try { r = accion.call(); }
                    catch (RuntimeException e) { throw e; }
                    catch (Exception e) { throw new IllegalStateException(e); }
                    em.flush();
                    if (retener) {
                        lista.countDown();
                        esperar(liberar);
                    }
                    if (rollback) throw new IllegalStateException("ROLLBACK_AUDITORIA");
                    return r;
                });
            } catch (ProblemaException e) {
                return e.getCodigo();
            } catch (IllegalStateException e) {
                if ("ROLLBACK_AUDITORIA".equals(e.getMessage())) return "ROLLBACK";
                throw e;
            }
        });
    }

    void esperar(CountDownLatch latch) {
        try { assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    void comprobarEspera(CompletableFuture<Integer> a, CompletableFuture<Integer> b) throws Exception {
        int primero = a.get(10, TimeUnit.SECONDS), segundo = b.get(10, TimeUnit.SECONDS);
        long fin = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < fin) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, primero, segundo))) return;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        fail("PostgreSQL no registró la espera de la segunda transacción");
    }

    void integridad() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM unidad_comercializable u
                WHERE EXISTS (SELECT 1 FROM evento e WHERE e.unidad_id=u.unidad_id AND e.estado NOT IN ('LIBERADA','CANCELADO'))
                  AND EXISTS (SELECT 1 FROM bloqueo_unidad bu JOIN bloqueo b USING(bloqueo_id) WHERE bu.unidad_id=u.unidad_id AND b.activo)
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT bu.unidad_id FROM bloqueo_unidad bu JOIN bloqueo b USING(bloqueo_id)
                WHERE b.activo GROUP BY bu.unidad_id HAVING count(*)>1) repetidas
                """, Integer.class)).isZero();
    }

    @ParameterizedTest
    @CsvSource({
        "BLOQUEO,BLOQUEO,false", "BLOQUEO,BLOQUEO,true",
        "BLOQUEO,PRERESERVA,false", "BLOQUEO,PRERESERVA,true",
        "PRERESERVA,BLOQUEO,false", "PRERESERVA,BLOQUEO,true",
        "BLOQUEO,REPROGRAMACION,false", "BLOQUEO,REPROGRAMACION,true",
        "REPROGRAMACION,BLOQUEO,false", "REPROGRAMACION,BLOQUEO,true"
    })
    void ambosOrdenesRespetanElLockConYSinUnidadPrevia(String primero, String segundo, boolean preexistente) throws Exception {
        if (preexistente) escenario.unidad("avril", fecha, "noche");
        if (primero.equals("REPROGRAMACION") || segundo.equals("REPROGRAMACION"))
            evento = escenario.evento("santa-barbara", fecha.minusDays(1), "noche", "PRE_RESERVA", vendedora, cliente);
        var pidA = new CompletableFuture<Integer>(); var pidB = new CompletableFuture<Integer>();
        var a = ejecutar(() -> operar(primero), pidA, true, false); esperar(lista);
        var b = ejecutar(() -> operar(segundo), pidB, false, false);
        comprobarEspera(pidA, pidB);
        liberar.countDown();
        assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo("OK");
        String esperado = primero.equals("BLOQUEO")
                ? segundo.equals("BLOQUEO") ? "YA_BLOQUEADA" : "UNIDAD_BLOQUEADA"
                : "UNIDAD_OCUPADA";
        assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo(esperado);
        integridad();
    }

    @Test void rangosParcialesConSalonesYTurnosPedidosEnOrdenInversoNoSeTraban() throws Exception {
        var pidA = new CompletableFuture<Integer>(); var pidB = new CompletableFuture<Integer>();
        var a = ejecutar(() -> {
            bloqueos.registrar(new BloqueoService.Pedido(new LinkedHashSet<>(List.of((short) 3, (short) 2)), fecha,
                    fecha.plusDays(2), new LinkedHashSet<>(List.of((short) 2, (short) 1)), (short) 7, null), quien);
            return "OK";
        }, pidA, true, false); esperar(lista);
        var b = ejecutar(() -> {
            bloqueos.registrar(new BloqueoService.Pedido(new LinkedHashSet<>(List.of((short) 2, (short) 1)), fecha.plusDays(1),
                    fecha.plusDays(3), new LinkedHashSet<>(List.of((short) 1, (short) 2)), (short) 8, null), quien);
            return "OK";
        }, pidB, false, false);
        comprobarEspera(pidA, pidB); liberar.countDown();
        assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo("OK");
        assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo("YA_BLOQUEADA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM unidad_comercializable WHERE salon_id=1 AND fecha BETWEEN ? AND ?", Integer.class, fecha, fecha.plusDays(3))).isZero();
        integridad();
    }

    @Test void dosDesbloqueosEsperanYConservanResponsableEHistoria() throws Exception {
        bloqueo = bloqueos.registrar(new BloqueoService.Pedido(Set.of((short) 1, (short) 2), fecha, fecha.plusDays(2), Set.of((short) 1, (short) 2), (short) 7, null), quien).id();
        var pidA = new CompletableFuture<Integer>(); var pidB = new CompletableFuture<Integer>();
        var a = ejecutar(() -> operar("DESBLOQUEO"), pidA, true, false); esperar(lista);
        var b = ejecutar(() -> operar("DESBLOQUEO"), pidB, false, false);
        comprobarEspera(pidA, pidB); liberar.countDown();
        assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo("OK");
        assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo("BLOQUEO_YA_DESBLOQUEADO");
        var fila = jdbc.queryForMap("SELECT activo,usuario_desbloqueo_id,fecha_desbloqueo FROM bloqueo WHERE bloqueo_id=?", bloqueo);
        assertThat(fila).containsEntry("activo", false).containsEntry("usuario_desbloqueo_id", admin.getId());
        assertThat(fila.get("fecha_desbloqueo")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo_unidad WHERE bloqueo_id=?", Integer.class, bloqueo)).isEqualTo(12);
        integridad();
    }

    @ParameterizedTest @ValueSource(strings={"BLOQUEO", "PRERESERVA"})
    void unDesbloqueoSinConfirmarNoSeAdelantaYTrasElCommitSePuedeOcupar(String tipo) throws Exception {
        bloqueo = bloqueos.registrar(new BloqueoService.Pedido(Set.of((short) 1), fecha, null, Set.of((short) 2), (short) 7, null), quien).id();
        var a = ejecutar(() -> operar("DESBLOQUEO"), new CompletableFuture<>(), true, false); esperar(lista);
        var b = ejecutar(() -> operar(tipo), new CompletableFuture<>(), false, false);
        assertThat(b.get(10, TimeUnit.SECONDS)).isEqualTo(tipo.equals("BLOQUEO") ? "YA_BLOQUEADA" : "UNIDAD_BLOQUEADA");
        liberar.countDown(); assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo("OK");
        assertThat(operar(tipo)).isEqualTo("OK");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo_unidad WHERE bloqueo_id=?", Integer.class, bloqueo)).isEqualTo(1);
        integridad();
    }

    @Test void rollbackDelGanadorLiberaElLockYPermiteLaPreReservaEnEspera() throws Exception {
        var pidA = new CompletableFuture<Integer>(); var pidB = new CompletableFuture<Integer>();
        var a = ejecutar(() -> operar("BLOQUEO"), pidA, true, true); esperar(lista);
        var b = ejecutar(() -> operar("PRERESERVA"), pidB, false, false);
        comprobarEspera(pidA, pidB); liberar.countDown();
        assertThat(a.get(20, TimeUnit.SECONDS)).isEqualTo("ROLLBACK");
        assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo("OK");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo WHERE usuario_id=?", Integer.class, admin.getId())).isZero();
        integridad();
    }

    @AfterEach void limpiar() throws Exception {
        liberar.countDown(); hilos.shutdown();
        assertThat(hilos.awaitTermination(25, TimeUnit.SECONDS)).isTrue();
        tx.executeWithoutResult(t -> {
            jdbc.execute("SET LOCAL session_replication_role=replica");
            String eventos = "SELECT evento_id FROM evento WHERE vendedora_id=" + vendedora.getId();
            jdbc.update("DELETE FROM notificacion_destinatario WHERE notificacion_id IN (SELECT notificacion_id FROM notificacion WHERE evento_id IN (" + eventos + "))");
            jdbc.update("DELETE FROM notificacion WHERE evento_id IN (" + eventos + ")");
            jdbc.update("DELETE FROM cambio_estado_evento WHERE evento_id IN (" + eventos + ")");
            jdbc.update("DELETE FROM reprogramacion WHERE evento_id IN (" + eventos + ")");
            jdbc.update("DELETE FROM modificacion_evento WHERE evento_id IN (" + eventos + ")");
            jdbc.update("DELETE FROM evento WHERE vendedora_id=?", vendedora.getId());
            jdbc.update("DELETE FROM bloqueo_unidad WHERE bloqueo_id IN (SELECT bloqueo_id FROM bloqueo WHERE usuario_id=?)", admin.getId());
            jdbc.update("DELETE FROM bloqueo WHERE usuario_id=?", admin.getId());
            jdbc.update("DELETE FROM unidad_comercializable WHERE fecha BETWEEN ? AND ?", fecha.minusDays(1), fecha.plusDays(3));
            jdbc.update("DELETE FROM cliente WHERE nombre=? OR nombre=?", cliente, "Cliente de " + cliente);
        });
    }
}
