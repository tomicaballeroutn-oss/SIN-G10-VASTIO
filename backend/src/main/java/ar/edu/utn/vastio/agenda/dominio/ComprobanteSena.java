package ar.edu.utn.vastio.agenda.dominio;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Comprobante interno de la seña (M-07, V12): uno por evento, con número correlativo por año (CS-AAAA-NNNNN). Se crea
 * la primera vez que alguien lo descarga; las descargas siguientes repiten número y fecha de emisión. No es una factura.
 */
@Entity
@Table(name = "comprobante_sena")
public class ComprobanteSena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comprobante_id")
    private Long id;

    @Column(name = "evento_id", nullable = false, unique = true)
    private Long eventoId;

    @Column(name = "numero", nullable = false, unique = true, length = 14)
    private String numero;

    @Column(name = "fecha_emision", nullable = false)
    private OffsetDateTime fechaEmision;

    /** Quién lo emitió por primera vez. */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    protected ComprobanteSena() {
    }

    public ComprobanteSena(long eventoId, String numero, OffsetDateTime fechaEmision, long usuarioId) {
        this.eventoId = eventoId;
        this.numero = numero;
        this.fechaEmision = fechaEmision;
        this.usuarioId = usuarioId;
    }

    public Long getId() {
        return id;
    }

    public long getEventoId() {
        return eventoId;
    }

    public String getNumero() {
        return numero;
    }

    public OffsetDateTime getFechaEmision() {
        return fechaEmision;
    }

    public long getUsuarioId() {
        return usuarioId;
    }
}
