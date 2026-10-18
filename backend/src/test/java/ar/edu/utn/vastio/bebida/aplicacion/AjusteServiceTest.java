package ar.edu.utn.vastio.bebida.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import ar.edu.utn.vastio.bebida.aplicacion.AjusteService.DatosAjuste;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.TipoMovimiento;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.MovimientoStockRepository;
import ar.edu.utn.vastio.bebida.infraestructura.UbicacionRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.AmbitoMotivo;
import ar.edu.utn.vastio.configuracion.dominio.Motivo;
import ar.edu.utn.vastio.notificaciones.aplicacion.NotificacionService;

/** Regla de motivos de UI-38, sin requerir PostgreSQL ni Docker. */
class AjusteServiceTest {
    private final UbicacionRepository ubicaciones = mock(UbicacionRepository.class);
    private final BebidaRepository bebidas = mock(BebidaRepository.class);
    private final MovimientoStockRepository movimientos = mock(MovimientoStockRepository.class);
    private final MovimientoService movimientoService = mock(MovimientoService.class);
    private final CatalogoService catalogos = mock(CatalogoService.class);
    private final NotificacionService notificaciones = mock(NotificacionService.class);
    private final AjusteService ajustes = new AjusteService(ubicaciones, bebidas, movimientos, movimientoService, catalogos, notificaciones);
    private final Ubicacion ubicacion = mock(Ubicacion.class);
    private final Bebida bebida = mock(Bebida.class);

    @BeforeEach
    void catalogos() {
        when(ubicaciones.findById((short) 4)).thenReturn(Optional.of(ubicacion));
        when(ubicacion.isActivo()).thenReturn(true);
        when(ubicacion.getId()).thenReturn((short) 4);
        when(bebidas.findById(7L)).thenReturn(Optional.of(bebida));
        when(bebida.getId()).thenReturn(7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Rotura", " rotura "})
    void laRoturaGeneraMermaConElMotivoDelCatalogoSinIdFijo(String nombre) {
        motivo(AmbitoMotivo.AJUSTE, nombre, true);
        when(movimientoService.registrar(any())).thenAnswer(i -> new MovimientoService.Resultado(i.getArgument(0), BigDecimal.TEN, null));

        var resultado = ajustes.declararRotura(datos(BigDecimal.ONE), 5L);

        assertThat(resultado.movimiento().getTipo()).isEqualTo(TipoMovimiento.MERMA);
        assertThat(resultado.movimiento().getMotivoId()).isEqualTo((short) 80);
        assertThat(resultado.movimiento().getCantidad()).isEqualByComparingTo("1");
        assertThat(resultado.movimiento().getOrigen()).isSameAs(ubicacion);
        assertThat(resultado.movimiento().getDestino()).isNull();
        assertThat(resultado.movimiento().getObservacion()).isEqualTo("Detalle de prueba");
    }

    @ParameterizedTest
    @CsvSource({"AJUSTE,Error de carga,true", "AJUSTE,Otro,true", "AJUSTE,Recuento físico,true",
            "AJUSTE,Rotura,false", "CANCELACION,Rotura,true"})
    void laRoturaRechazaMotivosIncorrectosSinRegistrarMovimientos(AmbitoMotivo ambito, String nombre, boolean activo) {
        motivo(ambito, nombre, activo);

        assertThatThrownBy(() -> ajustes.declararRotura(datos(BigDecimal.ONE), 5L))
                .isInstanceOfSatisfying(ProblemaException.class, e -> assertThat(e.getCodigo()).isEqualTo("MOTIVO_INVALIDO"));
        verifyNoInteractions(movimientoService, movimientos, notificaciones);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Error de carga", "Recuento físico", "Otro", "Rotura"})
    void elRecuentoSigueAceptandoLosMotivosValidosDeAjuste(String nombre) {
        motivo(AmbitoMotivo.AJUSTE, nombre, true);
        when(movimientos.saldoBloqueado((short) 4, 7L)).thenReturn(Optional.of(BigDecimal.TEN));
        when(movimientoService.registrar(any())).thenAnswer(i -> new MovimientoService.Resultado(i.getArgument(0), null, new BigDecimal("12")));

        var resultado = ajustes.registrarRecuento(datos(new BigDecimal("12")), 5L);

        assertThat(resultado.movimiento().getTipo()).isEqualTo(TipoMovimiento.AJUSTE);
        assertThat(resultado.movimiento().getMotivoId()).isEqualTo((short) 80);
        assertThat(resultado.movimiento().getCantidad()).isEqualByComparingTo("2");
    }

    @Test
    void elRecuentoConOtroSigueExigiendoDetalle() {
        motivo(AmbitoMotivo.AJUSTE, "Otro", true);
        var datos = new DatosAjuste((short) 4, 7L, BigDecimal.ONE, (short) 80, null);
        assertThatThrownBy(() -> ajustes.registrarRecuento(datos, 5L))
                .isInstanceOfSatisfying(ProblemaException.class, e -> assertThat(e.getCodigo()).isEqualTo("FALTA_DETALLE"));
        verifyNoInteractions(movimientoService, movimientos, notificaciones);
    }

    private void motivo(AmbitoMotivo ambito, String nombre, boolean activo) {
        Motivo motivo = new Motivo(ambito, nombre);
        motivo.actualizar(nombre, activo);
        ReflectionTestUtils.setField(motivo, "id", (short) 80);
        when(catalogos.motivo((short) 80)).thenReturn(motivo);
    }

    private DatosAjuste datos(BigDecimal cantidad) {
        return new DatosAjuste((short) 4, 7L, cantidad, (short) 80, "Detalle de prueba");
    }
}
