package ar.edu.utn.vastio.bebida.dominio;

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

/**
 * Depósito madre, depósito de transición o barra. Configurable: el circuito funciona con o sin transición.
 * El salón es de otro módulo y se guarda por id. El tipo no cambia después del alta. La baja es lógica.
 */
@Entity
@Table(name = "ubicacion")
public class Ubicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ubicacion_id")
    private Short id;

    @Column(name = "nombre", nullable = false, unique = true, length = 60)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 12)
    private TipoUbicacion tipo;

    /** Obligatorio en la barra; opcional en la transición; nunca en el depósito madre. */
    @Column(name = "salon_id")
    private Short salonId;

    /** Solo barras: el depósito madre o la transición desde donde se abastece. EAGER: toda respuesta muestra su nombre. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ubicacion_abastecimiento_id")
    private Ubicacion abastecimiento;

    /** Solo barras abastecidas por una transición: contingencia de retiro directo del depósito madre. */
    @Column(name = "permite_retiro_directo", nullable = false)
    private boolean permiteRetiroDirecto;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected Ubicacion() {
    }

    public Ubicacion(TipoUbicacion tipo, String nombre) {
        this.tipo = tipo;
        this.nombre = nombre;
    }

    /**
     * Datos editables. El salón y el abastecimiento se descartan donde el tipo no los usa, y el retiro directo solo
     * queda si la barra se abastece de una transición: desde el depósito madre ya es directo.
     */
    public void actualizar(String nombre, Short salonId, Ubicacion abastecimiento, boolean permiteRetiroDirecto) {
        this.nombre = nombre;
        this.salonId = tipo == TipoUbicacion.DEPOSITO ? null : salonId;
        this.abastecimiento = tipo == TipoUbicacion.BARRA ? abastecimiento : null;
        this.permiteRetiroDirecto = this.abastecimiento != null && this.abastecimiento.getTipo() == TipoUbicacion.TRANSICION
                && permiteRetiroDirecto;
    }

    public void darDeBaja() {
        activo = false;
    }

    public void reactivar() {
        activo = true;
    }

    public Short getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoUbicacion getTipo() {
        return tipo;
    }

    public Short getSalonId() {
        return salonId;
    }

    public Ubicacion getAbastecimiento() {
        return abastecimiento;
    }

    public boolean isPermiteRetiroDirecto() {
        return permiteRetiroDirecto;
    }

    public boolean isActivo() {
        return activo;
    }
}
