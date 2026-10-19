package ar.edu.utn.vastio.bebida.dominio;

import java.time.OffsetDateTime;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/** Cabecera inmutable de un traspaso, independiente de orden y entrega. */
@Entity
@Immutable
@Table(name = "preparacion_evento")
public class PreparacionEvento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preparacion_id") private Long id;
    @Column(name = "evento_id", nullable = false) private long eventoId;
    @ManyToOne @JoinColumn(name = "barra_id", nullable = false) private Ubicacion barra;
    @ManyToOne @JoinColumn(name = "origen_id", nullable = false) private Ubicacion origen;
    @ManyToOne @JoinColumn(name = "destino_id", nullable = false) private Ubicacion destino;
    @Column(name = "usuario_id", nullable = false) private long usuarioId;
    @Column(name = "fecha_hora", nullable = false) private OffsetDateTime fechaHora;
    @Column(nullable = false, length = 100) private String clave;
    @Column(nullable = false, length = 64) private String huella;

    protected PreparacionEvento() { }

    public PreparacionEvento(long eventoId, Ubicacion barra, Ubicacion origen, Ubicacion destino, long usuarioId,
            OffsetDateTime fechaHora, String clave, String huella) {
        this.eventoId = eventoId;
        this.barra = barra;
        this.origen = origen;
        this.destino = destino;
        this.usuarioId = usuarioId;
        this.fechaHora = fechaHora;
        this.clave = clave;
        this.huella = huella;
    }
    public Long getId() { return id; }
    public long getEventoId() { return eventoId; }
    public Ubicacion getBarra() { return barra; }
    public Ubicacion getOrigen() { return origen; }
    public Ubicacion getDestino() { return destino; }
    public long getUsuarioId() { return usuarioId; }
    public OffsetDateTime getFechaHora() { return fechaHora; }
    public String getHuella() { return huella; }
}
