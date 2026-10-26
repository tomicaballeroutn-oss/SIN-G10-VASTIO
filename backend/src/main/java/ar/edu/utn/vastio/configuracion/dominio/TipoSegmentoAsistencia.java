package ar.edu.utn.vastio.configuracion.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Segmento de asistencia de los eventos que la cuentan por partes (egresados).
 */
@Entity
@Table(name = "tipo_segmento_asistencia")
public class TipoSegmentoAsistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tipo_segmento_id")
    private Short id;

    @Column(name = "nombre", nullable = false, unique = true, length = 40)
    private String nombre;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    /**
     * Su asistencia prevista es la cantidad de invitados del evento (en egresados, los comensales de la cena), así
     * nadie se cuenta dos veces. Uno solo la tiene ({@code ux_segmento_equivale_invitados}).
     */
    @Column(name = "equivale_invitados", nullable = false)
    private boolean equivaleInvitados;

    protected TipoSegmentoAsistencia() {
    }

    public TipoSegmentoAsistencia(String nombre) {
        this.nombre = nombre;
    }

    public void actualizar(String nombre, boolean activo) {
        this.nombre = nombre;
        this.activo = activo;
    }

    public void setEquivaleInvitados(boolean equivaleInvitados) {
        this.equivaleInvitados = equivaleInvitados;
    }

    public Short getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean isActivo() {
        return activo;
    }

    public boolean isEquivaleInvitados() {
        return equivaleInvitados;
    }
}
