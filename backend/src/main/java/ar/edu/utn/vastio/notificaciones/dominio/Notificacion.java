package ar.edu.utn.vastio.notificaciones.dominio;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Aviso generado por un cambio de evento, con un destinatario por usuario (leída o no).
 */
@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notificacion_id")
    private Long id;

    /** El aviso lleva a la ficha de este evento. */
    @Column(name = "evento_id")
    private Long eventoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoNotificacion tipo;

    @Column(name = "mensaje", nullable = false)
    private String mensaje;

    /** Null si la generó el sistema. */
    @Column(name = "usuario_origen_id")
    private Long usuarioOrigenId;

    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;

    @OneToMany(mappedBy = "notificacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NotificacionDestinatario> destinatarios = new ArrayList<>();

    protected Notificacion() {
    }

    public Notificacion(Long eventoId, TipoNotificacion tipo, String mensaje, Long usuarioOrigenId) {
        this.eventoId = eventoId;
        this.tipo = tipo;
        this.mensaje = mensaje;
        this.usuarioOrigenId = usuarioOrigenId;
        this.fechaHora = OffsetDateTime.now();
    }

    public void agregarDestinatario(long usuarioId) {
        destinatarios.add(new NotificacionDestinatario(this, usuarioId));
    }

    public Long getId() {
        return id;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public Long getUsuarioOrigenId() {
        return usuarioOrigenId;
    }

    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }

    public List<NotificacionDestinatario> getDestinatarios() {
        return List.copyOf(destinatarios);
    }
}
