package ar.edu.utn.vastio.bebida.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

import ar.edu.utn.vastio.agenda.aplicacion.ConsultaHistoricaEventoService;
import ar.edu.utn.vastio.agenda.aplicacion.ConsultaHistoricaEventoService.EventoHistorico;
import ar.edu.utn.vastio.bebida.aplicacion.EstadisticaConsumoService.Filtros;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.ConsumoEventoRepository;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.TipoEvento;

class EstadisticaConsumoServiceTest {
    private final ConsultaHistoricaEventoService agenda = mock(ConsultaHistoricaEventoService.class);
    private final ConsumoEventoRepository consumos = mock(ConsumoEventoRepository.class);
    private final BebidaRepository bebidas = mock(BebidaRepository.class);
    private final CatalogoService catalogos = mock(CatalogoService.class);
    private final EstadisticaConsumoService servicio = new EstadisticaConsumoService(agenda, consumos, bebidas, catalogos);

    private void catalogo() {
        TipoEvento tipo = mock(TipoEvento.class);
        when(tipo.getId()).thenReturn((short) 2);
        when(tipo.getNombre()).thenReturn("Quince");
        when(tipo.isActivo()).thenReturn(true);
        when(catalogos.tiposEvento()).thenReturn(List.of(tipo));
        var bebida = mock(BebidaRepository.ReferenciaHistorica.class);
        when(bebida.getId()).thenReturn(1L);
        when(bebida.getNombre()).thenReturn("Prueba");
        when(bebida.getPresentacion()).thenReturn("750 ml");
        when(bebida.getActivo()).thenReturn(true);
        when(bebidas.referenciasHistoricas()).thenReturn(List.of(bebida));
    }

    @Test
    void consultaEnLotesSinNMasUnoYAunSinConsumoCuentaTodosLosEventos() {
        catalogo();
        when(agenda.cerrados(null, null, null, null)).thenReturn(LongStream.rangeClosed(1, 2001)
                .mapToObj(id -> new EventoHistorico(id, (short) 2, Integer.MAX_VALUE)).toList());
        when(consumos.historicos(anyList(), isNull())).thenReturn(List.of());
        var resultado = servicio.consultar(new Filtros(null, null, null, null, null));
        assertThat(resultado.resumen().eventos()).isEqualTo(2001);
        assertThat(resultado.resumen().invitadosRegistrados()).isEqualTo(2001L * Integer.MAX_VALUE);
        assertThat(resultado.porBebida().getFirst().medidas().promedioPorEvento()).isEqualByComparingTo("0");
        verify(consumos, times(3)).historicos(argThat(ids -> !ids.isEmpty() && ids.size() <= 1000), isNull());
        verify(agenda).cerrados(null, null, null, null);
        verifyNoMoreInteractions(consumos, agenda);
    }

    @Test
    void redondeaHalfUpAlFinalYExcluyeConsumoDeInvitadosInvalidos() {
        catalogo();
        when(agenda.cerrados(null, null, null, null)).thenReturn(List.of(
                new EventoHistorico(1, (short) 2, 200), new EventoHistorico(2, (short) 2, 0),
                new EventoHistorico(3, (short) 2, null)));
        var fila = mock(ConsumoEventoRepository.Historico.class);
        when(fila.getEventoId()).thenReturn(1L);
        when(fila.getBebidaId()).thenReturn(1L);
        when(fila.getCantidad()).thenReturn(new BigDecimal("0.01"));
        when(consumos.historicos(anyList(), isNull())).thenReturn(List.of(fila));
        var resultado = servicio.consultar(new Filtros(null, null, null, null, null));
        assertThat(resultado.resumen().porInvitadoRegistrado()).isEqualTo(new BigDecimal("0.0001"));
        assertThat(resultado.resumen().promedioPorEvento()).isEqualTo(new BigDecimal("0.0033"));
        assertThat(resultado.resumen().eventosConInvitadosValidos()).isEqualTo(1);
    }
}
