package ar.edu.utn.vastio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * En dev, por defecto, los datos de demostración más EscenarioCompleto: historia con consumo (para que las órdenes
 * salgan sugeridas), el evento de hoy en curso con la bebida preparada en la transición de Avril y el de ayer realizado
 * para mostrar el cierre.
 */
@SpringBootTest(properties = {
        "vastio.usuario-inicial.usuario=direccion.test",
        "vastio.usuario-inicial.contrasena=clave-inicial-de-prueba",
        "vastio.demo.contrasena=clave-de-demostracion"
})
@ActiveProfiles({"dev", "test"})
@Import(TestcontainersConfig.class)
class EscenarioCompletoIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void cargaLaHistoriaElEventoDeHoyYElDeAyer() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento WHERE estado = 'CERRADO'", Integer.class)).isGreaterThanOrEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consumo_evento", Integer.class)).isPositive();
        // El de hoy al mediodía en Avril, con la bebida ya preparada (En curso lo pone el sistema a las 12; en los tests está apagado).
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM evento e JOIN unidad_comercializable u USING (unidad_id)
                WHERE u.fecha = current_date AND u.salon_id = 1 AND u.turno_id = 1 AND e.estado IN ('CONFIRMADO', 'EN_CURSO')
                  AND EXISTS (SELECT 1 FROM movimiento_stock m WHERE m.evento_id = e.evento_id AND m.tipo = 'PREPARACION')""", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento WHERE estado = 'REALIZADO'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ubicacion WHERE nombre = 'Transición Avril'", Integer.class)).isEqualTo(1);
    }

    @Test
    void laCampanaArrancaLimpia() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion_destinatario WHERE NOT leida", Integer.class)).isZero();
    }

    @Test
    void losEventosNoTienenTodosLosMismosInvitados() {
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT cantidad_invitados) FROM evento WHERE cantidad_invitados IS NOT NULL",
                Integer.class)).isGreaterThan(5);
    }
}
