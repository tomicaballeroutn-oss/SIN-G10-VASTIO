package ar.edu.utn.vastio.agenda.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.agenda.aplicacion.ReprogramacionService.Pedido;
import ar.edu.utn.vastio.comun.errores.Mensajes;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Dos eventos que se reprograman a la misma unidad al mismo tiempo: entra uno solo.
 * Sin {@code @Transactional}: cada pedido confirma su propia transacción, como en producción.
 */
@PruebaDeIntegracion
class ReprogramacionConcurrenciaIT {

    private static final LocalDate ORIGINAL = LocalDate.of(2033, 8, 6);
    private static final LocalDate NUEVA = LocalDate.of(2033, 8, 13);

    @Autowired
    ReprogramacionService reprogramaciones;

    @Autowired
    Personas personas;

    @Autowired
    Escenario escenario;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transacciones;

    @RepeatedTest(3)
    void dosReprogramacionesSimultaneasALaMismaUnidadEntraUnaSola() throws Exception {
        Usuario lucia = personas.usuario("lucia.carrera.reprog", "Lucía Ferreyra", RolCodigo.VENDEDORA);
        Usuario melina = personas.usuario("melina.carrera.reprog", "Melina Sifón", RolCodigo.COORDINACION);
        List<Long> eventos = List.of(
                escenario.evento("avril", ORIGINAL, "noche", "CONTRATADO", lucia, "Carrera A"),
                escenario.evento("club", ORIGINAL, "noche", "SENADO", lucia, "Carrera B"));
        UsuarioActual coordinacion = new UsuarioActual(melina.getId(), Set.of("COORDINACION"));

        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Future<?>> resultados = new ArrayList<>();
        for (long evento : eventos) {
            resultados.add(hilos.submit(() -> {
                largada.await();
                return reprogramaciones.reprogramar(evento, new Pedido((short) 3, NUEVA, (short) 2, (short) 4, null), coordinacion);
            }));
        }
        largada.countDown();

        int exitos = 0;
        int fechaTomada = 0;
        for (Future<?> resultado : resultados) {
            try {
                resultado.get(20, TimeUnit.SECONDS);
                exitos++;
            } catch (ExecutionException e) {
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
                WHERE u.fecha = ? AND u.salon_id = 3 AND u.turno_id = 2 AND e.estado NOT IN ('LIBERADA', 'CANCELADO')""",
                Integer.class, NUEVA)).isEqualTo(1);
    }

    /** Lo confirmado queda en la base compartida con los otros tests: se borra salteando los triggers de solo inserción. */
    @AfterEach
    void limpiar() {
        transacciones.executeWithoutResult(t -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            String eventos = "SELECT evento_id FROM evento WHERE nombre IN ('Carrera A', 'Carrera B')";
            jdbc.update("DELETE FROM notificacion_destinatario WHERE notificacion_id IN (SELECT notificacion_id FROM notificacion WHERE evento_id IN (" + eventos + "))");
            jdbc.update("DELETE FROM notificacion WHERE evento_id IN (" + eventos + ")");
            jdbc.update("DELETE FROM reprogramacion WHERE evento_id IN (" + eventos + ")");
            jdbc.update("DELETE FROM cambio_estado_evento WHERE evento_id IN (" + eventos + ")");
            jdbc.update("DELETE FROM evento WHERE nombre IN ('Carrera A', 'Carrera B')");
            jdbc.update("DELETE FROM unidad_comercializable WHERE fecha IN (?, ?)", ORIGINAL, NUEVA);
            jdbc.update("DELETE FROM cliente WHERE nombre IN ('Cliente de Carrera A', 'Cliente de Carrera B')");
        });
    }
}
