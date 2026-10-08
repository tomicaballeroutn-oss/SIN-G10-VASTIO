package ar.edu.utn.vastio.bebida.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Cabecera de un ingreso de mercadería: destino, remito, fecha y quién lo registró. Cada bebida es un
 * {@link MovimientoStock} con este {@code ingreso_id}. También agrupa la carga inicial de una ubicación
 * ({@code es_inventario_inicial}). No se edita ni se anula: un error se corrige con un recuento.
 */
@Entity
@Table(name = "ingreso")
public class Ingreso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ingreso_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubicacion_destino_id", nullable = false)
    private Ubicacion destino;

    @Column(name = "proveedor_id")
    private Long proveedorId;

    @Column(name = "numero_remito", length = 30)
    private String numeroRemito;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @Column(name = "es_inventario_inicial", nullable = false)
    private boolean esInventarioInicial;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "fecha_registro", nullable = false)
    private OffsetDateTime fechaRegistro;

    protected Ingreso() {
    }

    private Ingreso(Ubicacion destino, LocalDate fechaIngreso, boolean esInventarioInicial, long usuarioId) {
        this.destino = destino;
        this.fechaIngreso = fechaIngreso;
        this.esInventarioInicial = esInventarioInicial;
        this.usuarioId = usuarioId;
        this.fechaRegistro = OffsetDateTime.now();
    }

    /** Mercadería comprada que entra al depósito madre. */
    public static Ingreso deMercaderia(Ubicacion deposito, LocalDate fecha, String numeroRemito, long usuarioId) {
        Ingreso ingreso = new Ingreso(deposito, fecha, false, usuarioId);
        ingreso.numeroRemito = numeroRemito;
        return ingreso;
    }

    public Long getId() {
        return id;
    }

    public Ubicacion getDestino() {
        return destino;
    }

    public Long getProveedorId() {
        return proveedorId;
    }

    public String getNumeroRemito() {
        return numeroRemito;
    }

    public LocalDate getFechaIngreso() {
        return fechaIngreso;
    }

    public boolean isEsInventarioInicial() {
        return esInventarioInicial;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }
}
