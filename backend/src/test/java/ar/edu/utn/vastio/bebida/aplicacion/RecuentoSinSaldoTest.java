package ar.edu.utn.vastio.bebida.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import ar.edu.utn.vastio.bebida.aplicacion.AjusteService.DatosAjuste;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.TipoMovimiento;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.MovimientoStockRepository;
import ar.edu.utn.vastio.bebida.infraestructura.UbicacionRepository;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.AmbitoMotivo;
import ar.edu.utn.vastio.configuracion.dominio.Motivo;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;

class RecuentoSinSaldoTest {
    private final MovimientoStockRepository movimientos = mock(MovimientoStockRepository.class);
    private final MovimientoService movimientoService = mock(MovimientoService.class);
    private final UbicacionRepository ubicaciones = mock(UbicacionRepository.class);
    private final BebidaRepository bebidas = mock(BebidaRepository.class);
    private final CatalogoService catalogos = mock(CatalogoService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final AjusteService servicio = new AjusteService(ubicaciones, bebidas, movimientos, movimientoService, catalogos, notificaciones);

    @BeforeEach
    void catalogo() {
        Ubicacion ubicacion = mock(Ubicacion.class);
        when(ubicacion.getId()).thenReturn((short) 2);
        when(ubicacion.isActivo()).thenReturn(true);
        when(ubicaciones.findById((short) 2)).thenReturn(Optional.of(ubicacion));
        Bebida bebida = mock(Bebida.class);
        when(bebida.getId()).thenReturn(7L);
        when(bebidas.findById(7L)).thenReturn(Optional.of(bebida));
        Motivo motivo = new Motivo(AmbitoMotivo.AJUSTE, "Recuento físico");
        ReflectionTestUtils.setField(motivo, "id", (short) 12);
        when(catalogos.motivo((short) 12)).thenReturn(motivo);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void saldoAusenteOCeroPermiteRegistrarLoContado(boolean filaPrevia) {
        when(movimientos.saldoBloqueado((short) 2, 7L)).thenReturn(filaPrevia ? Optional.of(BigDecimal.ZERO) : Optional.empty());
        when(movimientoService.registrar(any())).thenAnswer(i -> new MovimientoService.Resultado(i.getArgument(0), null, new BigDecimal("6")));
        var resultado = servicio.registrarRecuento(datos(6), 5L);
        assertThat(resultado.saldoAnterior()).isEqualByComparingTo("0");
        assertThat(resultado.saldo()).isEqualByComparingTo("6");
        assertThat(resultado.movimiento().getTipo()).isEqualTo(TipoMovimiento.AJUSTE);
        assertThat(resultado.movimiento().getCantidad()).isEqualByComparingTo("6");
        assertThat(resultado.movimiento().getMotivoId()).isEqualTo((short) 12);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void contarCeroNoRegistraMovimiento(boolean filaPrevia) {
        when(movimientos.saldoBloqueado((short) 2, 7L)).thenReturn(filaPrevia ? Optional.of(BigDecimal.ZERO) : Optional.empty());
        var resultado = servicio.registrarRecuento(datos(0), 5L);
        assertThat(resultado.movimiento()).isNull();
        assertThat(resultado.saldo()).isEqualByComparingTo("0");
        verifyNoInteractions(movimientoService, notificaciones);
    }

    private DatosAjuste datos(int cantidad) {
        return new DatosAjuste((short) 2, 7L, BigDecimal.valueOf(cantidad), (short) 12, null);
    }
}
