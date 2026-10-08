package ar.edu.utn.vastio.bebida.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Artículo del catálogo. Toda cantidad del sistema se expresa en botellas; la unidad de manipulación y las unidades
 * por bulto solo sirven para mostrar y cargar en cajas. La baja es lógica y reversible. No tiene precio: el sistema no
 * maneja dinero (Sprint 3, decisión 1).
 */
@Entity
@Table(name = "bebida")
public class Bebida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bebida_id")
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "presentacion", nullable = false, length = 40)
    private String presentacion;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tipo_bebida_id", nullable = false)
    private TipoBebida tipo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidad_manipulacion_id", nullable = false)
    private UnidadManipulacion unidad;

    @Column(name = "unidades_por_bulto", nullable = false)
    private short unidadesPorBulto;

    /** Umbral de stock bajo del depósito madre, en botellas. */
    @Column(name = "stock_minimo", precision = 10, scale = 2)
    private BigDecimal stockMinimo;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_baja")
    private OffsetDateTime fechaBaja;

    @OneToMany(mappedBy = "bebida", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CodigoBarra> codigos = new ArrayList<>();

    protected Bebida() {
    }

    public Bebida(String nombre, String presentacion, TipoBebida tipo, UnidadManipulacion unidad, short unidadesPorBulto,
            BigDecimal stockMinimo) {
        actualizar(nombre, presentacion, tipo, unidad, unidadesPorBulto, stockMinimo);
    }

    /** Con unidad Botella, las unidades por bulto quedan en 1 sin importar lo que se pida. */
    public final void actualizar(String nombre, String presentacion, TipoBebida tipo, UnidadManipulacion unidad,
            short unidadesPorBulto, BigDecimal stockMinimo) {
        this.nombre = nombre;
        this.presentacion = presentacion;
        this.tipo = tipo;
        this.unidad = unidad;
        this.unidadesPorBulto = unidad.esBotella() ? 1 : unidadesPorBulto;
        this.stockMinimo = stockMinimo;
    }

    /**
     * Deja exactamente esos códigos (código → botellas por lectura). Los que siguen se actualizan en el lugar en vez de
     * borrarse y volver a insertarse, porque Hibernate inserta antes de borrar y el código es la clave primaria.
     */
    public void fijarCodigos(Map<String, Short> nuevos) {
        codigos.removeIf(c -> !nuevos.containsKey(c.getCodigo()));
        Map<String, CodigoBarra> actuales = codigos.stream()
                .collect(Collectors.toMap(CodigoBarra::getCodigo, Function.identity()));
        nuevos.forEach((codigo, unidades) -> {
            CodigoBarra actual = actuales.get(codigo);
            if (actual != null) {
                actual.cambiarUnidades(unidades);
            } else {
                codigos.add(new CodigoBarra(this, codigo, unidades));
            }
        });
    }

    public void darDeBaja() {
        if (activo) {
            activo = false;
            fechaBaja = OffsetDateTime.now();
        }
    }

    public void reactivar() {
        activo = true;
        fechaBaja = null;
    }

    /** «Fernet Branca 750 ml», para los mensajes. */
    public String descripcion() {
        return nombre + " " + presentacion;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getPresentacion() {
        return presentacion;
    }

    public TipoBebida getTipo() {
        return tipo;
    }

    public UnidadManipulacion getUnidad() {
        return unidad;
    }

    public short getUnidadesPorBulto() {
        return unidadesPorBulto;
    }

    public BigDecimal getStockMinimo() {
        return stockMinimo;
    }

    public boolean isActivo() {
        return activo;
    }

    public OffsetDateTime getFechaBaja() {
        return fechaBaja;
    }

    /** Primero los de más botellas (la caja), después por código. */
    public List<CodigoBarra> getCodigos() {
        return codigos.stream()
                .sorted(Comparator.comparing(CodigoBarra::getUnidades).reversed().thenComparing(CodigoBarra::getCodigo))
                .toList();
    }
}
