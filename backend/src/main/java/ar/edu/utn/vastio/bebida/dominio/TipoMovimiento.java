package ar.edu.utn.vastio.bebida.dominio;

/**
 * Tipo de asiento de mercadería, con los mismos valores que {@code ck_movimiento_stock_tipo}. El sentido lo dan el
 * origen y el destino; la cantidad siempre es positiva y en botellas.
 */
public enum TipoMovimiento {
    INGRESO,
    INVENTARIO_INICIAL,
    SALIDA_BARRA,
    RETIRO_ADICIONAL,
    DEVOLUCION,
    REMANENTE,
    AJUSTE,
    MERMA
}
