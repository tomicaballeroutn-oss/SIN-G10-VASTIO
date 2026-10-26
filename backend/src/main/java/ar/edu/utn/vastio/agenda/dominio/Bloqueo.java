package ar.edu.utn.vastio.agenda.dominio;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * Bloqueo de unidades por motivos no comerciales (UI-21). No es un estado del evento.
 * Se registra (A1-02) sobre un conjunto de unidades y se desbloquea: la fila no se borra, queda inactiva con quién
 * y cuándo la desbloqueó.
 */
@Entity
@Table(name = "bloqueo")
public class Bloqueo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bloqueo_id")
    private Long id;

    /** → motivo (ámbito BLOQUEO), del módulo configuración. */
    @Column(name = "motivo_id", nullable = false)
    private Short motivoId;

    @Column(name = "detalle")
    private String detalle;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "fecha_registro", nullable = false)
    private OffsetDateTime fechaRegistro;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "usuario_desbloqueo_id")
    private Long usuarioDesbloqueoId;

    @Column(name = "fecha_desbloqueo")
    private OffsetDateTime fechaDesbloqueo;

    @ManyToMany
    @JoinTable(name = "bloqueo_unidad",
            joinColumns = @JoinColumn(name = "bloqueo_id"),
            inverseJoinColumns = @JoinColumn(name = "unidad_id"))
    private Set<UnidadComercializable> unidades = new LinkedHashSet<>();

    protected Bloqueo() {
    }

    /** Bloqueo activo sobre esas unidades, que el servicio ya tomó con {@code FOR UPDATE}. */
    public Bloqueo(short motivoId, String detalle, long usuarioId, OffsetDateTime fechaRegistro,
            Collection<UnidadComercializable> unidades) {
        this.motivoId = motivoId;
        this.detalle = detalle;
        this.usuarioId = usuarioId;
        this.fechaRegistro = fechaRegistro;
        this.activo = true;
        this.unidades = new LinkedHashSet<>(unidades);
    }

    /** Las unidades vuelven a estar disponibles. El bloqueo queda en la base como historia. */
    public void desbloquear(long usuarioId, OffsetDateTime cuando) {
        this.activo = false;
        this.usuarioDesbloqueoId = usuarioId;
        this.fechaDesbloqueo = cuando;
    }

    public Long getId() {
        return id;
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

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public boolean isActivo() {
        return activo;
    }

    public Long getUsuarioDesbloqueoId() {
        return usuarioDesbloqueoId;
    }

    public OffsetDateTime getFechaDesbloqueo() {
        return fechaDesbloqueo;
    }

    public Set<UnidadComercializable> getUnidades() {
        return Set.copyOf(unidades);
    }
}
