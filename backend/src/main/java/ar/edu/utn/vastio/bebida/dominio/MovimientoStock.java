package ar.edu.utn.vastio.bebida.dominio;

import java.math.BigDecimal;
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

import org.hibernate.annotations.Immutable;

/**
 * Asiento inalterable de mercadería (RNF-SEG-03). Solo inserción: el trigger {@code fn_solo_insercion} rechaza UPDATE y
 * DELETE, y las correcciones son asientos nuevos. La cantidad es positiva y en botellas; el sentido lo dan origen
 * (sale) y destino (entra). Guarda quién y cuándo.
 */
@Entity
@Immutable
@Table(name = "movimiento_stock")
public class MovimientoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movimiento_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 18)
    private TipoMovimiento tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bebida_id", nullable = false)
    private Bebida bebida;

    @Column(name = "cantidad", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_origen_id")
    private Ubicacion origen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ubicacion_destino_id")
    private Ubicacion destino;

    @Column(name = "evento_id")
    private Long eventoId;

    @Column(name = "entrega_id")
    private Long entregaId;

    @Column(name = "ingreso_id")
    private Long ingresoId;

    @Column(name = "movimiento_corregido_id")
    private Long movimientoCorregidoId;

    @Column(name = "motivo_id")
    private Short motivoId;

    @Column(name = "observacion", length = 255)
    private String observacion;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "fecha_hora", nullable = false)
    private OffsetDateTime fechaHora;

    protected MovimientoStock() {
    }

    private MovimientoStock(TipoMovimiento tipo, Bebida bebida, BigDecimal cantidad, Ubicacion origen, Ubicacion destino,
            long usuarioId) {
        if (cantidad.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad de un movimiento es positiva: el sentido lo dan origen y destino.");
        }
        this.tipo = tipo;
        this.bebida = bebida;
        this.cantidad = cantidad;
        this.origen = origen;
        this.destino = destino;
        this.usuarioId = usuarioId;
        this.fechaHora = OffsetDateTime.now();
    }

    /** Mercadería que entra al depósito con un ingreso (o con la carga inicial de una ubicación). */
    public static MovimientoStock entrada(TipoMovimiento tipo, Bebida bebida, BigDecimal cantidad, Ubicacion destino,
            Ingreso ingreso, long usuarioId) {
        MovimientoStock m = new MovimientoStock(tipo, bebida, cantidad, null, destino, usuarioId);
        m.ingresoId = ingreso.getId();
        return m;
    }

    /**
     * Corrección de un asiento por la diferencia (positiva: entra a la ubicación; negativa: sale), como AJUSTE que
     * referencia al corregido. El asiento original no se toca.
     */
    public static MovimientoStock correccion(MovimientoStock corregido, Ubicacion ubicacion, BigDecimal diferencia, short motivoId,
            String observacion, long usuarioId) {
        MovimientoStock m = ajuste(corregido.getBebida(), ubicacion, diferencia, motivoId, observacion, usuarioId);
        m.movimientoCorregidoId = corregido.getId();
        return m;
    }

    /** Ajuste por la diferencia (positiva: entra a la ubicación; negativa: sale), p. ej. un recuento físico. */
    public static MovimientoStock ajuste(Bebida bebida, Ubicacion ubicacion, BigDecimal diferencia, short motivoId,
            String observacion, long usuarioId) {
        boolean entra = diferencia.signum() > 0;
        MovimientoStock m = new MovimientoStock(TipoMovimiento.AJUSTE, bebida, diferencia.abs(), entra ? null : ubicacion,
                entra ? ubicacion : null, usuarioId);
        m.motivoId = motivoId;
        m.observacion = observacion;
        return m;
    }

    /** Mercadería que sale de la ubicación sin destino: una rotura declarada. */
    public static MovimientoStock merma(Bebida bebida, Ubicacion ubicacion, BigDecimal cantidad, short motivoId, String observacion,
            long usuarioId) {
        MovimientoStock m = new MovimientoStock(TipoMovimiento.MERMA, bebida, cantidad, ubicacion, null, usuarioId);
        m.motivoId = motivoId;
        m.observacion = observacion;
        return m;
    }

    /** Lo que suma (o resta) este asiento al saldo de la ubicación. */
    public BigDecimal efectoEn(Ubicacion ubicacion) {
        BigDecimal efecto = BigDecimal.ZERO;
        if (destino != null && destino.getId().equals(ubicacion.getId())) {
            efecto = efecto.add(cantidad);
        }
        if (origen != null && origen.getId().equals(ubicacion.getId())) {
            efecto = efecto.subtract(cantidad);
        }
        return efecto;
    }

    public Long getId() {
        return id;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public Bebida getBebida() {
        return bebida;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public Ubicacion getOrigen() {
        return origen;
    }

    public Ubicacion getDestino() {
        return destino;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public Long getEntregaId() {
        return entregaId;
    }

    public Long getIngresoId() {
        return ingresoId;
    }

    public Long getMovimientoCorregidoId() {
        return movimientoCorregidoId;
    }

    public Short getMotivoId() {
        return motivoId;
    }

    public String getObservacion() {
        return observacion;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public OffsetDateTime getFechaHora() {
        return fechaHora;
    }
}
