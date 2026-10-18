package ar.edu.utn.vastio.bebida.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Código de barras de un artículo. Identifica la bebida; la cantidad se tipea. {@code unidades} dice cuántas botellas
 * trae el envase que lo lleva (1 la botella, 6 o 12 la caja). Un código pertenece a una sola bebida.
 */
@Entity
@Table(name = "codigo_barra")
public class CodigoBarra {

    @Id
    @Column(name = "codigo", length = 20)
    private String codigo;

    /** EAGER: leer un código es para saber de qué bebida es. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "bebida_id", nullable = false)
    private Bebida bebida;

    @Column(name = "unidades", nullable = false)
    private short unidades;

    protected CodigoBarra() {
    }

    CodigoBarra(Bebida bebida, String codigo, short unidades) {
        this.bebida = bebida;
        this.codigo = codigo;
        this.unidades = unidades;
    }

    void cambiarUnidades(short unidades) {
        this.unidades = unidades;
    }

    public String getCodigo() {
        return codigo;
    }

    public Bebida getBebida() {
        return bebida;
    }

    public short getUnidades() {
        return unidades;
    }
}
