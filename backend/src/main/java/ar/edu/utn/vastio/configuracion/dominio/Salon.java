package ar.edu.utn.vastio.configuracion.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Salón del complejo. El color de la agenda sale de los tokens del sistema de diseño según {@code codigo}.
 */
@Entity
@Table(name = "salon")
public class Salon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "salon_id")
    private Short id;

    @Column(name = "nombre", nullable = false, unique = true, length = 40)
    private String nombre;

    @Column(name = "capacidad")
    private Integer capacidad;

    /** avril · club · santa-barbara */
    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected Salon() {
    }

    /**
     * El código no cambia: de él salen el color y el orden de la agenda. Un salón dado de baja
     * no acepta pre-reservas nuevas; sus eventos siguen en la agenda.
     */
    public void actualizar(String nombre, Integer capacidad, boolean activo) {
        this.nombre = nombre;
        this.capacidad = capacidad;
        this.activo = activo;
    }

    public Short getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public Integer getCapacidad() {
        return capacidad;
    }

    public String getCodigo() {
        return codigo;
    }

    public boolean isActivo() {
        return activo;
    }
}
