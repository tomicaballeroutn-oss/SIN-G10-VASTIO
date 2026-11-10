package ar.edu.utn.vastio.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ar.edu.utn.vastio.agenda.aplicacion.ClienteService.DatosCliente;

class DatosDeClienteTest {

    @Test
    void unaPersonaTieneDniTelefonoDeCordobaYGmail() {
        DatosCliente datos = DatosDeDemostracion.datosDeCliente("Martina Ortiz");
        assertThat(datos.nombre()).isEqualTo("Martina Ortiz");
        assertThat(datos.documento()).matches("\\d{8}");
        assertThat(datos.telefono()).matches("351 \\d{3}-\\d{4}");
        assertThat(datos.email()).matches("martina\\.ortiz\\d{2}@gmail\\.com");
    }

    @Test
    void unaInstitucionTieneCuitYCorreoDeEventosSinTildes() {
        DatosCliente datos = DatosDeDemostracion.datosDeCliente("Colegio Nacional de Monserrat");
        assertThat(datos.documento()).matches("30-\\d{8}-\\d").hasSizeLessThanOrEqualTo(13);
        assertThat(datos.email()).isEqualTo("eventos@nacionaldemonserrat.com.ar");
        assertThat(DatosDeDemostracion.datosDeCliente("Fundación Hogar de Día").email()).isEqualTo("eventos@fundacionhogardedia.com.ar");
    }

    @Test
    void siempreSonLosMismos() {
        assertThat(DatosDeDemostracion.datosDeCliente("Sofía Méndez")).isEqualTo(DatosDeDemostracion.datosDeCliente("Sofía Méndez"));
        assertThat(DatosDeDemostracion.datosDeCliente("Sofía Méndez").email()).startsWith("sofia.mendez");
    }
}
