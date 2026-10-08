package ar.edu.utn.vastio.bebida.dominio;

/**
 * Tipo de ubicación de stock (Sprint 3, decisión 8). Las divisiones internas del depósito no se modelan.
 * El orden es el del circuito: del depósito a la barra.
 */
public enum TipoUbicacion {
    /** Depósito madre: uno solo activo. Recibe los ingresos de mercadería. */
    DEPOSITO,
    /** Depósito de transición, con salón opcional. Recibe por transferencia. */
    TRANSICION,
    /** Barra de un salón: una por salón; Avril, hasta dos. */
    BARRA
}
