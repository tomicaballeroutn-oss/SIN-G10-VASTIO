package ar.edu.utn.vastio.agenda.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.agenda.aplicacion.ClienteService.DatosCliente;
import ar.edu.utn.vastio.agenda.aplicacion.PreReservaService.PreReserva;
import ar.edu.utn.vastio.comun.errores.Mensajes;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Objetivo del sprint: dos vendedoras que aparten la misma fecha al mismo tiempo no se pisan.
 * Sin {@code @Transactional}: cada pedido confirma su propia transacción, como en producción.
 */
@PruebaDeIntegracion
class PreReservaConcurrenciaIT {

    private static final LocalDate FECHA = LocalDate.of(2032, 2, 14);

    @Autowired
    PreReservaService preReservas;

    @Autowired
    Personas personas;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transacciones;

    @RepeatedTest(5)
    void dosPreReservasSimultaneasSobreLaMismaUnidadEntraUnaSola() throws Exception {
        Usuario lucia = personas.usuario("lucia.carrera", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        Usuario sofia = personas.usuario("sofia.carrera", "Sofía Méndez", RolCodigo.VENDEDORA);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Future<Long>> resultados = new ArrayList<>();
        for (Usuario vendedora : List.of(lucia, sofia)) {
            Callable<Long> pedido = () -> {
                largada.await();
                return preReservas.registrar(
                        new PreReserva((short) 1, FECHA, (short) 2, (short) 2, null, null,
                                new DatosCliente(null, "Cliente de " + vendedora.getNombreCompleto(), null, null, null)),
                        new UsuarioActual(vendedora.getId(), Set.of("VENDEDORA"))).getId();
            };
            resultados.add(hilos.submit(pedido));
        }
        largada.countDown();

        int exitos = 0;
        int fechaTomada = 0;
        for (Future<Long> resultado : resultados) {
            try {
                resultado.get(20, TimeUnit.SECONDS);
                exitos++;
            } catch (java.util.concurrent.ExecutionException e) {
                // Lo normal es el chequeo del servicio con el FOR UPDATE; el índice es la red de seguridad.
                if (e.getCause() instanceof ProblemaException p && p.getCodigo().equals(Mensajes.CODIGO_FECHA_TOMADA)
                        || e.getCause() instanceof DataIntegrityViolationException) {
                    fechaTomada++;
                } else {
                    throw e;
                }
            }
        }
        hilos.shutdown();

        assertThat(exitos).isEqualTo(1);
        assertThat(fechaTomada).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM evento e JOIN unidad_comercializable u USING (unidad_id)
                WHERE u.fecha = ? AND u.salon_id = 1 AND u.turno_id = 2 AND e.estado NOT IN ('LIBERADA', 'CANCELADO')""",
                Integer.class, FECHA)).isEqualTo(1);
    }

    /** Lo confirmado queda en la base compartida con los otros tests: se borra salteando los triggers de solo inserción. */
    @AfterEach
    void limpiar() {
        transacciones.executeWithoutResult(t -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            jdbc.update("""
                    DELETE FROM notificacion_destinatario WHERE notificacion_id IN (SELECT notificacion_id FROM notificacion
                    WHERE evento_id IN (SELECT evento_id FROM evento JOIN unidad_comercializable USING (unidad_id) WHERE fecha = ?))""", FECHA);
            jdbc.update("DELETE FROM notificacion WHERE evento_id IN (SELECT evento_id FROM evento JOIN unidad_comercializable USING (unidad_id) WHERE fecha = ?)", FECHA);
            jdbc.update("DELETE FROM cambio_estado_evento WHERE evento_id IN (SELECT evento_id FROM evento JOIN unidad_comercializable USING (unidad_id) WHERE fecha = ?)", FECHA);
            jdbc.update("DELETE FROM evento WHERE unidad_id IN (SELECT unidad_id FROM unidad_comercializable WHERE fecha = ?)", FECHA);
            jdbc.update("DELETE FROM unidad_comercializable WHERE fecha = ?", FECHA);
            jdbc.update("DELETE FROM cliente WHERE nombre IN ('Cliente de Lucía Ferreyra', 'Cliente de Sofía Méndez')");
        });
    }
}
