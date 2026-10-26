package ar.edu.utn.vastio.bebida.dominio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Orden de preparación de un evento (UI-35, docs/sprint-4.md, decisiones 11 a 25): la bebida que se propone retirar
 * para todo el evento, sugerida a partir del consumo histórico y ajustada por Compras. Una sola vigente por evento
 * ({@code ux_orden_evento_vigente}). El evento es de otro módulo y se guarda por id.
 */
@Entity
@Table(name = "orden_preparacion")
public class OrdenPreparacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "orden_id")
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoOrden estado;

    /** El depósito madre. El destino ({@code ubicacion_destino_id}) no se usa: la orden es por evento, no por barra. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ubicacion_origen_id")
    private Ubicacion origen;

    /** Quién la generó; null si se generó sola al confirmar el evento. */
    @Column(name = "responsable_id")
    private Long responsableId;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime fechaCreacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuente", nullable = false, length = 20)
    private FuenteSugerencia fuente;

    /** Eventos cerrados sobre los que se calculó (0 con proporciones o sin datos). */
    @Column(name = "eventos_base", nullable = false)
    private int eventosBase;

    /** Asistentes del evento al calcular: si cambian, la orden lo avisa. */
    @Column(name = "asistentes_base")
    private Integer asistentesBase;

    @Column(name = "margen_porcentaje", nullable = false)
    private short margenPorcentaje;

    @Column(name = "fecha_calculo", nullable = false)
    private OffsetDateTime fechaCalculo;

    @Column(name = "usuario_modificacion_id")
    private Long usuarioModificacionId;

    @Column(name = "fecha_modificacion")
    private OffsetDateTime fechaModificacion;

    @Column(name = "usuario_confirmacion_id")
    private Long usuarioConfirmacionId;

    @Column(name = "fecha_confirmacion")
    private OffsetDateTime fechaConfirmacion;

    @Column(name = "usuario_reapertura_id")
    private Long usuarioReaperturaId;

    @Column(name = "fecha_reapertura")
    private OffsetDateTime fechaReapertura;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<DetalleOrden> renglones = new ArrayList<>();

    protected OrdenPreparacion() {
    }

    /**
     * Una orden nueva, Sugerida, con lo que calculó el sistema.
     *
     * @param responsableId null si la genera el sistema al confirmar el evento.
     */
    public static OrdenPreparacion generar(long eventoId, Ubicacion origen, Long responsableId, Sugerencia sugerencia,
            OffsetDateTime momento) {
        OrdenPreparacion o = new OrdenPreparacion();
        o.eventoId = eventoId;
        o.origen = origen;
        o.responsableId = responsableId;
        o.fechaCreacion = momento;
        o.sugerir(sugerencia, momento);
        return o;
    }

    /**
     * Lo que calculó el sistema: por bebida, las botellas a retirar (ya redondeadas a cajas completas).
     *
     * @param asistentes los del evento al calcular; null si no se conocen.
     */
    public record Sugerencia(FuenteSugerencia fuente, int eventosBase, Integer asistentes, short margenPorcentaje,
            Map<Bebida, BigDecimal> cantidades) {
    }

    /**
     * Recalcular sugerencia (decisión 19): vuelve a Sugerida y pisa lo que había ajustado Compras. Los renglones que
     * siguen se actualizan en su lugar (el índice único por orden y bebida no admite borrar y volver a insertar en el
     * mismo guardado).
     */
    public void recalcular(Sugerencia sugerencia, long usuarioId, OffsetDateTime momento) {
        sugerir(sugerencia, momento);
        this.usuarioModificacionId = usuarioId;
        this.fechaModificacion = momento;
    }

    private void sugerir(Sugerencia sugerencia, OffsetDateTime momento) {
        this.estado = EstadoOrden.SUGERIDA;
        this.fuente = sugerencia.fuente();
        this.eventosBase = sugerencia.eventosBase();
        this.asistentesBase = sugerencia.asistentes();
        this.margenPorcentaje = sugerencia.margenPorcentaje();
        this.fechaCalculo = momento;
        Map<Long, DetalleOrden> actuales = new LinkedHashMap<>();
        renglones.forEach(r -> actuales.put(r.getBebida().getId(), r));
        renglones.removeIf(r -> sugerencia.cantidades().keySet().stream().noneMatch(b -> b.getId().equals(r.getBebida().getId())));
        sugerencia.cantidades().forEach((bebida, cantidad) -> {
            DetalleOrden renglon = actuales.get(bebida.getId());
            if (renglon == null) {
                renglones.add(new DetalleOrden(this, bebida, cantidad, cantidad));
            } else {
                renglon.sugerir(cantidad);
            }
        });
    }

    /**
     * Ajustar orden (docs/sprint-4.md, decisión 23): lo que se va a retirar de cada bebida, en botellas. Una bebida que
     * no estaba se agrega con sugerida 0; para «sacarla» se deja en 0. Las que no vienen no cambian. Pasa a Modificada.
     */
    public void ajustar(Map<Bebida, BigDecimal> retirar, long usuarioId, OffsetDateTime momento) {
        Map<Long, DetalleOrden> actuales = new LinkedHashMap<>();
        renglones.forEach(r -> actuales.put(r.getBebida().getId(), r));
        retirar.forEach((bebida, cantidad) -> {
            DetalleOrden renglon = actuales.get(bebida.getId());
            if (renglon == null) {
                renglones.add(new DetalleOrden(this, bebida, BigDecimal.ZERO, cantidad));
            } else {
                renglon.retirar(cantidad);
            }
        });
        this.estado = EstadoOrden.MODIFICADA;
        this.usuarioModificacionId = usuarioId;
        this.fechaModificacion = momento;
    }

    /** Confirmar orden (decisión 24): ya no se edita; la salida a barra queda vinculada a ella. */
    public void confirmar(long usuarioId, OffsetDateTime momento) {
        this.estado = EstadoOrden.CONFIRMADA;
        this.usuarioConfirmacionId = usuarioId;
        this.fechaConfirmacion = momento;
    }

    /** Reabrir orden (decisión 24): si algo cambió sobre la marcha, vuelve a Modificada. */
    public void reabrir(long usuarioId, OffsetDateTime momento) {
        this.estado = EstadoOrden.MODIFICADA;
        this.usuarioReaperturaId = usuarioId;
        this.fechaReapertura = momento;
    }

    /** Al cancelar el evento: deja lugar para otra orden. */
    public void descartar() {
        this.estado = EstadoOrden.DESCARTADA;
    }

    public boolean isVigente() {
        return EstadoOrden.VIGENTES.contains(estado);
    }

    public Long getId() {
        return id;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public EstadoOrden getEstado() {
        return estado;
    }

    public Ubicacion getOrigen() {
        return origen;
    }

    public Long getResponsableId() {
        return responsableId;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public FuenteSugerencia getFuente() {
        return fuente;
    }

    public int getEventosBase() {
        return eventosBase;
    }

    public Integer getAsistentesBase() {
        return asistentesBase;
    }

    public short getMargenPorcentaje() {
        return margenPorcentaje;
    }

    public OffsetDateTime getFechaCalculo() {
        return fechaCalculo;
    }

    public Long getUsuarioModificacionId() {
        return usuarioModificacionId;
    }

    public OffsetDateTime getFechaModificacion() {
        return fechaModificacion;
    }

    public Long getUsuarioConfirmacionId() {
        return usuarioConfirmacionId;
    }

    public OffsetDateTime getFechaConfirmacion() {
        return fechaConfirmacion;
    }

    public Long getUsuarioReaperturaId() {
        return usuarioReaperturaId;
    }

    public OffsetDateTime getFechaReapertura() {
        return fechaReapertura;
    }

    public int getVersion() {
        return version;
    }

    public List<DetalleOrden> getRenglones() {
        return Collections.unmodifiableList(renglones);
    }
}
