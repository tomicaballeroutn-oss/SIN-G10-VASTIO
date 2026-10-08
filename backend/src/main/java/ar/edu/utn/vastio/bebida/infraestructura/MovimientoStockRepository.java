package ar.edu.utn.vastio.bebida.infraestructura;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.bebida.dominio.MovimientoStock;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {

    @Query("SELECT m FROM MovimientoStock m JOIN FETCH m.bebida b JOIN FETCH b.unidad WHERE m.ingresoId IN :ingresos ORDER BY m.id")
    List<MovimientoStock> deIngresos(Collection<Long> ingresos);

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
