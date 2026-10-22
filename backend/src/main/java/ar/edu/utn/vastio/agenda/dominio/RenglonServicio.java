package ar.edu.utn.vastio.agenda.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Un renglón libre dentro de una categoría de servicio, con cantidad opcional: «Celíaco · 2», «Barra de tragos».
 * Nada predefinido (M-09): lo escribe quien carga el evento.
 */
@Embeddable
public class RenglonServicio {

    @Column(name = "texto", nullable = false, length = 120)
    private String texto;

    @Column(name = "cantidad")
    private Integer cantidad;

    protected RenglonServicio() {
    }

    public RenglonServicio(String texto, Integer cantidad) {
        this.texto = texto;
        this.cantidad = cantidad;
    }

    public String getTexto() {
        return texto;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    /** Cómo se lee en el historial y en la vista de cocina: «Celíaco (2)». */
    public String legible() {
        return cantidad == null ? texto : texto + " (" + cantidad + ")";
    }
}
