package ar.edu.utn.vastio.bebida.aplicacion;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ar.edu.utn.vastio.bebida.aplicacion.PreparacionService.*;
import ar.edu.utn.vastio.comun.errores.ProblemaException;

class PreparacionServiceTest {
    @Test void huellaNoDependeDeOrdenNiEscala() {
        assertThat(PreparacionService.huella(new Datos((short) 2, (short) 5, List.of(new Renglon(9, new BigDecimal("12.00")), new Renglon(3, BigDecimal.ONE)))))
                .isEqualTo(PreparacionService.huella(new Datos((short) 2, (short) 5, List.of(new Renglon(3, BigDecimal.ONE), new Renglon(9, new BigDecimal("12"))))));
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "0.5", "99999999.01", "100000000"})
    void rechazaCantidadesInvalidas(String cantidad) {
        assertThatThrownBy(() -> PreparacionService.validarCantidades(new Datos((short) 2, (short) 5,
                List.of(new Renglon(9, new BigDecimal(cantidad)))))).isInstanceOf(ProblemaException.class);
    }
    @Test void destinoFormaParteDeLaHuella() {
        var r = List.of(new Renglon(1, BigDecimal.TEN));
        assertThat(PreparacionService.huella(new Datos((short) 2, (short) 5, r)))
                .isNotEqualTo(PreparacionService.huella(new Datos((short) 2, (short) 6, r)));
    }
    @Test void rechazaDuplicados() {
        var r = new Renglon(1, BigDecimal.TEN);
        assertThatThrownBy(() -> PreparacionService.validarCantidades(new Datos((short) 2, (short) 5, List.of(r, r))))
                .isInstanceOf(ProblemaException.class).hasMessageContaining("dos veces");
    }
    @Test void permiteElLimiteEntero() {
        assertThatCode(() -> PreparacionService.validarCantidades(new Datos((short) 2, (short) 5,
                List.of(new Renglon(1, new BigDecimal("99999999.00")))))).doesNotThrowAnyException();
    }
}
