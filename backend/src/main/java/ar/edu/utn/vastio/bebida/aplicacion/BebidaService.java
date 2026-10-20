package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.CodigoBarra;
import ar.edu.utn.vastio.bebida.dominio.TipoBebida;
import ar.edu.utn.vastio.bebida.dominio.UnidadManipulacion;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.CodigoBarraRepository;
import ar.edu.utn.vastio.bebida.infraestructura.TipoBebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.UnidadManipulacionRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;

/**
 * Catálogo de bebidas (UI-25): alta, modificación, baja lógica, reactivación y búsqueda por código de barras (UI-30).
 * No hay dos bebidas activas con el mismo nombre y presentación, y un código pertenece a una sola bebida.
 * La baja se permite con saldo: la pantalla muestra antes dónde lo tiene ({@link #saldos}).
 */
@Service
@Transactional(readOnly = true)
public class BebidaService {

    /** Para el chequeo de nombre repetido al crear: ningún id vale 0. */
    private static final long SIN_ID = 0;

    private final BebidaRepository bebidas;
    private final CodigoBarraRepository codigos;
    private final TipoBebidaRepository tipos;
    private final UnidadManipulacionRepository unidades;

    public BebidaService(BebidaRepository bebidas, CodigoBarraRepository codigos, TipoBebidaRepository tipos,
            UnidadManipulacionRepository unidades) {
        this.bebidas = bebidas;
        this.codigos = codigos;
        this.tipos = tipos;
        this.unidades = unidades;
    }

    /** Código de barras y botellas que representa una lectura. */
    public record Codigo(String codigo, short unidades) {
    }

    /** Datos editables de una bebida. Las cantidades van en botellas. */
    public record DatosBebida(String nombre, String presentacion, short tipoId, short unidadId, short unidadesPorBulto,
            BigDecimal stockMinimo, List<Codigo> codigos) {
    }

    /** Ubicación donde la bebida tiene saldo distinto de cero, en botellas. */
    public record Saldo(short ubicacionId, String ubicacion, BigDecimal cantidad) {
    }

    /** Activas primero y después por nombre y presentación. Incluye las dadas de baja. */
    public List<Bebida> todas() {
        return bebidas.findAllConCodigos().stream()
                .sorted(Comparator.comparing(Bebida::isActivo).reversed()
                        .thenComparing(Bebida::getNombre, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Bebida::getPresentacion, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Bebida bebida(long id) {
        return bebidas.findById(id).orElseThrow(() -> ProblemaException.noEncontrado("BEBIDA_INEXISTENTE",
                "No encontramos esa bebida."));
    }

    /** La bebida de un código leído con la cámara o tipeado. Puede estar dada de baja: decide la pantalla. */
    public Bebida porCodigo(String codigo) {
        return codigos.findById(codigo.trim()).map(CodigoBarra::getBebida)
                .orElseThrow(() -> ProblemaException.noEncontrado("CODIGO_DESCONOCIDO",
                        "Ese código no está en el catálogo. Elegí la bebida a mano."));
    }

    public List<TipoBebida> tipos() {
        return tipos.findAllByOrderByIdAsc();
    }

    public List<UnidadManipulacion> unidades() {
        return unidades.findAllByOrderByIdAsc();
    }

    public List<Saldo> saldos(long id) {
        bebida(id);
        return bebidas.saldos(id).stream().map(s -> new Saldo(s.getUbicacionId(), s.getUbicacion(), s.getCantidad())).toList();
    }

    @Transactional
    public Bebida crear(DatosBebida datos) {
        exigirNombreLibre(datos.nombre(), datos.presentacion(), SIN_ID);
        Map<String, Short> nuevos = codigosValidados(datos.codigos(), SIN_ID);
        Bebida bebida = new Bebida(datos.nombre(), datos.presentacion(), tipo(datos.tipoId()), unidad(datos.unidadId()),
                datos.unidadesPorBulto(), datos.stockMinimo());
        bebida.fijarCodigos(nuevos);
        return bebidas.save(bebida);
    }

    /** Cambiar las unidades por bulto con movimientos registrados se permite: solo cambia cómo se muestra (decisión 7). */
    @Transactional
    public Bebida modificar(long id, DatosBebida datos) {
        Bebida bebida = bebida(id);
        if (bebida.isActivo()) {
            exigirNombreLibre(datos.nombre(), datos.presentacion(), id);
        }
        Map<String, Short> nuevos = codigosValidados(datos.codigos(), id);
        bebida.actualizar(datos.nombre(), datos.presentacion(), tipo(datos.tipoId()), unidad(datos.unidadId()),
                datos.unidadesPorBulto(), datos.stockMinimo());
        bebida.fijarCodigos(nuevos);
        return bebida;
    }

    /** Baja lógica, aunque tenga saldo: deja de ofrecerse para cargar y se sigue viendo en la consulta de stock. */
    @Transactional
    public Bebida darDeBaja(long id) {
        Bebida bebida = bebida(id);
        bebida.darDeBaja();
        return bebida;
    }

    @Transactional
    public Bebida reactivar(long id) {
        Bebida bebida = bebida(id);
        if (!bebida.isActivo()) {
            exigirNombreLibre(bebida.getNombre(), bebida.getPresentacion(), id);
            bebida.reactivar();
        }
        return bebida;
    }

    private void exigirNombreLibre(String nombre, String presentacion, long id) {
        bebidas.activasConMismoNombre(nombre, presentacion, id).stream().findFirst().ifPresent(otra -> {
            throw ProblemaException.conflicto("BEBIDA_REPETIDA",
                    "Ya hay una bebida activa «%s». Modificá esa en lugar de cargar otra.".formatted(otra.descripcion()));
        });
    }

    /** Sin repetidos en el pedido y sin códigos que ya sean de otra bebida (aunque esté dada de baja). */
    private Map<String, Short> codigosValidados(List<Codigo> pedidos, long id) {
        Map<String, Short> resultado = new LinkedHashMap<>();
        for (Codigo c : pedidos == null ? List.<Codigo>of() : pedidos) {
            if (resultado.put(c.codigo().trim(), c.unidades()) != null) {
                throw ProblemaException.reglaDeNegocio("CODIGO_REPETIDO",
                        "Cargaste dos veces el código %s. Dejalo una sola vez.".formatted(c.codigo().trim()));
            }
        }
        for (CodigoBarra existente : codigos.findByCodigoIn(resultado.keySet())) {
            Bebida otra = existente.getBebida();
            if (otra.getId() != id) {
                throw ProblemaException.conflicto("CODIGO_DE_OTRA_BEBIDA",
                        "El código %s ya es de %s%s. Quitáselo a esa bebida antes de usarlo acá.".formatted(
                                existente.getCodigo(), otra.descripcion(), otra.isActivo() ? "" : " (dada de baja)"));
            }
        }
        return resultado;
    }

    private TipoBebida tipo(short id) {
        return tipos.findById(id).orElseThrow(() -> ProblemaException.reglaDeNegocio("TIPO_BEBIDA_INEXISTENTE",
                "Elegí un tipo de bebida de la lista."));
    }

    private UnidadManipulacion unidad(short id) {
        return unidades.findById(id).orElseThrow(() -> ProblemaException.reglaDeNegocio("UNIDAD_INEXISTENTE",
                "Elegí una unidad de manipulación de la lista."));
    }
}
