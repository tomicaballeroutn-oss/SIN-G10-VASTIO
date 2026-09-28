Tabla para leer y comparar filas, con encabezado fijo, números alineados a la derecha y scroll horizontal en pantallas angostas.

**Vos pasás:** `columns` (`[{key,header,numeric?,render?,width?}]`), `rows`, `caption` (para lectores de pantalla), `dense`, `onRowClick` y `empty` (texto sin resultados).

- Marcá `numeric: true` en toda columna de cantidades, fechas u horas: usa numerales tabulares y alinea a la derecha.
- Ponele unidad a la cabecera ("Depósito, cajones") o al valor; no dejes cantidades sueltas.
- La fila clickeable se opera con Enter. Para más de una acción por fila, usá un menú de `IconButton`, no varias filas clickeables.
- En teléfono, priorizá tres columnas y mostrá el resto en el detalle; para listas ricas usá `EventCard` o `StockLevel`.
