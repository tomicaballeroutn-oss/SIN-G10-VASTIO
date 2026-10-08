package ar.edu.utn.vastio.bebida.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Forma en que se mueve la mercadería: Caja, Pack o Botella. Es solo la forma de mostrar y cargar; las cantidades se
 * guardan en botellas (Sprint 3, decisión 3). Lista fija, sin pantalla.
 */
@Entity
@Immutable
@Table(name = "unidad_manipulacion")
public class UnidadManipulacion {

    private static final String BOTELLA = "Botella";

    @Id
    @Column(name = "unidad_manipulacion_id")
    private Short id;

    @Column(name = "nombre", nullable = false, unique = true, length = 20)
    private String nombre;

    protected UnidadManipulacion() {
    }

    /** Se mueve de a una botella: las unidades por bulto son siempre 1. */
    public boolean esBotella() {
        return BOTELLA.equalsIgnoreCase(nombre);
    }

    public Short getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}
