package ar.edu.utn.vastio.bebida.dominio;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entrega de un evento hacia una barra: agrupa la salida inicial y los retiros adicionales. La abre el primer retiro y
 * la cierra la devolución (docs/sprint-3-operacion.md, decisiones 4 y 9). Una por evento y barra. El evento es de otro
 * módulo y se guarda por id.
 */
@Entity
@Table(name = "entrega")
public class Entrega {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "entrega_id")
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ubicacion_destino_id", nullable = false)
    private Ubicacion barra;

    /** La orden de preparación, si existía. Todavía no la usa nadie: es de «Registrar preparación del evento». */
    @Column(name = "orden_id")
    private Long ordenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoEntrega estado;

    @Column(name = "usuario_apertura_id", nullable = false)
    private Long usuarioAperturaId;

    @Column(name = "fecha_apertura", nullable = false)
    private OffsetDateTime fechaApertura;

    @Column(name = "usuario_cierre_id")
    private Long usuarioCierreId;

    @Column(name = "fecha_cierre")
    private OffsetDateTime fechaCierre;

    protected Entrega() {
    }

    public static Entrega abrir(long eventoId, Ubicacion barra, long usuarioId, OffsetDateTime momento) {
        Entrega e = new Entrega();
        e.eventoId = eventoId;
        e.barra = barra;
        e.estado = EstadoEntrega.ABIERTA;
        e.usuarioAperturaId = usuarioId;
        e.fechaApertura = momento;
        return e;
    }

    /** Con la devolución: ya no admite retiros. */
    public void cerrar(long usuarioId, OffsetDateTime momento) {
        estado = EstadoEntrega.CERRADA;
        usuarioCierreId = usuarioId;
        fechaCierre = momento;
    }

    public boolean isAbierta() {
        return estado == EstadoEntrega.ABIERTA;
    }

    public Long getId() {
        return id;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public Ubicacion getBarra() {
        return barra;
    }

    public Long getOrdenId() {
        return ordenId;
    }

    public EstadoEntrega getEstado() {
        return estado;
    }

    public Long getUsuarioAperturaId() {
        return usuarioAperturaId;
    }

    public OffsetDateTime getFechaApertura() {
        return fechaApertura;
    }

    public Long getUsuarioCierreId() {
        return usuarioCierreId;
    }

    public OffsetDateTime getFechaCierre() {
        return fechaCierre;
    }
}
