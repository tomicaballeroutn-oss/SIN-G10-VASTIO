package ar.edu.utn.vastio.bebida.dominio;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * Consumo de un evento por bebida, congelado al cierre (regla 7): no se recalcula después. Solo cantidades, en botellas:
 * el sistema no guarda precios ni costos. El evento es de otro módulo y se guarda por id.
 */
@Entity
@Immutable
@Table(name = "consumo_evento")
@IdClass(ConsumoEvento.Clave.class)
public class ConsumoEvento {

    /** Clave compuesta (evento_id, bebida_id). */
    public static class Clave implements Serializable {
        private Long eventoId;
        private Long bebida;

        public Clave() {
        }

        public Clave(Long eventoId, Long bebida) {
            this.eventoId = eventoId;
            this.bebida = bebida;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Clave c && Objects.equals(eventoId, c.eventoId) && Objects.equals(bebida, c.bebida);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventoId, bebida);
        }
    }

    @Id
    @Column(name = "evento_id")
    private Long eventoId;

    @Id
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "bebida_id")
    private Bebida bebida;

    @Column(name = "cantidad_entregada", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidadEntregada;

    @Column(name = "cantidad_devuelta", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidadDevuelta;

    @Column(name = "cantidad_remanente", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidadRemanente;

    @Column(name = "cantidad_consumida", nullable = false, precision = 10, scale = 2)
    private BigDecimal cantidadConsumida;

    protected ConsumoEvento() {
    }

    public ConsumoEvento(long eventoId, Bebida bebida, BigDecimal entregada, BigDecimal devuelta, BigDecimal remanente,
            BigDecimal consumida) {
        this.eventoId = eventoId;
        this.bebida = bebida;
        this.cantidadEntregada = entregada;
        this.cantidadDevuelta = devuelta;
        this.cantidadRemanente = remanente;
        this.cantidadConsumida = consumida;
    }

    public Long getEventoId() {
        return eventoId;
    }

    public Bebida getBebida() {
        return bebida;
    }

    public BigDecimal getCantidadEntregada() {
        return cantidadEntregada;
    }

    public BigDecimal getCantidadDevuelta() {
        return cantidadDevuelta;
    }

    public BigDecimal getCantidadRemanente() {
        return cantidadRemanente;
    }

    public BigDecimal getCantidadConsumida() {
        return cantidadConsumida;
    }
}
