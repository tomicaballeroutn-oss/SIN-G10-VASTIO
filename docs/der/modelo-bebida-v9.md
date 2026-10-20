# Relaciones vigentes del módulo B — V9

Este diagrama complementa el diccionario de datos. El PNG `DER_3_bebida.png` es el DER v2 histórico, anterior a V5–V9; no usarlo como esquema vigente. Proveedores y sus dos relaciones se retiraron en V9 por decisión del 19/10/2026. Sus datos históricos se conservan fuera de la base operativa mediante el [procedimiento de resguardo](../eliminacion-proveedores-despliegue.md).

```mermaid
erDiagram
    TIPO_BEBIDA ||--o{ BEBIDA : clasifica
    UNIDAD_MANIPULACION ||--o{ BEBIDA : presenta
    BEBIDA ||--o{ CODIGO_BARRA : identifica
    BEBIDA ||--o{ STOCK_UBICACION : saldo
    UBICACION ||--o{ STOCK_UBICACION : almacena
    UBICACION ||--o{ UBICACION : abastece
    SALON o|--o{ UBICACION : contiene
    UBICACION ||--o{ INGRESO : destino
    USUARIO ||--o{ INGRESO : registra
    INGRESO o|--o{ MOVIMIENTO_STOCK : agrupa
    BEBIDA ||--o{ MOVIMIENTO_STOCK : articulo
    UBICACION o|--o{ MOVIMIENTO_STOCK : origen
    UBICACION o|--o{ MOVIMIENTO_STOCK : destino
    USUARIO ||--o{ MOVIMIENTO_STOCK : registra
    EVENTO o|--o{ MOVIMIENTO_STOCK : imputa
    EVENTO ||--o{ ENTREGA : recibe
    UBICACION ||--o{ ENTREGA : barra
    ENTREGA o|--o{ MOVIMIENTO_STOCK : agrupa
    EVENTO ||--o{ PREPARACION_EVENTO : prepara
    PREPARACION_EVENTO o|--o{ MOVIMIENTO_STOCK : agrupa
    EVENTO ||--o{ CONSUMO_EVENTO : consolida
    BEBIDA ||--o{ CONSUMO_EVENTO : articulo
    EVENTO ||--o{ ORDEN_PREPARACION : propone
    ORDEN_PREPARACION ||--o{ DETALLE_ORDEN_PREPARACION : detalla
    BEBIDA ||--o{ DETALLE_ORDEN_PREPARACION : articulo
    ORDEN_PREPARACION o|--o{ ENTREGA : referencia
    TIPO_EVENTO ||--o{ PROPORCION_CONSUMO : estima
    BEBIDA ||--o{ PROPORCION_CONSUMO : articulo
```

Se muestran las relaciones principales del circuito; el diccionario y las migraciones definen el detalle de columnas, restricciones, motivos, usuarios de apertura/cierre y autorreferencia de correcciones. `movimiento_stock.ingreso_id` sigue vigente y conserva la trazabilidad de ingresos e inventario inicial. Preparación B5-04 es independiente de las órdenes de preparación.
