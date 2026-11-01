package ar.edu.utn.vastio.agenda.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.agenda.aplicacion.BloqueoService.Pedido;
import ar.edu.utn.vastio.agenda.aplicacion.ClienteService.DatosCliente;
import ar.edu.utn.vastio.agenda.aplicacion.PreReservaService.PreReserva;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * A1-02: un bloqueo y una pre-reserva (o dos bloqueos) que se pisan al mismo tiempo no pueden ganar los dos.
 * Sin {@code @Transactional}: cada pedido confirma su propia transacción, como en producción.
 */
@PruebaDeIntegracion
class BloqueoConcurrenciaIT {

    private static final LocalDate FECHA = LocalDate.of(2034, 6, 10);

    @Autowired
    BloqueoService bloqueos;

    @Autowired
    PreReservaService preReservas;

    @Autowired
    Personas personas;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transacciones;

    @RepeatedTest(5)
    void unBloqueoYUnaPreReservaSobreLaMismaUnidadEntraUnoSolo() throws Exception {
        Usuario gabriela = personas.usuario("gabriela.carrera", "Gabriela Soria", RolCodigo.ADMINISTRACION);
        Usuario lucia = personas.usuario("lucia.carrera.bloqueo", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        Callable<String> bloquear = () -> {
            bloqueos.registrar(new Pedido(Set.of((short) 1), FECHA, null, Set.of((short) 2), (short) 7, null),
                    new UsuarioActual(gabriela.getId(), Set.of("ADMINISTRACION")));
            return "bloqueo";
        };
        Callable<String> preReservar = () -> {
            preReservas.registrar(new PreReserva((short) 1, FECHA, (short) 2, (short) 2, null, null,
                    new DatosCliente(null, "Cliente de carrera de bloqueo", null, null, null)),
                    new UsuarioActual(lucia.getId(), Set.of("VENDEDORA")));
            return "pre-reserva";
        };

        List<String> ganadores = correrALaVez(List.of(bloquear, preReservar), "UNIDAD_OCUPADA", "UNIDAD_BLOQUEADA", "FECHA_TOMADA");

        assertThat(ganadores).hasSize(1);
        // Nunca las dos cosas sobre la misma unidad.
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM unidad_comercializable u
                WHERE u.fecha = ? AND u.salon_id = 1 AND u.turno_id = 2
                  AND EXISTS (SELECT 1 FROM evento e WHERE e.unidad_id = u.unidad_id AND e.estado NOT IN ('LIBERADA', 'CANCELADO'))
                  AND EXISTS (SELECT 1 FROM bloqueo_unidad bu JOIN bloqueo b USING (bloqueo_id) WHERE bu.unidad_id = u.unidad_id AND b.activo)""",
                Integer.class, FECHA)).isZero();
    }

    /**
     * Dos bloqueos que comparten el Club de Campo (todas sus fechas y turnos), con los salones y turnos pedidos en
     * distinto orden. Las unidades se toman siempre en el mismo orden, así que no se traban: uno entra y el otro recibe 409.
     */
    @RepeatedTest(5)
    void dosBloqueosQueSePisanEntraUnoSoloYNingunoSeTraba() throws Exception {
        Usuario gabriela = personas.usuario("gabriela.carrera2", "Gabriela Soria", RolCodigo.ADMINISTRACION);
        UsuarioActual quien = new UsuarioActual(gabriela.getId(), Set.of("ADMINISTRACION"));
        Callable<String> primero = () -> {
            bloqueos.registrar(new Pedido(Set.of((short) 1, (short) 2), FECHA, FECHA.plusDays(2), Set.of((short) 1, (short) 2), (short) 7, null), quien);
            return "A";
        };
        Callable<String> segundo = () -> {
            bloqueos.registrar(new Pedido(Set.of((short) 3, (short) 2), FECHA, FECHA.plusDays(2), Set.of((short) 2, (short) 1), (short) 8, null), quien);
            return "B";
        };

        List<String> ganadores = correrALaVez(List.of(primero, segundo), "YA_BLOQUEADA");

        assertThat(ganadores).hasSize(1);
        // Cada unidad quedó bloqueada una sola vez.
        assertThat(jdbc.queryForObject("""
                SELECT max(veces) FROM (
                    SELECT count(*) AS veces FROM bloqueo_unidad bu JOIN bloqueo b USING (bloqueo_id)
                    JOIN unidad_comercializable u USING (unidad_id)
                    WHERE b.activo AND u.fecha BETWEEN ? AND ? GROUP BY bu.unidad_id) por_unidad""",
                Integer.class, FECHA, FECHA.plusDays(2))).isEqualTo(1);
    }

    /** Corre los pedidos a la vez; devuelve los que ganaron y verifica que los otros fallaron con uno de esos códigos. */
    private static List<String> correrALaVez(List<Callable<String>> pedidos, String... codigosEsperados) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(pedidos.size());
        List<Future<String>> resultados = new ArrayList<>();
        for (Callable<String> pedido : pedidos) {
            resultados.add(hilos.submit(() -> {
                largada.await();
                return pedido.call();
            }));
        }
        largada.countDown();

        List<String> ganadores = new ArrayList<>();
        for (Future<String> resultado : resultados) {
            try {
                ganadores.add(resultado.get(30, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                // Lo normal es el chequeo del servicio con el FOR UPDATE; cualquier otra cosa (deadlock incluido) falla el test.
                if (!(e.getCause() instanceof ProblemaException p) || !List.of(codigosEsperados).contains(p.getCodigo())) {
                    throw e;
                }
            }
        }
        hilos.shutdown();
        return ganadores;
    }

    /** Lo confirmado queda en la base compartida con los otros tests: se borra salteando los triggers de solo inserción. */
    @AfterEach
    void limpiar() {
        transacciones.executeWithoutResult(t -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            jdbc.update("""
                    DELETE FROM notificacion_destinatario WHERE notificacion_id IN (SELECT notificacion_id FROM notificacion
                    WHERE evento_id IN (SELECT evento_id FROM evento JOIN unidad_comercializable USING (unidad_id) WHERE fecha BETWEEN ? AND ?))""",
                    FECHA, FECHA.plusDays(2));
            jdbc.update("DELETE FROM notificacion WHERE evento_id IN (SELECT evento_id FROM evento JOIN unidad_comercializable USING (unidad_id) WHERE fecha BETWEEN ? AND ?)",
                    FECHA, FECHA.plusDays(2));
            jdbc.update("DELETE FROM cambio_estado_evento WHERE evento_id IN (SELECT evento_id FROM evento JOIN unidad_comercializable USING (unidad_id) WHERE fecha BETWEEN ? AND ?)",
                    FECHA, FECHA.plusDays(2));
            jdbc.update("DELETE FROM evento WHERE unidad_id IN (SELECT unidad_id FROM unidad_comercializable WHERE fecha BETWEEN ? AND ?)",
                    FECHA, FECHA.plusDays(2));
            jdbc.update("DELETE FROM bloqueo_unidad WHERE unidad_id IN (SELECT unidad_id FROM unidad_comercializable WHERE fecha BETWEEN ? AND ?)",
                    FECHA, FECHA.plusDays(2));
            jdbc.update("DELETE FROM bloqueo WHERE usuario_id IN (SELECT usuario_id FROM usuario WHERE nombre_usuario IN ('gabriela.carrera', 'gabriela.carrera2'))");
            jdbc.update("DELETE FROM unidad_comercializable WHERE fecha BETWEEN ? AND ?", FECHA, FECHA.plusDays(2));
            jdbc.update("DELETE FROM cliente WHERE nombre = 'Cliente de carrera de bloqueo'");
        });
    }
}
