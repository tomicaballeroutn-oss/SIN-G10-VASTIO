package ar.edu.utn.vastio.bebida.dominio;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados de la orden de preparación (docs/maquina-de-estados.md). Mismos valores que el CHECK
 * {@code ck_orden_preparacion_estado}.
 */
public enum EstadoOrden {
    SUGERIDA,
    MODIFICADA,
    CONFIRMADA,
    DESCARTADA,
    CERRADA;

    /** Ocupan el lugar de la orden del evento ({@code ux_orden_evento_vigente}). */
    public static final Set<EstadoOrden> VIGENTES = EnumSet.of(SUGERIDA, MODIFICADA, CONFIRMADA);

    /** Se ajustan y se recalculan; confirmada, primero hay que reabrirla. */
    public static final Set<EstadoOrden> EDITABLES = EnumSet.of(SUGERIDA, MODIFICADA);
}
