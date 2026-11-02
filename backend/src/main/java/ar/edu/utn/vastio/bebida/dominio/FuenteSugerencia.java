package ar.edu.utn.vastio.bebida.dominio;

/**
 * De dónde salieron las botellas por asistente de la orden sugerida (docs/sprint-4.md, decisión 13), en el orden en que
 * se prueban. Mismos valores que el CHECK {@code ck_orden_preparacion_fuente}.
 */
public enum FuenteSugerencia {
    /** Los eventos cerrados del mismo tipo de evento, si son al menos {@code MINIMO_EVENTOS_DEL_TIPO}. */
    HISTORICO_TIPO,
    /** Las proporciones de consumo cargadas para el tipo de evento en Parámetros. */
    PROPORCIONES,
    /** Los eventos cerrados de todos los tipos. */
    HISTORICO_GENERAL,
    /** Ningún evento cerrado ni proporción: la orden sale vacía y Compras carga las bebidas. */
    SIN_DATOS
}
