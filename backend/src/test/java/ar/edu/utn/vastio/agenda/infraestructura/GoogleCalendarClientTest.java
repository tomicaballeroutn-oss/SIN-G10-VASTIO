package ar.edu.utn.vastio.agenda.infraestructura;

import static org.assertj.core.api.Assertions.*;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import ar.edu.utn.vastio.agenda.aplicacion.ContenidoCalendar;
import ar.edu.utn.vastio.agenda.aplicacion.ClienteCalendar.Fallo;

class GoogleCalendarClientTest {
    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void credencialesFallidasSeRegistranUnaSolaVezSinRuta(org.springframework.boot.test.system.CapturedOutput salida) {
        String ruta = "archivo-privado-inexistente-m06.json";
        var cliente = new GoogleCalendarClient(new CalendarProperties(true, "prueba", "prueba", ruta, 5000, 10000));
        assertThatThrownBy(() -> cliente.comprobarDestino("prueba")).hasMessage("CREDENCIALES_NO_DISPONIBLES");
        assertThat(salida.getAll().lines().filter(l -> l.contains("fase=CREDENCIALES")).count()).isEqualTo(1);
        assertThat(salida.getAll()).doesNotContain(ruta, "carga de credenciales fallida");
    }

    @Test void roundtripYModificacionManualSeDetectanPorContenidoReal() {
        var inicio=ZonedDateTime.parse("2032-12-31T20:00:00-03:00[America/Argentina/Cordoba]");
        var fin=inicio.plusHours(10);
        String hash=ContenidoCalendar.hash(String.join("\u0000","EV · Avril · Noche","Tipo: Casamiento","Avril",inicio.toString(),fin.toString()));
        var c=new ContenidoCalendar.Contenido("EV · Avril · Noche","Tipo: Casamiento","Avril",inicio,fin,hash);
        var google=GoogleCalendarClient.evento("abc123",c).setEtag("etag");
        assertThat(GoogleCalendarClient.remoto(google,"abc123").huella()).isEqualTo(hash);
        assertThat(google.getAttendees()).isNull();
        assertThat(google.getAttachments()).isNull();
        google.setSummary("Edición manual");
        assertThat(GoogleCalendarClient.remoto(google,"abc123").huella()).isNotEqualTo(hash);
        assertThatThrownBy(()->GoogleCalendarClient.remoto(google,"otra-identidad")).isInstanceOf(Fallo.class);
    }
    @Test void desactivadoNoCargaArchivoNiConecta() {
        var cliente=new GoogleCalendarClient(new CalendarProperties(false,"","","",5000,10000));
        assertThat(cliente.configurado()).isFalse();
        assertThatThrownBy(()->cliente.comprobarDestino("calendario")).hasMessage("CALENDAR_NO_CONFIGURADO");
    }
    @Test void configuracionActivaExigeDestinoExplicito() {
        assertThatThrownBy(()->new CalendarProperties(true,"primary","prueba","archivo",5000,10000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
