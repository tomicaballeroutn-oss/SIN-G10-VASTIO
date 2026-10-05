package ar.edu.utn.vastio.configuracion.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Categoría de servicio contratado. Configurable: Meli no quiere desplegables fijos.
 */
@Entity
@Table(name = "categoria_servicio")
public class CategoriaServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "categoria_id")
    private Short id;

    @Column(name = "nombre", nullable = false, unique = true, length = 40)
    private String nombre;

    /** Orden de presentación en la ficha. */
    @Column(name = "orden", nullable = false)
    private Short orden;

    @Column(name = "visible_en_cocina", nullable = false)
    private boolean visibleEnCocina;

    /** Condición de «Confirmar evento»: tiene que haber un servicio cargado en esta categoría. */
    @Column(name = "requerida_para_confirmar", nullable = false)
    private boolean requeridaParaConfirmar;

    /** Categoría de bebida (bodega, tipo de barra): sus cambios en un evento confirmado le llegan a Compras. */
    @Column(name = "avisa_a_compras", nullable = false)
    private boolean avisaACompras;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected CategoriaServicio() {
    }

    public CategoriaServicio(String nombre, short orden, boolean visibleEnCocina, boolean requeridaParaConfirmar,
            boolean avisaACompras) {
        this.nombre = nombre;
        this.orden = orden;
        this.visibleEnCocina = visibleEnCocina;
        this.requeridaParaConfirmar = requeridaParaConfirmar;
        this.avisaACompras = avisaACompras;
    }

    public void actualizar(String nombre, short orden, boolean visibleEnCocina, boolean requeridaParaConfirmar,
            boolean avisaACompras, boolean activo) {
        this.nombre = nombre;
        this.orden = orden;
        this.visibleEnCocina = visibleEnCocina;
        this.requeridaParaConfirmar = requeridaParaConfirmar;
        this.avisaACompras = avisaACompras;
        this.activo = activo;
    }

    public Short getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public short getOrden() {
        return orden;
    }

    public boolean isVisibleEnCocina() {
        return visibleEnCocina;
    }

    public boolean isRequeridaParaConfirmar() {
        return requeridaParaConfirmar;
    }

    public boolean isAvisaACompras() {
        return avisaACompras;
    }

    public boolean isActivo() {
        return activo;
    }
}
