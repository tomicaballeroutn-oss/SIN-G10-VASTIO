package ar.edu.utn.vastio.bebida.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import ar.edu.utn.vastio.bebida.aplicacion.IngresoService.DatosIngreso;
import ar.edu.utn.vastio.bebida.aplicacion.IngresoService.Renglon;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.Ingreso;
import ar.edu.utn.vastio.bebida.dominio.Proveedor;
import ar.edu.utn.vastio.bebida.dominio.TipoMovimiento;
import ar.edu.utn.vastio.bebida.dominio.TipoUbicacion;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.IngresoRepository;
import ar.edu.utn.vastio.bebida.infraestructura.MovimientoStockRepository;
import ar.edu.utn.vastio.bebida.infraestructura.ProveedorRepository;
import ar.edu.utn.vastio.bebida.infraestructura.UbicacionRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;

class IngresoServiceTest {
    private final IngresoRepository ingresos = mock(IngresoRepository.class);
    private final MovimientoStockRepository movimientos = mock(MovimientoStockRepository.class);
    private final MovimientoService movimientoService = mock(MovimientoService.class);
    private final BebidaRepository bebidas = mock(BebidaRepository.class);
    private final UbicacionRepository ubicaciones = mock(UbicacionRepository.class);
    private final ProveedorRepository proveedores = mock(ProveedorRepository.class);
    private final IngresoService servicio = new IngresoService(ingresos, movimientos, movimientoService, bebidas, ubicaciones, proveedores);
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
    void unaSolicitudAnteriorSinProveedorConservaElIngresoYElMovimiento() {
        var registrado = servicio.registrar(new DatosIngreso(null, "R-001", renglones), 5L);
        assertThat(registrado.ingreso().getProveedorId()).isNull();
        assertThat(registrado.ingreso().getNumeroRemito()).isEqualTo("R-001");
        var movimiento = registrado.renglones().getFirst();
        assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.INGRESO);
        assertThat(movimiento.getCantidad()).isEqualByComparingTo("12");
        assertThat(movimiento.getDestino()).isSameAs(deposito);
        assertThat(movimiento.getOrigen()).isNull();
        assertThat(movimiento.getIngresoId()).isEqualTo(90L);
        assertThat(movimiento.getUsuarioId()).isEqualTo(5L);
        verifyNoInteractions(proveedores);
    }

    @Test
    void asociaElProveedorActivoSeleccionado() {
        Proveedor proveedor = proveedor();
        when(proveedores.findById(80L)).thenReturn(Optional.of(proveedor));
        var registrado = servicio.registrar(new DatosIngreso(null, null, renglones, 80L), 5L);
        assertThat(registrado.ingreso().getProveedor()).isSameAs(proveedor);
        assertThat(registrado.ingreso().getProveedorId()).isEqualTo(80L);
        assertThat(registrado.renglones().getFirst().getCantidad()).isEqualByComparingTo("12");
    }

    @Test
    void rechazaProveedorInexistenteSinGuardar() {
        when(proveedores.findById(80L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.registrar(new DatosIngreso(null, null, renglones, 80L), 5L))
                .isInstanceOfSatisfying(ProblemaException.class, e -> assertThat(e.getCodigo()).isEqualTo("PROVEEDOR_INEXISTENTE"));
        verifyNoInteractions(ingresos, movimientoService);
    }

    @Test
    void rechazaProveedorInactivoSinGuardar() {
        Proveedor proveedor = proveedor();
        proveedor.darDeBaja();
        when(proveedores.findById(80L)).thenReturn(Optional.of(proveedor));
        assertThatThrownBy(() -> servicio.registrar(new DatosIngreso(null, null, renglones, 80L), 5L))
                .isInstanceOfSatisfying(ProblemaException.class, e -> assertThat(e.getCodigo()).isEqualTo("PROVEEDOR_INACTIVO"));
        verifyNoInteractions(ingresos, movimientoService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rechazaIdentificadoresInvalidos(long id) {
        assertThatThrownBy(() -> servicio.registrar(new DatosIngreso(null, null, renglones, id), 5L))
                .isInstanceOfSatisfying(ProblemaException.class, e -> assertThat(e.getCodigo()).isEqualTo("PROVEEDOR_INVALIDO"));
        verifyNoInteractions(proveedores, ingresos, movimientoService);
    }

    @Test
    void recientesConservaProveedorHistoricoInactivoYAdmiteIngresosSinProveedor() {
        Proveedor proveedor = proveedor();
        proveedor.darDeBaja();
        Ingreso conProveedor = Ingreso.deMercaderia(deposito, LocalDate.now(), null, proveedor, 5L);
        Ingreso sinProveedor = Ingreso.deMercaderia(deposito, LocalDate.now(), null, null, 5L);
        ReflectionTestUtils.setField(conProveedor, "id", 90L);
        ReflectionTestUtils.setField(sinProveedor, "id", 91L);
        when(ingresos.recientes(any())).thenReturn(List.of(conProveedor, sinProveedor));
        when(movimientos.deIngresos(any())).thenReturn(List.of());
        var recientes = servicio.recientes(10);
        assertThat(recientes.getFirst().ingreso().getProveedor()).isSameAs(proveedor);
        assertThat(recientes.getFirst().ingreso().getProveedor().isActivo()).isFalse();
        assertThat(recientes.get(1).ingreso().getProveedor()).isNull();
    }

    private Proveedor proveedor() {
        Proveedor proveedor = new Proveedor("Distribuidora de prueba");
        ReflectionTestUtils.setField(proveedor, "id", 80L);
        return proveedor;
    }
}
