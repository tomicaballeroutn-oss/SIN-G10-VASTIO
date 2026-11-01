package ar.edu.utn.vastio.agenda.aplicacion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import ar.edu.utn.vastio.agenda.dominio.*;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.*;

class ContenidoCalendarTest {
    private final CatalogoService catalogos = mock(CatalogoService.class);
    private final Turno turno = mock(Turno.class);
    private final Salon salon = mock(Salon.class);
    private final TipoEvento tipo = mock(TipoEvento.class);
    private final ContenidoCalendar proyeccion = new ContenidoCalendar(catalogos);

    private Evento evento() {
        when(catalogos.turno((short)2)).thenReturn(turno);
        when(catalogos.salon((short)1)).thenReturn(salon);
        when(catalogos.tipoEvento((short)1)).thenReturn(tipo);
        when(turno.getNombre()).thenReturn("Noche");
        when(turno.getHoraInicio()).thenReturn(LocalTime.of(20,0));
        when(turno.getHoraFin()).thenReturn(LocalTime.of(6,0));
        when(turno.isCruzaMedianoche()).thenReturn(true);
        when(salon.getNombre()).thenReturn("Avril");
        when(tipo.getNombre()).thenReturn("Casamiento");
        Evento e = new Evento("EV-2026-00123", new UnidadComercializable((short)1, LocalDate.of(2032,12,31),(short)2),
                mock(Cliente.class),(short)1,99,"Nombre privado");
        e.actualizarDatos("Nombre privado",(short)1,LocalTime.of(22,30),"Observaciones privadas");
        return e;
    }
    @Test void turnoCompletoCruzaElAnioYLaHoraParticularSoloDescribe() {
        var c = proyeccion.de(evento());
        assertThat(c.titulo()).isEqualTo("EV-2026-00123 · Avril · Noche");
        assertThat(c.inicio().toLocalTime()).isEqualTo(LocalTime.of(20,0));
        assertThat(c.fin().toLocalDate()).isEqualTo(LocalDate.of(2033,1,1));
        assertThat(c.fin().toLocalTime()).isEqualTo(LocalTime.of(6,0));
        assertThat(c.inicio().getZone().getId()).isEqualTo("America/Argentina/Cordoba");
        assertThat(c.descripcion()).contains("22:30", "Casamiento", "Avril", "Noche", "EV-2026-00123")
                .doesNotContain("Nombre privado", "Observaciones privadas", "99");
    }
    @Test void sinHoraParticularNoInventaUnaDuracion() {
        var e=evento(); e.actualizarDatos("Otro nombre",(short)1,null,"privado");
        assertThat(proyeccion.de(e).descripcion()).doesNotContain("Hora particular");
    }
    @Test void cambiosDeCatalogoCambianLaHuellaSinVersionDeEvento() {
        var e=evento(); var antes=proyeccion.de(e);
        when(turno.getHoraFin()).thenReturn(LocalTime.of(5,0));
        assertThat(proyeccion.de(e).huella()).isNotEqualTo(antes.huella());
        when(salon.getNombre()).thenReturn("Avril nuevo");
        assertThat(proyeccion.de(e).titulo()).contains("Avril nuevo");
    }
    @Test void datosNoPublicadosNoCambianLaHuella() {
        var e=evento(); var antes=proyeccion.de(e);
        e.actualizarDatos("Otro dato privado",(short)1,e.getHoraInicio(),"secreto distinto");
        assertThat(proyeccion.de(e).huella()).isEqualTo(antes.huella());
    }
    @Test void turnoDiurnoYFinInvalido() {
        var e=evento(); when(turno.isCruzaMedianoche()).thenReturn(false);
        when(turno.getHoraInicio()).thenReturn(LocalTime.NOON);
        when(turno.getHoraFin()).thenReturn(LocalTime.of(18,0));
        assertThat(proyeccion.de(e).fin().toLocalDate()).isEqualTo(e.getUnidad().getFecha());
        when(turno.getHoraFin()).thenReturn(LocalTime.NOON);
        assertThatThrownBy(()->proyeccion.de(e)).isInstanceOf(IllegalStateException.class);
    }
    @Test void identidadEstableCompatibleYSeparadaPorEntorno() {
        assertThat(ContenidoCalendar.identidad("prueba",123)).matches("[0-9a-f]{64}")
                .isEqualTo(ContenidoCalendar.identidad("prueba",123))
                .isNotEqualTo(ContenidoCalendar.identidad("produccion",123))
                .isNotEqualTo(ContenidoCalendar.identidad("prueba",124));
    }
}
