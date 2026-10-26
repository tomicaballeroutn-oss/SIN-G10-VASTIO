package ar.edu.utn.vastio.reportes.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ar.edu.utn.vastio.agenda.dominio.EstadoEvento;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.Salon;
import ar.edu.utn.vastio.configuracion.dominio.TipoEvento;
import ar.edu.utn.vastio.reportes.infraestructura.OcupacionRepository;
import ar.edu.utn.vastio.reportes.infraestructura.OcupacionRepository.EventoOcupacion;
import ar.edu.utn.vastio.usuarios.aplicacion.UsuarioService;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

class ReporteOcupacionServiceTest {

    private final OcupacionRepository ocupacion = mock(OcupacionRepository.class);
    private final CatalogoService catalogos = mock(CatalogoService.class);
    private final UsuarioService usuarios = mock(UsuarioService.class);
    private final ReporteOcupacionService servicio = new ReporteOcupacionService(ocupacion, catalogos, usuarios);

    private final Salon avril = salon((short) 1, "avril", "Avril");
    private final Salon club = salon((short) 2, "club", "Club");
    private final TipoEvento quince = tipo((short) 2, "Quince");
    private final Usuario lucia = vendedora(10L, "Lucía Ferreyra");

    @BeforeEach
    void catalogos() {
        when(catalogos.salones()).thenReturn(List.of(avril, club));
        when(catalogos.tiposEvento()).thenReturn(List.of(quince));
        when(usuarios.todos()).thenReturn(List.of(lucia));
        when(usuarios.nombres(anyCollection())).thenReturn(Map.of(10L, "Lucía Ferreyra"));
    }

    @Test
    void cuentaLosEstadosOcupadosYSoloMarcaRealizadosYCerradosComoRealizados() {
        LocalDate desde = LocalDate.of(2040, 8, 1);
        LocalDate hasta = LocalDate.of(2040, 8, 31);
        when(ocupacion.consultar(desde, hasta, null, null, null, null)).thenReturn(List.of(
                evento(1, 1, EstadoEvento.SENADO), evento(2, 1, EstadoEvento.CONTRATADO),
                evento(3, 2, EstadoEvento.CONFIRMADO), evento(4, 2, EstadoEvento.EN_CURSO),
                evento(5, 1, EstadoEvento.REALIZADO), evento(6, 2, EstadoEvento.CERRADO)));
        when(ocupacion.bloqueadasPorSalon(desde, hasta, null, null)).thenReturn(Map.of());

        var resultado = servicio.consultar(desde, hasta, null, null, null, null);

        assertThat(resultado.resumen().unidadesOcupadas()).isEqualTo(6);
        assertThat(resultado.resumen().eventosRealizados()).isEqualTo(2);
        assertThat(resultado.porVendedoraYTipo()).singleElement()
                .satisfies(fila -> assertThat(fila.eventosIncluidos()).isEqualTo(6));
    }

    @Test
    void descuentaBloqueosYPromediaElPorcentajeDeCadaSalon() {
        LocalDate desde = LocalDate.of(2040, 9, 30);
        LocalDate hasta = LocalDate.of(2040, 10, 1);
        when(ocupacion.consultar(desde, hasta, null, null, null, null)).thenReturn(List.of(
                evento(1, 1, EstadoEvento.SENADO), evento(2, 1, EstadoEvento.CERRADO),
                evento(3, 2, EstadoEvento.REALIZADO)));
        when(ocupacion.bloqueadasPorSalon(desde, hasta, null, null)).thenReturn(Map.of((short) 1, 1L));

        var resultado = servicio.consultar(desde, hasta, null, null, null, null);

        assertThat(resultado.porSalon()).hasSize(2);
        assertThat(resultado.porSalon().getFirst().unidadesOcupadas()).isEqualTo(2);
        assertThat(resultado.porSalon().getFirst().unidadesDisponibles()).isEqualTo(3);
        assertThat(resultado.porSalon().getFirst().ocupacion()).isEqualTo(66.67);
        assertThat(resultado.porSalon().get(1).unidadesDisponibles()).isEqualTo(4);
        assertThat(resultado.porSalon().get(1).ocupacion()).isEqualTo(25.0);
        assertThat(resultado.resumen().ocupacionPromedio()).isCloseTo(45.835, within(0.000001));
    }

    @Test
    void temporadaAltaCuentaSoloLosDiasDeOctubreADiciembreYReenviaElFiltro() {
        LocalDate desde = LocalDate.of(2040, 9, 30);
        LocalDate hasta = LocalDate.of(2040, 10, 1);
        when(ocupacion.consultar(desde, hasta, (short) 1, (short) 2, 10L, "ALTA"))
                .thenReturn(List.of(evento(1, 1, EstadoEvento.SENADO)));
        when(ocupacion.bloqueadasPorSalon(desde, hasta, (short) 1, "ALTA")).thenReturn(Map.of((short) 1, 1L));

        var resultado = servicio.consultar(desde, hasta, (short) 1, (short) 2, 10L, "ALTA");

        assertThat(resultado.regimen()).isEqualTo("ALTA");
        assertThat(resultado.porSalon()).singleElement().satisfies(salon -> {
            assertThat(salon.unidadesOcupadas()).isEqualTo(1);
            assertThat(salon.unidadesDisponibles()).isEqualTo(1);
            assertThat(salon.ocupacion()).isEqualTo(100.0);
        });
        verify(ocupacion).consultar(desde, hasta, (short) 1, (short) 2, 10L, "ALTA");
        verify(ocupacion).bloqueadasPorSalon(desde, hasta, (short) 1, "ALTA");
    }

    @Test
    void temporadaBajaCuentaSoloLosDiasDeEneroASeptiembre() {
        LocalDate desde = LocalDate.of(2040, 9, 30);
        LocalDate hasta = LocalDate.of(2040, 10, 1);
        when(ocupacion.consultar(desde, hasta, null, null, null, "BAJA"))
                .thenReturn(List.of(evento(1, 2, EstadoEvento.SENADO)));
        when(ocupacion.bloqueadasPorSalon(desde, hasta, null, "BAJA")).thenReturn(Map.of());

        var resultado = servicio.consultar(desde, hasta, null, null, null, "BAJA");

        assertThat(resultado.porSalon()).filteredOn(salon -> salon.salonId() == 2).singleElement()
                .satisfies(salon -> {
                    assertThat(salon.unidadesDisponibles()).isEqualTo(2);
                    assertThat(salon.ocupacion()).isEqualTo(50.0);
                });
    }

    @Test
    void incluyeEl29DeFebreroYTodosLosAniosDelPeriodo() {
        LocalDate desde = LocalDate.of(2040, 1, 1);
        LocalDate hasta = LocalDate.of(2041, 12, 31);
        for (String regimen : List.of("BAJA", "ALTA")) {
            when(ocupacion.consultar(desde, hasta, null, null, null, regimen)).thenReturn(List.of());
            when(ocupacion.bloqueadasPorSalon(desde, hasta, null, regimen)).thenReturn(Map.of());
            var resultado = servicio.consultar(desde, hasta, null, null, null, regimen);
            long dias = regimen.equals("BAJA") ? 547 : 184;
            assertThat(resultado.porSalon()).allSatisfy(salon -> {
                assertThat(salon.unidadesDisponibles()).isEqualTo(dias * 2);
                assertThat(salon.ocupacion()).isZero();
            });
            assertThat(resultado.resumen().ocupacionPromedio()).isZero();
        }
    }

    @Test
    void periodoSinDiasDeTemporadaOSalonDevuelveCeroSinNaN() {
        LocalDate fecha = LocalDate.of(2040, 2, 29);
        when(ocupacion.consultar(fecha, fecha, null, null, null, "ALTA")).thenReturn(List.of());
        when(ocupacion.bloqueadasPorSalon(fecha, fecha, null, "ALTA")).thenReturn(Map.of());
        var sinDias = servicio.consultar(fecha, fecha, null, null, null, "ALTA");
        assertThat(sinDias.porSalon()).allSatisfy(salon -> {
            assertThat(salon.unidadesDisponibles()).isZero();
            assertThat(salon.ocupacion()).isZero();
        });
        when(catalogos.salones()).thenReturn(List.of());
        var sinSalones = servicio.consultar(fecha, fecha, null, null, null, "ALTA");
        assertThat(sinSalones.porSalon()).isEmpty();
        assertThat(sinSalones.resumen().ocupacionPromedio()).isZero();
    }

    private static Salon salon(short id, String codigo, String nombre) {
        Salon salon = mock(Salon.class);
        when(salon.getId()).thenReturn(id);
        when(salon.getCodigo()).thenReturn(codigo);
        when(salon.getNombre()).thenReturn(nombre);
        return salon;
    }

    private static TipoEvento tipo(short id, String nombre) {
        TipoEvento tipo = mock(TipoEvento.class);
        when(tipo.getId()).thenReturn(id);
        when(tipo.getNombre()).thenReturn(nombre);
        return tipo;
    }

    private static Usuario vendedora(long id, String nombre) {
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(id);
        when(usuario.getNombreCompleto()).thenReturn(nombre);
        when(usuario.codigosDeRol()).thenReturn(Set.of(RolCodigo.VENDEDORA));
        return usuario;
    }

    private static EventoOcupacion evento(long id, int salonId, EstadoEvento estado) {
        return new EventoOcupacion(id, (short) salonId, (short) 2, 10L, estado);
    }
}
