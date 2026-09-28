Línea de tiempo vertical con el historial de un evento o un movimiento: qué cambió, quién y cuándo.

**Vos pasás:** `items` (`[{title, from?, to?, detail?, actor?, action?, at?, icon?, tone?}]`), del más antiguo al más reciente. `from` y `to` son estados y se dibujan con `StatusChip`.

- Es la vista de trazabilidad (RNF-SEG-03): no se edita ni se borra. Una corrección es un ítem nuevo.
- Mostrala detrás de "Ver historial" en el detalle del evento, no en el primer plano.
- El `tone` del punto sigue el del estado al que se llegó.
