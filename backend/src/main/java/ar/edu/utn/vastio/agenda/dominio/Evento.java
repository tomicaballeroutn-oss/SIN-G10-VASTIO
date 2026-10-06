package ar.edu.utn.vastio.agenda.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ContactoEvento> contactos = new ArrayList<>();

    protected Evento() {
    }

    /**
     * Una pre-reserva nueva. Nace sin estado: lo fija {@code MaquinaDeEstados.registrarCreacion},
     * que además deja la primera fila del historial.
     */
    public Evento(String codigo, UnidadComercializable unidad, Cliente cliente, short tipoEventoId, long vendedoraId,
            String nombre) {
        this.codigo = codigo;
        this.unidad = unidad;
        this.cliente = cliente;
        this.tipoEventoId = tipoEventoId;
        this.vendedoraId = vendedoraId;
        this.nombre = nombre;
        this.fechaCreacion = OffsetDateTime.now();
    }

    /** Datos que completa «Registrar evento». El registro de qué cambió lo arma el caso de uso. */
    public void actualizarDatos(String nombre, short tipoEventoId, String observacionesInternas) {
        this.nombre = nombre;
        this.tipoEventoId = tipoEventoId;
        this.observacionesInternas = observacionesInternas;
    }

    /** Cantidad de invitados y si ya es definitiva (condición de «Confirmar evento»). */
    public void registrarInvitados(int cantidad, boolean definitivos) {
        this.cantidadInvitados = cantidad;
        this.invitadosDefinitivos = definitivos;
    }

    public ContactoEvento agregarContacto(String nombre, String vinculo, String telefono, String email) {
        ContactoEvento contacto = new ContactoEvento(this, nombre, vinculo, telefono, email);
        contactos.add(contacto);
        return contacto;
    }

    public void actualizarContacto(ContactoEvento contacto, String nombre, String vinculo, String telefono, String email) {
        contacto.actualizar(nombre, vinculo, telefono, email);
    }

    public void quitarContacto(ContactoEvento contacto) {
        contactos.remove(contacto);
    }

    public List<ContactoEvento> getContactos() {
        return List.copyOf(contactos);
    }

    /**
     * Datos de la seña. Se cargan antes de pasar a Señado: el CHECK de la base los exige desde ese estado.
     */
    public void registrarSena(BigDecimal importe, LocalDate fecha, String firmanteNombre, String firmanteDni,
            String firmanteContacto) {
        this.importeSena = importe;
        this.fechaSena = fecha;
        this.firmanteNombre = firmanteNombre;
        this.firmanteDni = firmanteDni;
        this.firmanteContacto = firmanteContacto;
    }

    /** Planner responsable de la jornada; null la quita (solo antes de confirmar). */
    public void asignarPlanner(Long plannerId) {
        this.plannerId = plannerId;
    }

    /** Fecha de firma del contrato. Se carga antes de pasar a Contratado: el CHECK de la base la exige desde ese estado. */
    public void registrarFirmaContrato(LocalDate fecha) {
        this.fechaFirmaContrato = fecha;
    }

    /** Solo la llama {@code MaquinaDeEstados}: es quien valida la transición y la registra en el historial. */
    public void cambiarEstado(EstadoEvento nuevo) {
        this.estado = nuevo;
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
