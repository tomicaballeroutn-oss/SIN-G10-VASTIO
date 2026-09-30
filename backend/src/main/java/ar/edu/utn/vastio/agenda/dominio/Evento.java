package ar.edu.utn.vastio.agenda.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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
import jakarta.persistence.Version;

/**
 * Entidad central que vincula la agenda con la bebida. Tipo de evento, vendedora y planner son de otros
 * módulos (configuración y usuarios) y se guardan por id; sus nombres se piden a esos servicios.
 */
@Entity
@Table(name = "evento")
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evento_id")
    private Long id;

    /** EV-AAAA-NNNNN */
    @Column(name = "codigo", nullable = false, unique = true, length = 14)
    private String codigo;

    /** Cambia al reprogramar. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_id")
    private UnidadComercializable unidad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "tipo_evento_id", nullable = false)
    private Short tipoEventoId;

    /** Vendedora titular. */
    @Column(name = "vendedora_id", nullable = false)
    private Long vendedoraId;

    @Column(name = "planner_id")
    private Long plannerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 15)
    private EstadoEvento estado;

    @Column(name = "nombre", nullable = false, length = 120)
    private String nombre;

    @Column(name = "cantidad_invitados")
    private Integer cantidadInvitados;

    @Column(name = "invitados_definitivos", nullable = false)
    private boolean invitadosDefinitivos;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    /** No se muestran en cocina. */
    @Column(name = "observaciones_internas", columnDefinition = "text")
    private String observacionesInternas;

    /** Dato económico: se filtra por perfil antes de salir del backend. */
    @Column(name = "importe_sena", precision = 14, scale = 2)
    private BigDecimal importeSena;

    @Column(name = "fecha_sena")
    private LocalDate fechaSena;

    @Column(name = "firmante_nombre", length = 120)
    private String firmanteNombre;

    @Column(name = "firmante_dni", length = 10)
    private String firmanteDni;

    @Column(name = "firmante_contacto", length = 120)
    private String firmanteContacto;

    @Column(name = "fecha_firma_contrato")
    private LocalDate fechaFirmaContrato;

    @Column(name = "motivo_cancelacion_id")
    private Short motivoCancelacionId;

    @Column(name = "detalle_cancelacion")
    private String detalleCancelacion;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime fechaCreacion;

    /** Bloqueo optimista: dos ediciones simultáneas no se pisan. */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    protected Evento() {
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public UnidadComercializable getUnidad() {
        return unidad;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public short getTipoEventoId() {
        return tipoEventoId;
    }

    public long getVendedoraId() {
        return vendedoraId;
    }

    public Long getPlannerId() {
        return plannerId;
    }

    public EstadoEvento getEstado() {
        return estado;
    }

    public String getNombre() {
        return nombre;
    }

    public Integer getCantidadInvitados() {
        return cantidadInvitados;
    }

    public boolean isInvitadosDefinitivos() {
        return invitadosDefinitivos;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public String getObservacionesInternas() {
        return observacionesInternas;
    }

    public BigDecimal getImporteSena() {
        return importeSena;
    }

    public LocalDate getFechaSena() {
        return fechaSena;
    }

    public String getFirmanteNombre() {
        return firmanteNombre;
    }

    public String getFirmanteDni() {
        return firmanteDni;
    }

    public String getFirmanteContacto() {
        return firmanteContacto;
    }

    public LocalDate getFechaFirmaContrato() {
        return fechaFirmaContrato;
    }

    public Short getMotivoCancelacionId() {
        return motivoCancelacionId;
    }

    public String getDetalleCancelacion() {
        return detalleCancelacion;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public Integer getVersion() {
        return version;
    }
}
