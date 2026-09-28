package ar.edu.utn.vastio.configuracion.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import ar.edu.utn.vastio.TestcontainersConfig;
import ar.edu.utn.vastio.configuracion.dominio.Parametro;
import ar.edu.utn.vastio.configuracion.dominio.ParametroClave;
import ar.edu.utn.vastio.configuracion.dominio.Salon;
import ar.edu.utn.vastio.configuracion.dominio.Turno;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfig.class)
class ConfiguracionRepositoryIT {

    @Autowired
    SalonRepository salones;

    @Autowired
    TurnoRepository turnos;

    @Autowired
    ParametroRepository parametros;

    @Test
    void losSalonesSalenEnElOrdenDeLaAgenda() {
        assertThat(salones.findByActivoTrueOrderByIdAsc())
                .extracting(Salon::getCodigo)
                .containsExactly("avril", "club", "santa-barbara");
    }

    @Test
    void elTurnoNocheCruzaMedianoche() {
        Turno noche = turnos.findByCodigo("noche").orElseThrow();

        assertThat(noche.getNombre()).isEqualTo("Noche");
        assertThat(noche.getHoraInicio()).isEqualTo(LocalTime.of(20, 0));
        assertThat(noche.getHoraFin()).isEqualTo(LocalTime.of(6, 0));
        assertThat(noche.isCruzaMedianoche()).isTrue();
        assertThat(turnos.findByCodigo("mediodia").orElseThrow().isCruzaMedianoche()).isFalse();
    }

    @Test
    void todasLasClavesDeParametroExisten() {
        for (ParametroClave clave : ParametroClave.values()) {
            assertThat(parametros.findById(clave.name())).as(clave.name()).isPresent();
        }
        assertThat(parametros.findById("MINUTOS_EXPIRACION_SESION"))
                .map(Parametro::getValor)
                .contains("60");
    }
}
