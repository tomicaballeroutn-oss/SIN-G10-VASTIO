package ar.edu.utn.vastio.bebida.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.bebida.aplicacion.BebidaService.DatosBebida;
import ar.edu.utn.vastio.bebida.aplicacion.IngresoService.DatosIngreso;
import ar.edu.utn.vastio.bebida.aplicacion.IngresoService.Renglon;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;

/**
 * Dos ingresos simultáneos de la misma bebida suman los dos al saldo: el saldo se actualiza en una sola sentencia.
 * Sin {@code @Transactional}: cada ingreso confirma su propia transacción, como en producción.
 */
@PruebaDeIntegracion
class IngresoConcurrenciaIT {

    private static final String NOMBRE = "Bebida de concurrencia";

    @Autowired
    IngresoService ingresos;

    @Autowired
    BebidaService bebidas;

    @Autowired
    Personas personas;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transacciones;

    long bebida;

    @BeforeEach
    void bebida() {
        bebida = bebidas.crear(new DatosBebida(NOMBRE, "750 ml", (short) 3, (short) 1, (short) 6, null, List.of())).getId();
    }

    @RepeatedTest(3)
    void dosIngresosALaVezSumanLosDos() throws Exception {
        long usuario = personas.de(RolCodigo.COMPRAS).getId();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(4);
        List<Future<?>> resultados = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            resultados.add(hilos.submit(() -> {
                largada.await();
                return ingresos.registrar(new DatosIngreso(null, null, List.of(new Renglon(bebida, BigDecimal.valueOf(6)))), usuario);
            }));
        }
        largada.countDown();
        for (Future<?> resultado : resultados) {
            resultado.get(20, TimeUnit.SECONDS);
        }
        hilos.shutdown();

        assertThat(jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id = 1 AND bebida_id = ?",
                BigDecimal.class, bebida)).isEqualByComparingTo("24");
    }

    /** Lo confirmado queda en la base compartida con los otros tests: se borra salteando los triggers de solo inserción. */
    @AfterEach
    void limpiar() {
        transacciones.executeWithoutResult(t -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            jdbc.update("CREATE TEMP TABLE ingresos_de_prueba ON COMMIT DROP AS SELECT DISTINCT ingreso_id FROM movimiento_stock WHERE bebida_id = ?", bebida);
            jdbc.update("DELETE FROM movimiento_stock WHERE bebida_id = ?", bebida);
            jdbc.update("DELETE FROM ingreso WHERE ingreso_id IN (SELECT ingreso_id FROM ingresos_de_prueba)");
            jdbc.update("DELETE FROM stock_ubicacion WHERE bebida_id = ?", bebida);
            jdbc.update("DELETE FROM bebida WHERE bebida_id = ?", bebida);
        });
    }
}
