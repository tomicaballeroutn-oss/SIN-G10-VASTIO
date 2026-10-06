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

/**
 * Archivo del legajo del evento (p. ej. el contrato digitalizado). El archivo vive en el almacenamiento;
 * la base guarda la referencia ({@code ruta}, relativa al directorio del legajo).
 */
@Entity
@Table(name = "documento_evento")
public class DocumentoEvento {

    /** Mismos valores que el CHECK {@code ck_documento_evento_tipo}. */
    public enum Tipo {
        CONTRATO,
        PRESUPUESTO,
        OTRO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "documento_id")
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 15)
    private Tipo tipo;

    /** Nombre original, para descargarlo con el mismo nombre. */
    @Column(name = "nombre_archivo", nullable = false, length = 150)
    private String nombreArchivo;

    @Column(name = "ruta", nullable = false)
    private String ruta;

    @Column(name = "mime_type", nullable = false, length = 60)
    private String mimeType;

    @Column(name = "tamano_bytes", nullable = false)
    private Integer tamanoBytes;

    /** Quién lo subió. */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "fecha_carga", nullable = false)
    private OffsetDateTime fechaCarga;

    protected DocumentoEvento() {
    }

    public DocumentoEvento(long eventoId, Tipo tipo, String nombreArchivo, String ruta, String mimeType, int tamanoBytes,
            long usuarioId, OffsetDateTime fechaCarga) {
        this.eventoId = eventoId;
        this.tipo = tipo;
        this.nombreArchivo = nombreArchivo;
        this.ruta = ruta;
        this.mimeType = mimeType;
        this.tamanoBytes = tamanoBytes;
        this.usuarioId = usuarioId;
        this.fechaCarga = fechaCarga;
    }

    public Long getId() {
        return id;
    }

    public long getEventoId() {
        return eventoId;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public String getRuta() {
        return ruta;
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getTamanoBytes() {
        return tamanoBytes;
    }

    public long getUsuarioId() {
        return usuarioId;
    }

    public OffsetDateTime getFechaCarga() {
        return fechaCarga;
    }
}
