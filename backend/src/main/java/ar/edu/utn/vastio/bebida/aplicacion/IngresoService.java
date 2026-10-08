package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.Ingreso;
import ar.edu.utn.vastio.bebida.dominio.MovimientoStock;
import ar.edu.utn.vastio.bebida.dominio.TipoMovimiento;
import ar.edu.utn.vastio.bebida.dominio.TipoUbicacion;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.IngresoRepository;
import ar.edu.utn.vastio.bebida.infraestructura.MovimientoStockRepository;
import ar.edu.utn.vastio.bebida.infraestructura.UbicacionRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;

/**
 * Registrar ingreso de bebida (UI-29): la mercadería comprada entra al depósito madre, con una cabecera (fecha y remito)
 * y un movimiento INGRESO por bebida que suma al saldo. No se edita ni se anula: un error se corrige con un recuento
 * (docs/sprint-3.md, decisión 14).
 */
@Service
@Transactional(readOnly = true)
public class IngresoService {

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);

    private final IngresoRepository ingresos;
    private final MovimientoStockRepository movimientos;
    private final MovimientoService movimientoService;
    private final BebidaRepository bebidas;
    private final UbicacionRepository ubicaciones;

    public IngresoService(IngresoRepository ingresos, MovimientoStockRepository movimientos, MovimientoService movimientoService,
            BebidaRepository bebidas, UbicacionRepository ubicaciones) {
        this.ingresos = ingresos;
        this.movimientos = movimientos;
        this.movimientoService = movimientoService;
        this.bebidas = bebidas;
        this.ubicaciones = ubicaciones;
    }

    /** Una bebida del ingreso, en botellas enteras mayores a 0. */
    public record Renglon(long bebidaId, BigDecimal cantidad) {
    }

    /** Sin fecha, hoy. */
    public record DatosIngreso(LocalDate fecha, String numeroRemito, List<Renglon> renglones) {
    }

    /** Un ingreso con sus bebidas, para mostrarlo con quién y cuándo. */
    public record IngresoRegistrado(Ingreso ingreso, List<MovimientoStock> renglones) {
    }

    @Transactional
    public IngresoRegistrado registrar(DatosIngreso datos, long usuarioId) {
        Ubicacion deposito = ubicaciones.findFirstByTipoAndActivoTrue(TipoUbicacion.DEPOSITO)
                .orElseThrow(() -> ProblemaException.reglaDeNegocio("SIN_DEPOSITO_MADRE",
                        "No hay un depósito madre activo. Configuralo en Parámetros, Ubicaciones de stock."));
        LocalDate hoy = LocalDate.now(ZONA);
        LocalDate fecha = datos.fecha() == null ? hoy : datos.fecha();
        if (fecha.isAfter(hoy)) {
            throw ProblemaException.reglaDeNegocio("FECHA_FUTURA", "La fecha de ingreso no puede ser posterior a hoy.");
        }
        if (datos.renglones() == null || datos.renglones().isEmpty()) {
            throw ProblemaException.reglaDeNegocio("INGRESO_VACIO", "Cargá al menos una bebida con su cantidad.");
        }
        Map<Long, Bebida> porId = bebidasActivas(datos.renglones());

        Ingreso ingreso = ingresos.save(Ingreso.deMercaderia(deposito, fecha, datos.numeroRemito(), usuarioId));
        List<MovimientoStock> registrados = datos.renglones().stream()
                .map(r -> movimientoService.registrar(MovimientoStock.entrada(TipoMovimiento.INGRESO, porId.get(r.bebidaId()),
                        r.cantidad(), deposito, ingreso, usuarioId)).movimiento())
                .toList();
        return new IngresoRegistrado(ingreso, registrados);
    }

    /** Los últimos ingresos de mercadería (sin la carga inicial), del más reciente al más viejo. */
    public List<IngresoRegistrado> recientes(int limite) {
        List<Ingreso> lista = ingresos.recientes(PageRequest.of(0, limite));
        Map<Long, List<MovimientoStock>> porIngreso = movimientos.deIngresos(lista.stream().map(Ingreso::getId).toList())
                .stream().collect(Collectors.groupingBy(MovimientoStock::getIngresoId));
        return lista.stream().map(i -> new IngresoRegistrado(i, porIngreso.getOrDefault(i.getId(), List.of()))).toList();
    }

    /** Cada bebida una sola vez y activa: una dada de baja no se ofrece para cargar (decisión 7). */
    private Map<Long, Bebida> bebidasActivas(List<Renglon> renglones) {
        Set<Long> vistas = new HashSet<>();
        for (Renglon r : renglones) {
            if (!vistas.add(r.bebidaId())) {
                throw ProblemaException.reglaDeNegocio("BEBIDA_REPETIDA_EN_INGRESO",
                        "Una bebida aparece dos veces. Sumá las cantidades en un solo renglón.");
            }
        }
        Map<Long, Bebida> porId = bebidas.findAllById(vistas).stream().collect(Collectors.toMap(Bebida::getId, Function.identity()));
        for (Renglon r : renglones) {
            Bebida bebida = porId.get(r.bebidaId());
            if (bebida == null) {
                throw ProblemaException.reglaDeNegocio("BEBIDA_INEXISTENTE", "Una de las bebidas ya no está en el catálogo. Volvé a cargar la pantalla.");
            }
            if (!bebida.isActivo()) {
                throw ProblemaException.reglaDeNegocio("BEBIDA_DADA_DE_BAJA",
                        "%s está dada de baja. Sacala del ingreso o reactivala en el catálogo.".formatted(bebida.descripcion()));
            }
        }
        return porId;
    }
}
