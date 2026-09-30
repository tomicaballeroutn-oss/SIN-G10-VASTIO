package ar.edu.utn.vastio.agenda.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;

/**
 * Tabla de transiciones de docs/maquina-de-estados.md.
 */
class MaquinaDeEstadosTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "PRE_RESERVA, SENADO", "PRE_RESERVA, LIBERADA",
            "SENADO, CONTRATADO", "SENADO, CANCELADO",
            "CONTRATADO, CONFIRMADO", "CONTRATADO, CANCELADO",
            "CONFIRMADO, EN_CURSO", "CONFIRMADO, CANCELADO",
            "EN_CURSO, REALIZADO",
            "REALIZADO, CERRADO"
    })
    void transicionesPermitidas(EstadoEvento desde, EstadoEvento hasta) {
        assertThat(MaquinaDeEstados.permite(desde, hasta)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            // La pre-reserva no se cancela: se libera. Y liberar no es cancelar.
            "PRE_RESERVA, CANCELADO", "SENADO, LIBERADA",
            // No se saltean pasos ni se vuelve atrás.
            "PRE_RESERVA, CONTRATADO", "SENADO, PRE_RESERVA", "EN_CURSO, CANCELADO", "REALIZADO, CANCELADO"
    })
    void transicionesProhibidas(EstadoEvento desde, EstadoEvento hasta) {
        assertThat(MaquinaDeEstados.permite(desde, hasta)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoEvento.class, names = {"CERRADO", "LIBERADA", "CANCELADO"})
    void losEstadosFinalesNoSalen(EstadoEvento fin) {
        for (EstadoEvento destino : EstadoEvento.values()) {
            assertThat(MaquinaDeEstados.permite(fin, destino)).as("%s → %s", fin, destino).isFalse();
        }
    }

    @Test
    void soloLiberadaYCanceladoDejanLibreLaUnidad() {
        assertThat(EnumSet.allOf(EstadoEvento.class).stream().filter(e -> !e.esActivo()))
                .containsExactlyInAnyOrder(EstadoEvento.LIBERADA, EstadoEvento.CANCELADO);
    }
}
