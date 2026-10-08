package ar.edu.utn.vastio.bebida.aplicacion;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.bebida.dominio.MovimientoStock;
import ar.edu.utn.vastio.bebida.infraestructura.MovimientoStockRepository;

/**
 * Único camino para mover mercadería: inserta el asiento y actualiza el saldo de origen y destino en la misma
 * transacción que quien lo llama (ingreso, carga inicial, ajuste y, más adelante, entregas y devoluciones).
 * Nunca rechaza un movimiento por falta de saldo teórico (regla 5); quien lo llama decide si avisa.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class MovimientoService {

    private final MovimientoStockRepository movimientos;

    public MovimientoService(MovimientoStockRepository movimientos) {
        this.movimientos = movimientos;
    }

    /**
     * Saldos después del movimiento, en botellas: null donde el movimiento no tiene origen o destino.
     */
    public record Resultado(MovimientoStock movimiento, BigDecimal saldoOrigen, BigDecimal saldoDestino) {
    }

    public Resultado registrar(MovimientoStock movimiento) {
        MovimientoStock guardado = movimientos.save(movimiento);
        long bebida = guardado.getBebida().getId();
        BigDecimal saldoOrigen = guardado.getOrigen() == null ? null
                : movimientos.sumarAlSaldo(guardado.getOrigen().getId(), bebida, guardado.getCantidad().negate());
        BigDecimal saldoDestino = guardado.getDestino() == null ? null
                : movimientos.sumarAlSaldo(guardado.getDestino().getId(), bebida, guardado.getCantidad());
        return new Resultado(guardado, saldoOrigen, saldoDestino);
    }
}
