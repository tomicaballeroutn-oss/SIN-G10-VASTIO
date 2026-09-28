Chip del estado de un evento o del stock, con ícono, palabra y color: disponible, pre-reserva, señado, confirmado, realizado, cancelado, bloqueado, en stock, stock bajo y sin stock.

**Vos pasás:** `status` y, si hace falta, `size="sm"` para listas y líneas de tiempo o `label` para reemplazar el texto.

- Es el único componente que muestra un estado. No dibujes chips propios ni cambies el ícono de un estado.
- Pre-reserva y stock bajo llevan contorno punteado: el patrón ayuda a distinguirlos sin depender del color.
- Los estados del evento siguen la secuencia disponible, pre-reserva, señado, confirmado, realizado; cancelado y bloqueado son salidas.
