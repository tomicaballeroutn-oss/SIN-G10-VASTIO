package ar.edu.utn.vastio.agenda.dominio;

import java.time.OffsetDateTime;
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
 * En este sprint solo se lee para la agenda; el alta llega con «Registrar bloqueo de unidades».
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

    public Set<UnidadComercializable> getUnidades() {
        return Set.copyOf(unidades);
    }
}
