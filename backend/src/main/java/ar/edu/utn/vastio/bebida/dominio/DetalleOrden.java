package ar.edu.utn.vastio.bebida.dominio;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Renglón de la orden de preparación: lo que sugirió el sistema y lo que Compras decidió retirar, en botellas.
 */
@Entity
@Table(name = "detalle_orden_preparacion")
public class DetalleOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detalle_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id")
    private OrdenPreparacion orden;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "bebida_id")
    private Bebida bebida;

    /** 0 en una bebida que agregó Compras. */
    @Column(name = "cantidad_sugerida", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidadSugerida;

    @Column(name = "cantidad_a_retirar", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidadARetirar;

    protected DetalleOrden() {
    }

    DetalleOrden(OrdenPreparacion orden, Bebida bebida, BigDecimal cantidadSugerida, BigDecimal cantidadARetirar) {
        this.orden = orden;
        this.bebida = bebida;
        this.cantidadSugerida = cantidadSugerida;
        this.cantidadARetirar = cantidadARetirar;
    }

    void sugerir(BigDecimal cantidad) {
        this.cantidadSugerida = cantidad;
        this.cantidadARetirar = cantidad;
    }

    void retirar(BigDecimal cantidad) {
        this.cantidadARetirar = cantidad;
    }

    public Long getId() {
        return id;
    }

    public Bebida getBebida() {
        return bebida;
    }

    public BigDecimal getCantidadSugerida() {
        return cantidadSugerida;
    }

    public BigDecimal getCantidadARetirar() {
        return cantidadARetirar;
    }
}
