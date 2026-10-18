package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.Ingreso;
import ar.edu.utn.vastio.bebida.dominio.MovimientoStock;
import ar.edu.utn.vastio.bebida.dominio.TipoMovimiento;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.IngresoRepository;
import ar.edu.utn.vastio.bebida.infraestructura.MovimientoStockRepository;
import ar.edu.utn.vastio.bebida.infraestructura.UbicacionRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;

/**
 * Registrar inventario inicial (UI-28): la carga de puesta en marcha de cada ubicación. Se puede guardar varias veces
 * para retomarla: una bebida nueva se registra como INVENTARIO_INICIAL y una ya cargada que cambia, como AJUSTE por la
 * diferencia que referencia al original, con el motivo «Error de carga». Cuando la ubicación tiene un movimiento de
 * otro tipo, la carga queda cerrada y se corrige con un recuento (docs/sprint-3.md, decisión 12).
 */
@Service
@Transactional(readOnly = true)
public class InventarioInicialService {

    /** Motivo «Error de carga» (ámbito AJUSTE), cargado en V2. Por id: el nombre se puede editar en Parámetros. */
    static final short MOTIVO_ERROR_DE_CARGA = 10;

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);

    private final UbicacionRepository ubicaciones;
    private final MovimientoStockRepository movimientos;
    private final MovimientoService movimientoService;
    private final IngresoRepository ingresos;
    private final BebidaRepository bebidas;

    public InventarioInicialService(UbicacionRepository ubicaciones, MovimientoStockRepository movimientos,
            MovimientoService movimientoService, IngresoRepository ingresos, BebidaRepository bebidas) {
        this.ubicaciones = ubicaciones;
        this.movimientos = movimientos;
        this.movimientoService = movimientoService;
        this.ingresos = ingresos;
        this.bebidas = bebidas;
    }

    /** Cantidad de una bebida en la carga, en botellas enteras (0 para sacarla). */
    public record Renglon(long bebidaId, BigDecimal cantidad) {
    }

    /** Lo cargado por bebida (en botellas, ya corregido), si quedó cerrada y quién la tocó por última vez. */
    public record CargaInicial(Ubicacion ubicacion, boolean cerrada, Map<Long, BigDecimal> cargado, Long ultimoUsuarioId,
            OffsetDateTime ultimaModificacion) {
    }

    public CargaInicial carga(short ubicacionId) {
        return carga(ubicacion(ubicacionId));
    }

    @Transactional
    public CargaInicial guardar(short ubicacionId, List<Renglon> renglones, long usuarioId) {
        Ubicacion ubicacion = ubicaciones.bloquear(ubicacionId).orElseThrow(InventarioInicialService::inexistente);
        if (!ubicacion.isActivo()) {
            throw ProblemaException.reglaDeNegocio("UBICACION_INACTIVA",
                    "%s está dada de baja. Reactivala para cargar su inventario inicial.".formatted(ubicacion.getNombre()));
        }
        if (movimientos.tieneOtrosMovimientos(ubicacionId)) {
            throw ProblemaException.reglaDeNegocio("CARGA_INICIAL_CERRADA",
                    "La carga inicial de %s ya está cerrada porque tiene otros movimientos. Corregí el saldo con un recuento."
                            .formatted(ubicacion.getNombre()));
        }
        Map<Long, Bebida> porId = bebidasDe(renglones);
        Map<Long, List<MovimientoStock>> previos = movimientos.cargaInicial(ubicacionId).stream()
                .collect(Collectors.groupingBy(m -> m.getBebida().getId(), LinkedHashMap::new, Collectors.toList()));

        Ingreso cabecera = null;
        for (Renglon r : renglones) {
            Bebida bebida = porId.get(r.bebidaId());
            List<MovimientoStock> deLaBebida = previos.getOrDefault(r.bebidaId(), List.of());
            MovimientoStock original = deLaBebida.stream().filter(m -> m.getTipo() == TipoMovimiento.INVENTARIO_INICIAL).findFirst().orElse(null);
            if (original == null) {
                if (r.cantidad().signum() == 0) {
                    continue;
                }
                if (!bebida.isActivo()) {
                    throw ProblemaException.reglaDeNegocio("BEBIDA_DADA_DE_BAJA",
                            "%s está dada de baja. Sacala de la carga o reactivala en el catálogo.".formatted(bebida.descripcion()));
                }
                if (cabecera == null) {
                    cabecera = ingresos.save(Ingreso.deInventarioInicial(ubicacion, LocalDate.now(ZONA), usuarioId));
                }
                movimientoService.registrar(MovimientoStock.entrada(TipoMovimiento.INVENTARIO_INICIAL, bebida, r.cantidad(), ubicacion,
                        cabecera, usuarioId));
            } else {
                BigDecimal actual = neto(deLaBebida, ubicacion);
                BigDecimal diferencia = r.cantidad().subtract(actual);
                if (diferencia.signum() != 0) {
                    movimientoService.registrar(MovimientoStock.correccion(original, ubicacion, diferencia, MOTIVO_ERROR_DE_CARGA,
                            "Corrección de la carga inicial", usuarioId));
                }
            }
        }
        return carga(ubicacion);
    }

    private CargaInicial carga(Ubicacion ubicacion) {
        List<MovimientoStock> lista = movimientos.cargaInicial(ubicacion.getId());
        Map<Long, BigDecimal> cargado = new LinkedHashMap<>();
        lista.forEach(m -> cargado.merge(m.getBebida().getId(), m.efectoEn(ubicacion), BigDecimal::add));
        MovimientoStock ultimo = lista.isEmpty() ? null : lista.getLast();
        return new CargaInicial(ubicacion, movimientos.tieneOtrosMovimientos(ubicacion.getId()), cargado,
                ultimo == null ? null : ultimo.getUsuarioId(), ultimo == null ? null : ultimo.getFechaHora());
    }

    private static BigDecimal neto(List<MovimientoStock> lista, Ubicacion ubicacion) {
        return lista.stream().map(m -> m.efectoEn(ubicacion)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Cada bebida una sola vez y existente. Que esté activa se exige solo al cargarla por primera vez. */
    private Map<Long, Bebida> bebidasDe(List<Renglon> renglones) {
        Set<Long> vistas = new HashSet<>();
        for (Renglon r : renglones) {
            if (!vistas.add(r.bebidaId())) {
                throw ProblemaException.reglaDeNegocio("BEBIDA_REPETIDA_EN_CARGA", "Una bebida aparece dos veces. Dejala en un solo renglón.");
            }
        }
        Map<Long, Bebida> porId = bebidas.findAllById(vistas).stream().collect(Collectors.toMap(Bebida::getId, Function.identity()));
        if (porId.size() != vistas.size()) {
            throw ProblemaException.reglaDeNegocio("BEBIDA_INEXISTENTE", "Una de las bebidas ya no está en el catálogo. Volvé a cargar la pantalla.");
        }
        return porId;
    }

    private Ubicacion ubicacion(short id) {
        return ubicaciones.findById(id).orElseThrow(InventarioInicialService::inexistente);
    }

    private static ProblemaException inexistente() {
        return ProblemaException.noEncontrado("UBICACION_INEXISTENTE", "No encontramos esa ubicación.");
    }
}
