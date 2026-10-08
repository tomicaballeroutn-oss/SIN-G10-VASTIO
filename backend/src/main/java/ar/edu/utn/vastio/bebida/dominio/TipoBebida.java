package ar.edu.utn.vastio.bebida.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Clasificación de bebidas: Vino, Espumante, Destilado, Aperitivo, Cerveza. Lista fija, sin pantalla (Sprint 3, decisión 2).
 */
@Entity
@Immutable
@Table(name = "tipo_bebida")
public class TipoBebida {

    @Id
    @Column(name = "tipo_bebida_id")
    private Short id;

    @Column(name = "nombre", nullable = false, unique = true, length = 40)
    private String nombre;

    protected TipoBebida() {
    }

    public Short getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}
