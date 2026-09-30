package ar.edu.utn.vastio.agenda.dominio;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Un dato del evento que cambió: qué, de qué a qué, quién y cuándo. Solo inserción (trigger de V3).
 */
@Entity
@Immutable
@Table(name = "modificacion_evento")
public class ModificacionEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "modificacion_id")
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    /** Ej.: cantidad_invitados, cliente.telefono, contacto. */
    @Column(name = "campo", nullable = false, length = 60)
    private String campo;

    @Column(name = "valor_anterior", columnDefinition = "text")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "text")
    private String valorNuevo;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;

    protected ModificacionEvento() {
    }

    public ModificacionEvento(long eventoId, String campo, String valorAnterior, String valorNuevo, long usuarioId,
            OffsetDateTime fechaHora) {
        this.eventoId = eventoId;
        this.campo = campo;
        this.valorAnterior = valorAnterior;
        this.valorNuevo = valorNuevo;
        this.usuarioId = usuarioId;
        this.fechaHora = fechaHora;
    }

    public Long getId() {
        return id;
    }

    public long getEventoId() {
        return eventoId;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }

    public long getUsuarioId() {
        return usuarioId;
    }

    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }
}
