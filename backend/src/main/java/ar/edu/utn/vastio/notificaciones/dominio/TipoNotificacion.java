package ar.edu.utn.vastio.notificaciones.dominio;

/**
 * Tipos de aviso. Mismos valores que el CHECK {@code ck_notificacion_tipo}.
 */
public enum TipoNotificacion {
    EVENTO_NUEVO,
    SENA,
    CONTRATO,
    CONFIRMACION,
    MODIFICACION,
    REPROGRAMACION,
    CANCELACION,
    PLANNER_ASIGNADA,
    ALERTA_STOCK
}
