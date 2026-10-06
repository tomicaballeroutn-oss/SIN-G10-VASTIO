package ar.edu.utn.vastio.agenda.dominio;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Cambio de salón, fecha o turno de un evento. Conserva la unidad original y no cambia el estado («Reprogramado» no
 * es un estado). Solo inserción (trigger de V3). El motivo es del módulo configuración: se guarda por id.
 */
@Entity
@Immutable
@Table(name = "reprogramacion")
public class Reprogramacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reprogramacion_id")
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_anterior_id")
    private UnidadComercializable unidadAnterior;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_nueva_id")
    private UnidadComercializable unidadNueva;

    @Column(name = "motivo_id", nullable = false)
    private Short motivoId;

    @Column(name = "detalle")
    private String detalle;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;

    protected Reprogramacion() {
    }

    public Reprogramacion(long eventoId, UnidadComercializable unidadAnterior, UnidadComercializable unidadNueva,
            short motivoId, String detalle, long usuarioId, OffsetDateTime fechaHora) {
        this.eventoId = eventoId;
        this.unidadAnterior = unidadAnterior;
        this.unidadNueva = unidadNueva;
        this.motivoId = motivoId;
        this.detalle = detalle;
        this.usuarioId = usuarioId;
        this.fechaHora = fechaHora;
    }

    public Long getId() {
        return id;
    }

    public long getEventoId() {
        return eventoId;
    }

    public UnidadComercializable getUnidadAnterior() {
        return unidadAnterior;
    }

    public UnidadComercializable getUnidadNueva() {
        return unidadNueva;
    }

    public short getMotivoId() {
        return motivoId;
    }

    public String getDetalle() {
        return detalle;
    }

    public long getUsuarioId() {
        return usuarioId;
    }

    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }
}
