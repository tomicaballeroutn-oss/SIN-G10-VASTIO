package ar.edu.utn.vastio.notificaciones.dominio;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * Un destinatario de un aviso. El usuario es del módulo usuarios: se guarda por id.
 */
@Entity
@Table(name = "notificacion_destinatario")
public class NotificacionDestinatario {

    @EmbeddedId
    private Id id = new Id();

    @MapsId("notificacionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notificacion_id")
    private Notificacion notificacion;

    @Column(name = "leida", nullable = false)
    private boolean leida;

    @Column(name = "fecha_lectura")
    private OffsetDateTime fechaLectura;

    protected NotificacionDestinatario() {
    }

    NotificacionDestinatario(Notificacion notificacion, long usuarioId) {
        this.notificacion = notificacion;
        this.id.usuarioId = usuarioId;
    }

    public Notificacion getNotificacion() {
        return notificacion;
    }

    /** La primera lectura queda; volver a abrirla no cambia el momento. */
    public void marcarLeida(OffsetDateTime ahora) {
        if (!leida) {
            leida = true;
            fechaLectura = ahora;
        }
    }

    public long getUsuarioId() {
        return id.usuarioId;
    }

    public boolean isLeida() {
        return leida;
    }

    public OffsetDateTime getFechaLectura() {
        return fechaLectura;
    }

    @Embeddable
    public static class Id implements Serializable {

        @Column(name = "notificacion_id")
        private Long notificacionId;

        @Column(name = "usuario_id")
        private Long usuarioId;

        @Override
        public boolean equals(Object o) {
            return o instanceof Id otro && Objects.equals(notificacionId, otro.notificacionId)
                    && Objects.equals(usuarioId, otro.usuarioId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(notificacionId, usuarioId);
        }
    }
}
