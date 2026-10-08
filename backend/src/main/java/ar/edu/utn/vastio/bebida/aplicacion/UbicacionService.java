package ar.edu.utn.vastio.bebida.aplicacion;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.bebida.dominio.TipoUbicacion;
import ar.edu.utn.vastio.bebida.dominio.Ubicacion;
import ar.edu.utn.vastio.bebida.infraestructura.UbicacionRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;
import ar.edu.utn.vastio.configuracion.aplicacion.CatalogoService;
import ar.edu.utn.vastio.configuracion.dominio.Salon;

/**
 * Configurar ubicaciones de stock (UI-26): depósito madre (uno solo activo), depósitos de transición y barras (una por
 * salón; Avril, hasta dos). Cada barra se abastece del depósito madre o de una transición, y en ese caso puede tener
 * habilitado el retiro directo del depósito madre como contingencia (docs/sprint-3.md, decisiones 8 a 11).
 */
@Service
@Transactional(readOnly = true)
public class UbicacionService {

    /** Para los chequeos de nombre repetido al crear: ningún id vale 0. */
    private static final short SIN_ID = 0;

    private final UbicacionRepository ubicaciones;
    private final CatalogoService catalogos;

    public UbicacionService(UbicacionRepository ubicaciones, CatalogoService catalogos) {
        this.ubicaciones = ubicaciones;
        this.catalogos = catalogos;
    }

    /**
     * Datos editables. {@code abastecimientoId} y {@code permiteRetiroDirecto} solo cuentan en la barra; sin
     * abastecimiento, la barra se abastece del depósito madre.
     */
    public record DatosUbicacion(String nombre, Short salonId, Short abastecimientoId, boolean permiteRetiroDirecto) {
    }

    /** En el orden del circuito (depósito, transición, barra), activas primero y por nombre. Incluye las dadas de baja. */
    public List<Ubicacion> todas() {
        return ubicaciones.findAllConAbastecimiento().stream()
                .sorted(Comparator.comparing(Ubicacion::getTipo)
                        .thenComparing(Comparator.comparing(Ubicacion::isActivo).reversed())
                        .thenComparing(Ubicacion::getNombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Ubicacion ubicacion(short id) {
        return ubicaciones.findById(id).orElseThrow(() -> ProblemaException.noEncontrado("UBICACION_INEXISTENTE",
                "No encontramos esa ubicación."));
    }

    /** Nombre de cada salón, para mostrar las barras y transiciones. */
    public List<Salon> salones() {
        return catalogos.salones();
    }

    @Transactional
    public Ubicacion crear(TipoUbicacion tipo, DatosUbicacion datos) {
        exigirNombreLibre(datos.nombre(), SIN_ID);
        if (tipo == TipoUbicacion.DEPOSITO) {
            exigirSinOtroDepositoMadre(SIN_ID);
        }
        Ubicacion ubicacion = new Ubicacion(tipo, datos.nombre());
        aplicar(ubicacion, datos);
        return ubicaciones.save(ubicacion);
    }

    /** El tipo no cambia: para pasar de transición a barra se da de baja una y se crea la otra. */
    @Transactional
    public Ubicacion modificar(short id, TipoUbicacion tipo, DatosUbicacion datos) {
        Ubicacion ubicacion = ubicacion(id);
        if (tipo != null && tipo != ubicacion.getTipo()) {
            throw ProblemaException.reglaDeNegocio("TIPO_NO_CAMBIA",
                    "El tipo de una ubicación no se cambia. Dala de baja y creá una nueva del tipo que necesitás.");
        }
        exigirNombreLibre(datos.nombre(), id);
        aplicar(ubicacion, datos);
        return ubicacion;
    }

    /**
     * Se rechaza si es el depósito madre, si tiene saldo distinto de cero o si es una transición de la que se abastece
     * alguna barra activa (decisión 11).
     */
    @Transactional
    public Ubicacion darDeBaja(short id) {
        Ubicacion ubicacion = ubicacion(id);
        if (!ubicacion.isActivo()) {
            return ubicacion;
        }
        if (ubicacion.getTipo() == TipoUbicacion.DEPOSITO) {
            throw ProblemaException.reglaDeNegocio("BAJA_DEPOSITO_MADRE",
                    "El depósito madre no se puede dar de baja: de ahí sale toda la mercadería.");
        }
        long conSaldo = ubicaciones.bebidasConSaldo(id);
        if (conSaldo > 0) {
            throw ProblemaException.reglaDeNegocio("BAJA_CON_SALDO",
                    "%s tiene saldo de %d %s. Pasalo a otra ubicación o registrá un recuento antes de darla de baja."
                            .formatted(ubicacion.getNombre(), conSaldo, conSaldo == 1 ? "bebida" : "bebidas"));
        }
        List<Ubicacion> abastecidas = ubicaciones.findByAbastecimientoIdAndActivoTrue(id);
        if (!abastecidas.isEmpty()) {
            throw ProblemaException.reglaDeNegocio("BAJA_TRANSICION_EN_USO",
                    "%s abastece a %s. Cambiá desde dónde se abastece antes de darla de baja."
                            .formatted(ubicacion.getNombre(), nombres(abastecidas)));
        }
        ubicacion.darDeBaja();
        return ubicacion;
    }

    /** Vuelve a chequear lo que la baja dejó de garantizar: un solo depósito madre, el máximo de barras y su origen. */
    @Transactional
    public Ubicacion reactivar(short id) {
        Ubicacion ubicacion = ubicacion(id);
        if (ubicacion.isActivo()) {
            return ubicacion;
        }
        switch (ubicacion.getTipo()) {
            case DEPOSITO -> exigirSinOtroDepositoMadre(id);
            case BARRA -> {
                exigirLugarParaBarra(salonActivo(ubicacion.getSalonId()), id);
                Ubicacion origen = ubicacion.getAbastecimiento();
                if (!origen.isActivo()) {
                    throw ProblemaException.reglaDeNegocio("ABASTECIMIENTO_INACTIVO",
                            "Se abastece de %s, que está dada de baja. Cambiá desde dónde se abastece y volvé a reactivarla."
                                    .formatted(origen.getNombre()));
                }
            }
            case TRANSICION -> {
                if (ubicacion.getSalonId() != null) {
                    salonActivo(ubicacion.getSalonId());
                }
            }
        }
        ubicacion.reactivar();
        return ubicacion;
    }

    private void aplicar(Ubicacion ubicacion, DatosUbicacion datos) {
        Ubicacion origen = null;
        Short salonId = datos.salonId();
        switch (ubicacion.getTipo()) {
            case DEPOSITO -> salonId = null;
            case TRANSICION -> {
                if (salonId != null && !Objects.equals(salonId, ubicacion.getSalonId())) {
                    salonActivo(salonId);
                }
            }
            case BARRA -> {
                if (salonId == null) {
                    throw ProblemaException.reglaDeNegocio("BARRA_SIN_SALON", "Elegí el salón de la barra.");
                }
                if (!Objects.equals(salonId, ubicacion.getSalonId()) || ubicacion.getId() == null) {
                    exigirLugarParaBarra(salonActivo(salonId), ubicacion.getId() == null ? SIN_ID : ubicacion.getId());
                }
                origen = origen(datos.abastecimientoId());
            }
        }
        ubicacion.actualizar(datos.nombre(), salonId, origen, datos.permiteRetiroDirecto());
    }

    /** El depósito madre o una transición activa; sin elegir, el depósito madre. */
    private Ubicacion origen(Short id) {
        if (id == null) {
            return ubicaciones.findFirstByTipoAndActivoTrue(TipoUbicacion.DEPOSITO).orElseThrow(() ->
                    ProblemaException.reglaDeNegocio("SIN_DEPOSITO_MADRE",
                            "No hay un depósito madre activo. Cargalo antes de configurar las barras."));
        }
        Ubicacion origen = ubicacion(id);
        if (!origen.isActivo() || origen.getTipo() == TipoUbicacion.BARRA) {
            throw ProblemaException.reglaDeNegocio("ABASTECIMIENTO_INVALIDO",
                    "Una barra se abastece del depósito madre o de un depósito de transición activo.");
        }
        return origen;
    }

    private Salon salonActivo(Short id) {
        Salon salon = catalogos.salones().stream().filter(s -> s.getId().equals(id)).findFirst()
                .orElseThrow(() -> ProblemaException.reglaDeNegocio("SALON_INEXISTENTE", "Elegí un salón de la lista."));
        if (!salon.isActivo()) {
            throw ProblemaException.reglaDeNegocio("SALON_INACTIVO",
                    "%s está dado de baja. Elegí un salón activo.".formatted(salon.getNombre()));
        }
        return salon;
    }

    private void exigirLugarParaBarra(Salon salon, short id) {
        long activas = ubicaciones.findByTipoAndSalonIdAndActivoTrue(TipoUbicacion.BARRA, salon.getId()).stream()
                .filter(b -> b.getId() != id).count();
        if (activas >= salon.getMaximoBarras()) {
            throw ProblemaException.conflicto("MAXIMO_BARRAS",
                    "%s ya tiene %s, que es el máximo.".formatted(salon.getNombre(),
                            salon.getMaximoBarras() == 1 ? "1 barra activa" : salon.getMaximoBarras() + " barras activas"));
        }
    }

    private void exigirSinOtroDepositoMadre(short id) {
        ubicaciones.findFirstByTipoAndActivoTrue(TipoUbicacion.DEPOSITO).filter(d -> d.getId() != id).ifPresent(d -> {
            throw ProblemaException.conflicto("DEPOSITO_MADRE_EXISTENTE",
                    "Ya hay un depósito madre: %s. Solo puede haber uno activo.".formatted(d.getNombre()));
        });
    }

    private void exigirNombreLibre(String nombre, short id) {
        if (ubicaciones.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw ProblemaException.conflicto("NOMBRE_REPETIDO", "Ya hay una ubicación con ese nombre.");
        }
    }

    private static String nombres(List<Ubicacion> lista) {
        return String.join(", ", lista.stream().map(Ubicacion::getNombre).toList());
    }
}
