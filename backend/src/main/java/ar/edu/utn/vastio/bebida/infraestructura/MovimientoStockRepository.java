package ar.edu.utn.vastio.bebida.infraestructura;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.bebida.dominio.MovimientoStock;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {

    @Query("SELECT m FROM MovimientoStock m JOIN FETCH m.bebida b JOIN FETCH b.unidad WHERE m.ingresoId IN :ingresos ORDER BY m.id")
    List<MovimientoStock> deIngresos(Collection<Long> ingresos);

    /**
     * La carga inicial de la ubicación: sus INVENTARIO_INICIAL y los AJUSTE que los corrigen, en orden.
     */
    @Query("""
            SELECT m FROM MovimientoStock m JOIN FETCH m.bebida
            WHERE (m.destino.id = :ubicacionId OR m.origen.id = :ubicacionId)
              AND (m.tipo = ar.edu.utn.vastio.bebida.dominio.TipoMovimiento.INVENTARIO_INICIAL
                   OR m.movimientoCorregidoId IN (SELECT i.id FROM MovimientoStock i
                       WHERE i.tipo = ar.edu.utn.vastio.bebida.dominio.TipoMovimiento.INVENTARIO_INICIAL AND i.destino.id = :ubicacionId))
            ORDER BY m.id""")
    List<MovimientoStock> cargaInicial(short ubicacionId);

    /**
     * Si la ubicación ya tiene un movimiento que no es de su carga inicial (un ingreso, una entrega, un recuento): desde
     * ahí la carga inicial queda cerrada (docs/sprint-3.md, decisión 12).
     */
    @Query("""
            SELECT count(m) > 0 FROM MovimientoStock m
            WHERE (m.destino.id = :ubicacionId OR m.origen.id = :ubicacionId)
              AND m.tipo <> ar.edu.utn.vastio.bebida.dominio.TipoMovimiento.INVENTARIO_INICIAL
              AND (m.movimientoCorregidoId IS NULL OR m.movimientoCorregidoId NOT IN (SELECT i.id FROM MovimientoStock i
                       WHERE i.tipo = ar.edu.utn.vastio.bebida.dominio.TipoMovimiento.INVENTARIO_INICIAL AND i.destino.id = :ubicacionId))""")
    boolean tieneOtrosMovimientos(short ubicacionId);

    /** Saldo de la bebida en la ubicación, bloqueando la fila hasta el final de la transacción (recuento). */
    @Query(nativeQuery = true, value = "SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id = :ubicacionId AND bebida_id = :bebidaId FOR UPDATE")
    Optional<BigDecimal> saldoBloqueado(short ubicacionId, long bebidaId);

    /**
     * Suma {@code delta} (positivo o negativo) al saldo de la bebida en la ubicación y devuelve el saldo nuevo. Una sola
     * sentencia: dos movimientos simultáneos de la misma bebida y ubicación se suman, no se pisan. El saldo puede quedar
     * negativo (regla 5): no hay CHECK ≥ 0.
     */
    @Query(nativeQuery = true, value = """
            INSERT INTO stock_ubicacion (ubicacion_id, bebida_id, cantidad, fecha_actualizacion)
            VALUES (:ubicacionId, :bebidaId, :delta, now())
            ON CONFLICT (ubicacion_id, bebida_id)
            DO UPDATE SET cantidad = stock_ubicacion.cantidad + EXCLUDED.cantidad, fecha_actualizacion = now()
            RETURNING cantidad""")
    BigDecimal sumarAlSaldo(short ubicacionId, long bebidaId, BigDecimal delta);
}
