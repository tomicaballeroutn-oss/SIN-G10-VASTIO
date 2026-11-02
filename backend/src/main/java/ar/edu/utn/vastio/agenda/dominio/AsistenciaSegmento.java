package ar.edu.utn.vastio.agenda.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Asistencia prevista y real de un segmento en un evento de un tipo que la cuenta por partes (egresados). Quién la
 * cambió y cuándo queda en {@code modificacion_evento}.
 */
@Entity
@Table(name = "asistencia_segmento")
public class AsistenciaSegmento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asistencia_id")
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Column(name = "tipo_segmento_id", nullable = false)
    private Short segmentoId;

    /** Null si no se prevé o es el segmento que equivale a los invitados (su previsto es la cantidad de invitados). */
    @Column(name = "cantidad_prevista")
    private Integer cantidadPrevista;

    /** Se carga con el evento Realizado o Cerrado. */
    @Column(name = "cantidad_real")
    private Integer cantidadReal;

    protected AsistenciaSegmento() {
    }

    public AsistenciaSegmento(long eventoId, short segmentoId) {
        this.eventoId = eventoId;
        this.segmentoId = segmentoId;
    }

    public void setCantidadPrevista(Integer cantidadPrevista) {
        this.cantidadPrevista = cantidadPrevista;
    }

    public void setCantidadReal(Integer cantidadReal) {
        this.cantidadReal = cantidadReal;
    }

    public Long getId() {
        return id;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public Short getSegmentoId() {
        return segmentoId;
    }

    public Integer getCantidadPrevista() {
        return cantidadPrevista;
    }

    public Integer getCantidadReal() {
        return cantidadReal;
    }
}
