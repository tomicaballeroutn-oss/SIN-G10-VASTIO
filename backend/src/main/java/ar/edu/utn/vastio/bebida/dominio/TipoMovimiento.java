package ar.edu.utn.vastio.bebida.dominio;

/**
 * Tipo de asiento de mercadería, con los mismos valores que {@code ck_movimiento_stock_tipo}. El sentido lo dan el
 * origen y el destino; la cantidad siempre es positiva y en botellas.
 */
public enum TipoMovimiento {
    PREPARACION,
    INGRESO,
    INVENTARIO_INICIAL,
    SALIDA_BARRA,
    RETIRO_ADICIONAL,
    DEVOLUCION,
    REMANENTE,
    /** Lo consumido en la barra, que sale de ella con la devolución (V7). Asiento de cuentas: no se mueve mercadería. */
    CONSUMO,
    AJUSTE,
    MERMA
}
