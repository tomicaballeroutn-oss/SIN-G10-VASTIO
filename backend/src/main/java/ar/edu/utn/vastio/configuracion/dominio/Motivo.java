package ar.edu.utn.vastio.configuracion.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Motivo de cancelación, reprogramación, bloqueo o ajuste de stock. El nombre es único dentro del ámbito.
 */
@Entity
@Table(name = "motivo")
public class Motivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "motivo_id")
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(name = "ambito", nullable = false, length = 15)
    private AmbitoMotivo ambito;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected Motivo() {
    }

    public Motivo(AmbitoMotivo ambito, String nombre) {
        this.ambito = ambito;
        this.nombre = nombre;
    }

    /** El ámbito no cambia: un motivo de cancelación usado en un evento no puede pasar a ser de bloqueo. */
    public void actualizar(String nombre, boolean activo) {
        this.nombre = nombre;
        this.activo = activo;
    }

    /** «Otro» exige contar qué pasó (cancelaciones, ajustes de stock). */
    public boolean pideDetalle() {
        return nombre.trim().equalsIgnoreCase("otro") || nombre.trim().equalsIgnoreCase("otra");
    }

    public Short getId() {
        return id;
    }

    public AmbitoMotivo getAmbito() {
        return ambito;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActivo() {
        return activo;
    }
}
