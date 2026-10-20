package ar.edu.utn.vastio.bebida.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ar.edu.utn.vastio.bebida.aplicacion.IngresoService.DatosIngreso;
import ar.edu.utn.vastio.bebida.aplicacion.IngresoService.Renglon;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.Ingreso;
import ar.edu.utn.vastio.bebida.dominio.TipoMovimiento;
import ar.edu.utn.vastio.bebida.dominio.TipoUbicacion;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.IngresoRepository;
import ar.edu.utn.vastio.bebida.infraestructura.MovimientoStockRepository;
import ar.edu.utn.vastio.bebida.infraestructura.UbicacionRepository;

class IngresoServiceTest {
    private final IngresoRepository ingresos = mock(IngresoRepository.class);
    private final MovimientoStockRepository movimientos = mock(MovimientoStockRepository.class);
    private final MovimientoService movimientoService = mock(MovimientoService.class);
    private final BebidaRepository bebidas = mock(BebidaRepository.class);
    private final UbicacionRepository ubicaciones = mock(UbicacionRepository.class);
    private final IngresoService servicio = new IngresoService(ingresos, movimientos, movimientoService, bebidas, ubicaciones);
    private final Ubicacion deposito = mock(Ubicacion.class);
    private final List<Renglon> renglones = List.of(new Renglon(7L, new BigDecimal("12")));

    @BeforeEach
    void catalogo() {
        when(ubicaciones.findFirstByTipoAndActivoTrue(TipoUbicacion.DEPOSITO)).thenReturn(Optional.of(deposito));
        Bebida bebida = mock(Bebida.class);
        when(bebida.getId()).thenReturn(7L);
        when(bebida.isActivo()).thenReturn(true);
        when(bebidas.findAllById(any())).thenReturn(List.of(bebida));
        when(ingresos.save(any())).thenAnswer(i -> {
            Ingreso ingreso = i.getArgument(0);
            ReflectionTestUtils.setField(ingreso, "id", 90L);
            return ingreso;
        });
        when(movimientoService.registrar(any())).thenAnswer(i -> new MovimientoService.Resultado(i.getArgument(0), null, new BigDecimal("12")));
    }

    @Test
    void conservaElIngresoYElMovimiento() {
        var registrado = servicio.registrar(new DatosIngreso(null, "R-001", renglones), 5L);
        assertThat(registrado.ingreso().getNumeroRemito()).isEqualTo("R-001");
        var movimiento = registrado.renglones().getFirst();
        assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.INGRESO);
        assertThat(movimiento.getCantidad()).isEqualByComparingTo("12");
        assertThat(movimiento.getDestino()).isSameAs(deposito);
        assertThat(movimiento.getOrigen()).isNull();
        assertThat(movimiento.getIngresoId()).isEqualTo(90L);
        assertThat(movimiento.getUsuarioId()).isEqualTo(5L);
    }

}
