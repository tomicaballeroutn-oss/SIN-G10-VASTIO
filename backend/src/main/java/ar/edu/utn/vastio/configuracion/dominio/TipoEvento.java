package ar.edu.utn.vastio.configuracion.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tipo de evento (Casamiento, Quince, …). Dado de baja no se ofrece en eventos nuevos; los existentes lo conservan.
 */
@Entity
@Table(name = "tipo_evento")
public class TipoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tipo_evento_id")
    private Short id;

    @Column(name = "nombre", nullable = false, unique = true, length = 40)
    private String nombre;

    /** true si exige asistencia por segmento (egresados). */
    @Column(name = "usa_segmentos", nullable = false)
    private boolean usaSegmentos;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected TipoEvento() {
    }

    public TipoEvento(String nombre, boolean usaSegmentos) {
        this.nombre = nombre;
        this.usaSegmentos = usaSegmentos;
    }

    public void actualizar(String nombre, boolean usaSegmentos, boolean activo) {
        this.nombre = nombre;
        this.usaSegmentos = usaSegmentos;
        this.activo = activo;
    }

    public Short getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isUsaSegmentos() {
        return usaSegmentos;
    }

    public boolean isActivo() {
        return activo;
    }
}
