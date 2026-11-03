-- =====================================================================
-- Vastio · V7 · Consumo de la barra (Sprint 3, operación del evento).
-- =====================================================================

-- Con la devolución, lo consumido sale de la barra (origen la barra, sin destino, imputado al evento): así el saldo de
-- la barra queda igual a lo que de verdad hay en ella, el remanente, y el evento siguiente arranca de ahí
-- (docs/sprint-3-operacion.md, decisiones 12 y 13). Es un asiento de cuentas: no se mueve mercadería.
ALTER TABLE movimiento_stock DROP CONSTRAINT ck_movimiento_stock_tipo;
ALTER TABLE movimiento_stock ADD CONSTRAINT ck_movimiento_stock_tipo CHECK (tipo IN ('INGRESO', 'INVENTARIO_INICIAL',
    'SALIDA_BARRA', 'RETIRO_ADICIONAL', 'DEVOLUCION', 'REMANENTE', 'CONSUMO', 'AJUSTE', 'MERMA'));

ALTER TABLE movimiento_stock DROP CONSTRAINT ck_movimiento_stock_3;
ALTER TABLE movimiento_stock ADD CONSTRAINT ck_movimiento_stock_3 CHECK (
    tipo NOT IN ('SALIDA_BARRA', 'RETIRO_ADICIONAL', 'DEVOLUCION', 'REMANENTE', 'CONSUMO') OR evento_id IS NOT NULL);
