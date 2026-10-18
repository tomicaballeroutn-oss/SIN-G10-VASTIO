package ar.edu.utn.vastio.bebida.aplicacion;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.bebida.dominio.Bebida;
import ar.edu.utn.vastio.bebida.dominio.Proveedor;
import ar.edu.utn.vastio.bebida.infraestructura.BebidaRepository;
import ar.edu.utn.vastio.bebida.infraestructura.ProveedorRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;

/**
 * Proveedores de bebida (paso 2 de la carga inicial, UI-28): alta, modificación, baja lógica y reactivación. Las bebidas
 * que provee son las que lo tienen como proveedor habitual: marcar una se la quita al proveedor que tenía
 * (docs/sprint-3.md, decisión 13).
 */
@Service
@Transactional(readOnly = true)
public class ProveedorService {

    /** Para los chequeos al crear: ningún id vale 0. */
    private static final long SIN_ID = 0;

    private final ProveedorRepository proveedores;
    private final BebidaRepository bebidas;

    public ProveedorService(ProveedorRepository proveedores, BebidaRepository bebidas) {
        this.proveedores = proveedores;
        this.bebidas = bebidas;
    }

    /** CUIT con o sin guiones; vacíos, null. */
    public record DatosProveedor(String razonSocial, String cuit, String telefono, String email) {
    }

    /** El proveedor con las bebidas que lo tienen como habitual, por nombre. */
    public record ProveedorConBebidas(Proveedor proveedor, List<Bebida> bebidas) {
    }

    /** Activos primero y por razón social. */
    public List<ProveedorConBebidas> todos() {
        Map<Long, List<Bebida>> porProveedor = bebidas.findAllConCodigos().stream()
                .filter(b -> b.getProveedorHabitual() != null)
                .collect(Collectors.groupingBy(b -> b.getProveedorHabitual().getId()));
        return proveedores.findAll().stream()
                .sorted(Comparator.comparing(Proveedor::isActivo).reversed()
                        .thenComparing(Proveedor::getRazonSocial, String.CASE_INSENSITIVE_ORDER))
                .map(p -> new ProveedorConBebidas(p, ordenadas(porProveedor.getOrDefault(p.getId(), List.of()))))
                .toList();
    }

    @Transactional
    public ProveedorConBebidas crear(DatosProveedor datos) {
        String cuit = cuitValidado(datos.cuit(), SIN_ID);
        exigirRazonSocialLibre(datos.razonSocial(), SIN_ID);
        Proveedor proveedor = new Proveedor(datos.razonSocial());
        proveedor.actualizar(datos.razonSocial(), cuit, datos.telefono(), datos.email());
        return conBebidas(proveedores.save(proveedor));
    }

    @Transactional
    public ProveedorConBebidas modificar(long id, DatosProveedor datos) {
        Proveedor proveedor = proveedor(id);
        String cuit = cuitValidado(datos.cuit(), id);
        if (proveedor.isActivo()) {
            exigirRazonSocialLibre(datos.razonSocial(), id);
        }
        proveedor.actualizar(datos.razonSocial(), cuit, datos.telefono(), datos.email());
        return conBebidas(proveedor);
    }

    /** Las bebidas lo conservan como habitual (se ve como dado de baja) hasta que se les elija otro. */
    @Transactional
    public ProveedorConBebidas darDeBaja(long id) {
        Proveedor proveedor = proveedor(id);
        proveedor.darDeBaja();
        return conBebidas(proveedor);
    }

    @Transactional
    public ProveedorConBebidas reactivar(long id) {
        Proveedor proveedor = proveedor(id);
        if (!proveedor.isActivo()) {
            exigirRazonSocialLibre(proveedor.getRazonSocial(), id);
            proveedor.reactivar();
        }
        return conBebidas(proveedor);
    }

    /**
     * Deja exactamente esas bebidas con este proveedor habitual: las nuevas se lo cambian (aunque tuvieran otro) y las
     * que ya no están quedan sin proveedor habitual.
     */
    @Transactional
    public ProveedorConBebidas fijarBebidas(long id, Collection<Long> bebidaIds) {
        Proveedor proveedor = proveedor(id);
        if (!proveedor.isActivo()) {
            throw ProblemaException.reglaDeNegocio("PROVEEDOR_INACTIVO",
                    "%s está dado de baja. Reactivalo para asignarle bebidas.".formatted(proveedor.getRazonSocial()));
        }
        Set<Long> pedidas = new HashSet<>(bebidaIds);
        List<Bebida> elegidas = bebidas.findAllById(pedidas);
        if (elegidas.size() != pedidas.size()) {
            throw ProblemaException.reglaDeNegocio("BEBIDA_INEXISTENTE", "Una de las bebidas ya no está en el catálogo. Volvé a cargar la pantalla.");
        }
        bebidas.findByProveedorHabitualId(id).stream().filter(b -> !pedidas.contains(b.getId())).forEach(b -> b.asignarProveedor(null));
        elegidas.forEach(b -> b.asignarProveedor(proveedor));
        return new ProveedorConBebidas(proveedor, ordenadas(elegidas));
    }

    private ProveedorConBebidas conBebidas(Proveedor proveedor) {
        return new ProveedorConBebidas(proveedor, proveedor.getId() == null ? List.of() : ordenadas(bebidas.findByProveedorHabitualId(proveedor.getId())));
    }

    private static List<Bebida> ordenadas(List<Bebida> lista) {
        return lista.stream().sorted(Comparator.comparing(Bebida::getNombre, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /** Solo los dígitos; sin CUIT, null. Once dígitos con dígito verificador válido y sin repetir. */
    private String cuitValidado(String texto, long id) {
        String cuit = texto == null ? "" : texto.replaceAll("\\D", "");
        if (cuit.isEmpty()) {
            return null;
        }
        if (!Proveedor.cuitValido(cuit)) {
            throw ProblemaException.reglaDeNegocio("CUIT_INVALIDO",
                    "Revisá el CUIT: tiene que tener 11 números y el último no coincide con el dígito verificador.");
        }
        if (proveedores.existsByCuitAndIdNot(cuit, id)) {
            throw ProblemaException.conflicto("CUIT_REPETIDO", "Ya hay un proveedor con ese CUIT.");
        }
        return cuit;
    }

    private void exigirRazonSocialLibre(String razonSocial, long id) {
        if (proveedores.existsByActivoTrueAndRazonSocialIgnoreCaseAndIdNot(razonSocial, id)) {
            throw ProblemaException.conflicto("PROVEEDOR_REPETIDO", "Ya hay un proveedor activo «%s».".formatted(razonSocial));
        }
    }

    private Proveedor proveedor(long id) {
        return proveedores.findById(id).orElseThrow(() -> ProblemaException.noEncontrado("PROVEEDOR_INEXISTENTE",
                "No encontramos ese proveedor."));
    }
}
