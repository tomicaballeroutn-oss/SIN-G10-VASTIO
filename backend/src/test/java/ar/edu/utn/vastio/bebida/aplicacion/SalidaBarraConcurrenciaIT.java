package ar.edu.utn.vastio.bebida.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

import ar.edu.utn.vastio.Escenario;
import ar.edu.utn.vastio.Personas;
import ar.edu.utn.vastio.PruebaDeIntegracion;
import ar.edu.utn.vastio.bebida.aplicacion.BebidaService.DatosBebida;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.DatosRetiro;
import ar.edu.utn.vastio.bebida.aplicacion.EntregaService.Renglon;
import ar.edu.utn.vastio.comun.seguridad.UsuarioActual;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

/**
 * Dos primeros retiros simultáneos a la misma barra abren una sola entrega: uno queda como SALIDA_BARRA y el otro como
 * RETIRO_ADICIONAL. Sin {@code @Transactional}: cada retiro confirma su propia transacción, como en producción.
 */
@PruebaDeIntegracion
class SalidaBarraConcurrenciaIT {

    private static final LocalDate FECHA = LocalDate.of(2032, 1, 7);
    private static final short BARRA_AVRIL = 2;

    @Autowired
    EntregaService entregas;

    @Autowired
    BebidaService bebidas;

    @Autowired
    Personas personas;

    @Autowired
    Escenario escenario;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transacciones;

    long bebida;
    long evento;

    @BeforeEach
    void escenario() {
        bebida = bebidas.crear(new DatosBebida("Bebida de salida concurrente", "750 ml", (short) 3, (short) 1, (short) 6, null,
                List.of(), null)).getId();
        evento = escenario.evento("avril", FECHA, "noche", "EN_CURSO", personas.de(RolCodigo.VENDEDORA),
                personas.de(RolCodigo.PLANNER), "Evento de concurrencia");
    }

    @RepeatedTest(3)
    void dosPrimerosRetirosALaVezAbrenUnaSolaEntrega() throws Exception {
        Usuario compras = personas.de(RolCodigo.COMPRAS);
        UsuarioActual quien = new UsuarioActual(compras.getId(), Set.of("COMPRAS"));
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        List<Future<?>> resultados = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            resultados.add(hilos.submit(() -> {
                largada.await();
                return entregas.retirar(evento, BARRA_AVRIL, new DatosRetiro(false, List.of(new Renglon(bebida, BigDecimal.valueOf(6)))), quien);
            }));
        }
        largada.countDown();
        for (Future<?> resultado : resultados) {
            resultado.get(20, TimeUnit.SECONDS);
        }
        hilos.shutdown();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM entrega WHERE evento_id = ?", Integer.class, evento)).isOne();
        assertThat(jdbc.queryForList("SELECT tipo FROM movimiento_stock WHERE evento_id = ? ORDER BY movimiento_id", String.class, evento))
                .containsExactly("SALIDA_BARRA", "RETIRO_ADICIONAL");
        assertThat(jdbc.queryForObject("SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id = ? AND bebida_id = ?",
                BigDecimal.class, BARRA_AVRIL, bebida)).isEqualByComparingTo("12");
    }

    /** Lo confirmado queda en la base compartida con los otros tests: se borra salteando los triggers de solo inserción. */
    @AfterEach
    void limpiar() {
        transacciones.executeWithoutResult(t -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            jdbc.update("DELETE FROM movimiento_stock WHERE evento_id = ?", evento);
            jdbc.update("DELETE FROM entrega WHERE evento_id = ?", evento);
            jdbc.update("DELETE FROM stock_ubicacion WHERE bebida_id = ?", bebida);
            jdbc.update("DELETE FROM bebida WHERE bebida_id = ?", bebida);
            jdbc.update("""
                    WITH borrado AS (DELETE FROM evento WHERE evento_id = ? RETURNING cliente_id, unidad_id),
                         clientes AS (DELETE FROM cliente WHERE cliente_id IN (SELECT cliente_id FROM borrado))
                    DELETE FROM unidad_comercializable WHERE unidad_id IN (SELECT unidad_id FROM borrado)""", evento);
        });
    }
}
