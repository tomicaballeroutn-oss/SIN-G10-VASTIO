package ar.edu.utn.vastio.agenda.dominio;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Lo acordado con el cliente en una categoría (Plato principal, Tipo de barra, …), con descripción libre.
 * Uno por categoría y evento ({@code ux_servicio_contratado_evento_categoria}, V4). La categoría es del módulo
 * configuración: se guarda por id.
 */
@Entity
@Table(name = "servicio_contratado")
public class ServicioContratado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "servicio_id")
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Column(name = "categoria_id", nullable = false)
    private Short categoriaId;

    @Column(name = "descripcion", nullable = false, columnDefinition = "text")
    private String descripcion;

    /** Último en modificarlo. */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "fecha_modificacion", nullable = false)
    private OffsetDateTime fechaModificacion;

    protected ServicioContratado() {
    }

    public ServicioContratado(long eventoId, short categoriaId, String descripcion, long usuarioId, OffsetDateTime momento) {
        this.eventoId = eventoId;
        this.categoriaId = categoriaId;
        this.descripcion = descripcion;
        this.usuarioId = usuarioId;
        this.fechaModificacion = momento;
    }

    public void cambiar(String descripcion, long usuarioId, OffsetDateTime momento) {
        this.descripcion = descripcion;
        this.usuarioId = usuarioId;
        this.fechaModificacion = momento;
    }

    public Long getId() {
        return id;
    }

    public long getEventoId() {
        return eventoId;
    }

    public short getCategoriaId() {
        return categoriaId;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public long getUsuarioId() {
        return usuarioId;
    }

    public OffsetDateTime getFechaModificacion() {
        return fechaModificacion;
    }
}
