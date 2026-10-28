package ar.edu.utn.vastio.bebida.infraestructura;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import java.time.OffsetDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.bebida.dominio.MovimientoStock;
import ar.edu.utn.vastio.bebida.dominio.TipoMovimiento;

public interface MovimientoStockRepository extends JpaRepository<MovimientoStock, Long> {

    /**
     * Una operación de la consulta de movimientos (UI-37): los asientos del mismo ingreso o preparación, o, si no son de
     * uno, del mismo momento; y además del mismo tipo, entrega, usuario, evento, origen, destino, motivo y observación.
     * {@code fechaHora}: la del último asiento. {@code primero} y {@code ultimo}: su id más bajo y más alto.
     */
    interface Operacion {
        TipoMovimiento getTipo();
        OffsetDateTime getFechaHora();
        Long getIngresoId();
        Long getPreparacionId();
        Long getEntregaId();
        Long getUsuarioId();
        Long getEventoId();
        Short getOrigenId();
        Short getDestinoId();
        Short getMotivoId();
        String getObservacion();
        Long getPrimero();
        Long getUltimo();
    }

    /** Una página de operaciones, de la más nueva a la más vieja. Los filtros null no filtran. */
    @Query("""
            SELECT m.tipo AS tipo, max(m.fechaHora) AS fechaHora, m.ingresoId AS ingresoId, m.preparacionId AS preparacionId,
                   m.entregaId AS entregaId, m.usuarioId AS usuarioId, m.eventoId AS eventoId, o.id AS origenId,
                   d.id AS destinoId, m.motivoId AS motivoId, m.observacion AS observacion, min(m.id) AS primero,
                   max(m.id) AS ultimo
            FROM MovimientoStock m LEFT JOIN m.origen o LEFT JOIN m.destino d
            WHERE (:ubicacionId IS NULL OR o.id = :ubicacionId OR d.id = :ubicacionId)
              AND (:bebidaId IS NULL OR m.bebida.id = :bebidaId)
              AND (:eventoId IS NULL OR m.eventoId = :eventoId)
              AND (:tipo IS NULL OR m.tipo = :tipo)
              AND m.fechaHora >= :desde AND m.fechaHora < :hasta
            GROUP BY m.tipo, m.ingresoId, m.preparacionId, m.entregaId, m.usuarioId, m.eventoId, o.id, d.id, m.motivoId,
                     m.observacion, CASE WHEN m.ingresoId IS NULL AND m.preparacionId IS NULL THEN m.fechaHora END
            ORDER BY max(m.fechaHora) DESC, max(m.id) DESC""")
    Slice<Operacion> operaciones(Short ubicacionId, Long bebidaId, Long eventoId, TipoMovimiento tipo, OffsetDateTime desde,
            OffsetDateTime hasta, Pageable pagina);

    /** Los asientos con esos filtros entre dos ids (inclusive), con bebida y ubicaciones. */
    @Query("""
            SELECT m FROM MovimientoStock m JOIN FETCH m.bebida b JOIN FETCH b.unidad
              LEFT JOIN FETCH m.origen o LEFT JOIN FETCH m.destino d
            WHERE (:ubicacionId IS NULL OR o.id = :ubicacionId OR d.id = :ubicacionId)
              AND (:bebidaId IS NULL OR b.id = :bebidaId)
              AND (:eventoId IS NULL OR m.eventoId = :eventoId)
              AND (:tipo IS NULL OR m.tipo = :tipo)
              AND m.id BETWEEN :desdeId AND :hastaId
            ORDER BY m.id""")
    List<MovimientoStock> asientos(Short ubicacionId, Long bebidaId, Long eventoId, TipoMovimiento tipo, long desdeId,
            long hastaId);

    /** Los eventos con algún movimiento, para el filtro de la consulta. */
    @Query("SELECT DISTINCT m.eventoId FROM MovimientoStock m WHERE m.eventoId IS NOT NULL")
    List<Long> eventosConMovimientos();

    @Query("SELECT m FROM MovimientoStock m JOIN FETCH m.bebida b JOIN FETCH b.unidad WHERE m.preparacionId = :id ORDER BY m.id")
    List<MovimientoStock> dePreparacion(long id);

    @Query("SELECT m FROM MovimientoStock m JOIN FETCH m.bebida b JOIN FETCH b.unidad WHERE m.ingresoId IN :ingresos ORDER BY m.id")
    List<MovimientoStock> deIngresos(Collection<Long> ingresos);

    /** Los movimientos de una entrega (salidas, retiros adicionales y, al cerrarla, la devolución), en orden. */
    @Query("""
            SELECT m FROM MovimientoStock m JOIN FETCH m.bebida b JOIN FETCH b.unidad LEFT JOIN FETCH m.origen
            WHERE m.entregaId = :entregaId ORDER BY m.id""")
    List<MovimientoStock> deEntrega(long entregaId);

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

    /**
     * Ancla existente aunque todavía no haya stock. Todos los escritores toman este mismo bloqueo antes de mover.
     * NO KEY UPDATE permite los KEY SHARE de las FK de ingreso/movimiento, evitando upgrades desde bloqueos compartidos.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(nativeQuery = true, value = "SELECT ubicacion_id FROM ubicacion WHERE ubicacion_id = :ubicacionId FOR NO KEY UPDATE")
    Short bloquearUbicacion(short ubicacionId);

    /**
     * Bloquea primero y lee después, en otra sentencia: READ COMMITTED ve lo confirmado por quien nos hizo esperar.
     * No crea una fila de saldo para bloquear. La exclusión se conserva hasta terminar la transacción del recuento.
     */
    default Optional<BigDecimal> saldoBloqueado(short ubicacionId, long bebidaId) {
        bloquearUbicacion(ubicacionId);
        return saldoDe(ubicacionId, bebidaId);
    }

    @Query(nativeQuery = true, value = "SELECT cantidad FROM stock_ubicacion WHERE ubicacion_id = :ubicacionId AND bebida_id = :bebidaId")
    Optional<BigDecimal> saldoDe(short ubicacionId, long bebidaId);

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
