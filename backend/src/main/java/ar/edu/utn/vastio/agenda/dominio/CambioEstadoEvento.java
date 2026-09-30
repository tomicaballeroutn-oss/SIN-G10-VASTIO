package ar.edu.utn.vastio.agenda.dominio;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Una transición de estado (RNF-SEG-03). Solo inserción: la base rechaza UPDATE y DELETE (trigger de V3)
 * y Hibernate la trata como inmutable. La escribe únicamente {@code MaquinaDeEstados}.
 */
@Entity
@Immutable
@Table(name = "cambio_estado_evento")
public class CambioEstadoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cambio_id")
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    /** Null en la creación. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 15)
    private EstadoEvento estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 15)
    private EstadoEvento estadoNuevo;

    /** Null = transición automática del sistema. */
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;

    @Column(name = "observacion")
    private String observacion;

    protected CambioEstadoEvento() {
    }

    public CambioEstadoEvento(long eventoId, EstadoEvento estadoAnterior, EstadoEvento estadoNuevo, Long usuarioId,
            OffsetDateTime fechaHora, String observacion) {
        this.eventoId = eventoId;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.usuarioId = usuarioId;
        this.fechaHora = fechaHora;
        this.observacion = observacion;
    }

    public Long getId() {
        return id;
    }

    public long getEventoId() {
        return eventoId;
    }

    public EstadoEvento getEstadoAnterior() {
        return estadoAnterior;
    }

    public EstadoEvento getEstadoNuevo() {
        return estadoNuevo;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }

    public String getObservacion() {
        return observacion;
    }
}
