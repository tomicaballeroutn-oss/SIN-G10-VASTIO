package ar.edu.utn.vastio.agenda.dominio;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Salón × fecha × turno. Se crea la primera vez que se ocupa o se bloquea, y es el ancla del
 * {@code SELECT … FOR UPDATE} que evita que dos pedidos tomen la misma unidad a la vez.
 * Salón y turno son del módulo configuración: se guardan por id.
 */
@Entity
@Table(name = "unidad_comercializable")
public class UnidadComercializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unidad_id")
    private Long id;

    @Column(name = "salon_id", nullable = false)
    private Short salonId;

    /** Día en que empieza la jornada: la noche del sábado es del sábado aunque termine el domingo. */
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "turno_id", nullable = false)
    private Short turnoId;

    protected UnidadComercializable() {
    }

    public UnidadComercializable(short salonId, LocalDate fecha, short turnoId) {
        this.salonId = salonId;
        this.fecha = fecha;
        this.turnoId = turnoId;
    }

    public Long getId() {
        return id;
    }

    public short getSalonId() {
        return salonId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public short getTurnoId() {
        return turnoId;
    }
}
