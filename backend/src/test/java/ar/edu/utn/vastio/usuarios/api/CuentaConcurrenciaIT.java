package ar.edu.utn.vastio.usuarios.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.usuarios.aplicacion.AutenticacionService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.infraestructura.UsuarioRepository;

@PruebaDeIntegracion
@Timeout(60)
class CuentaConcurrenciaIT {
    @Autowired Personas personas;
    @Autowired AutenticacionService autenticacion;
    @Autowired TransactionTemplate transacciones;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;

    @Test
    void dosCambiosConLaMismaActualNoSeSobrescriben() throws Exception {
        long id = personas.usuario("contrasena." + UUID.randomUUID(), "Cuenta concurrente", RolCodigo.COMPRAS).getId();
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch guardada = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        CompletableFuture<Integer> pidA = new CompletableFuture<>();
        CompletableFuture<Integer> pidB = new CompletableFuture<>();
        try {
            Future<?> primero = hilos.submit(() -> transacciones.executeWithoutResult(t -> {
                jdbc.execute("SET LOCAL statement_timeout='15s'");
                pidA.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                autenticacion.cambiarContrasena(id, Personas.CONTRASENA, "primera-clave-nueva");
                guardada.countDown();
                try {
                    assertThat(liberar.await(15, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            assertThat(guardada.await(15, TimeUnit.SECONDS)).isTrue();
            Future<String> segundo = hilos.submit(() -> {
                try {
                    transacciones.executeWithoutResult(t -> {
                        jdbc.execute("SET LOCAL statement_timeout='15s'");
                        pidB.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        autenticacion.cambiarContrasena(id, Personas.CONTRASENA, "segunda-clave-nueva");
                    });
                    return "CAMBIO_ACEPTADO";
                } catch (ProblemaException e) {
                    return e.getCodigo();
                }
            });
            int a = pidA.get(10, TimeUnit.SECONDS);
            int b = pidB.get(10, TimeUnit.SECONDS);
            long fin = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean bloqueado = false;
            while (System.nanoTime() < fin) {
                if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, a, b))) {
                    bloqueado = true;
                    break;
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
            }
            if (!bloqueado) fail("El segundo cambio debe esperar el commit del primero");
            liberar.countDown();
            primero.get(20, TimeUnit.SECONDS);
            assertThat(segundo.get(20, TimeUnit.SECONDS)).isEqualTo("CONTRASENA_ACTUAL_INCORRECTA");
            String hash = usuarios.findById(id).orElseThrow().getHashContrasena();
            assertThat(encoder.matches("primera-clave-nueva", hash)).isTrue();
            assertThat(encoder.matches("segunda-clave-nueva", hash)).isFalse();
        } finally {
            liberar.countDown();
            hilos.shutdown();
            assertThat(hilos.awaitTermination(25, TimeUnit.SECONDS)).isTrue();
            usuarios.deleteById(id);
        }
    }
}
