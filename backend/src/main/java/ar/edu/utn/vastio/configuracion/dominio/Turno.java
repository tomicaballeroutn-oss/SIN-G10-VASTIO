package ar.edu.utn.vastio.configuracion.dominio;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Turno comercializable. Su horario define cuándo el evento pasa a En curso y a Realizado.
 */
@Entity
@Table(name = "turno")
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "turno_id")
    private Short id;

    /** mediodia · noche */
    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(name = "nombre", nullable = false, unique = true, length = 20)
    private String nombre;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    /** true si hora_fin corresponde al día siguiente. */
    @Column(name = "cruza_medianoche", nullable = false)
    private boolean cruzaMedianoche;

    protected Turno() {
    }

    /**
     * El horario se puede cambiar aunque haya eventos futuros: el salón se adapta a cambios de último momento.
     * Si la hora de fin es anterior a la de inicio, el turno termina al día siguiente.
     */
    public void actualizar(String nombre, LocalTime horaInicio, LocalTime horaFin) {
        if (horaInicio.equals(horaFin)) {
            throw new IllegalArgumentException("El turno no puede empezar y terminar a la misma hora");
        }
        this.nombre = nombre;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.cruzaMedianoche = horaFin.isBefore(horaInicio);
    }

    public Short getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public boolean isCruzaMedianoche() {
        return cruzaMedianoche;
    }
}
