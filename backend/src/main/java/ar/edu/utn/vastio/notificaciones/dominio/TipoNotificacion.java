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
    ALERTA_STOCK,
    /** A la planner: cargar la asistencia real de cierre de un evento de egresados (Sprint 4). */
    ASISTENCIA_REAL
}
