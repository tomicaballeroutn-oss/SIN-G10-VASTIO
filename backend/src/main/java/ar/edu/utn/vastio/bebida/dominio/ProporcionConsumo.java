package ar.edu.utn.vastio.bebida.dominio;

import java.math.BigDecimal;

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
 * Botellas por asistente de una bebida en un tipo de evento (docs/sprint-4.md, decisión 14). Alimenta la orden sugerida
 * mientras no hay suficientes eventos cerrados del tipo; sirve para arrancar con datos de eventos anteriores al sistema.
 * El tipo de evento es de configuración y se guarda por id.
 */
@Entity
@Table(name = "proporcion_consumo")
public class ProporcionConsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "proporcion_id")
    private Long id;

    @Column(name = "tipo_evento_id", nullable = false)
    private Short tipoEventoId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "bebida_id")
    private Bebida bebida;

    @Column(name = "botellas_por_asistente", nullable = false, precision = 8, scale = 4)
    private BigDecimal botellasPorAsistente;

    protected ProporcionConsumo() {
    }

    public ProporcionConsumo(short tipoEventoId, Bebida bebida, BigDecimal botellasPorAsistente) {
        this.tipoEventoId = tipoEventoId;
        this.bebida = bebida;
        this.botellasPorAsistente = botellasPorAsistente;
    }

    public void setBotellasPorAsistente(BigDecimal botellasPorAsistente) {
        this.botellasPorAsistente = botellasPorAsistente;
    }

    public Long getId() {
        return id;
    }

    public Short getTipoEventoId() {
        return tipoEventoId;
    }

    public Bebida getBebida() {
        return bebida;
    }

    public BigDecimal getBotellasPorAsistente() {
        return botellasPorAsistente;
    }
}
