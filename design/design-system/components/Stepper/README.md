Selector de cantidad con botones grandes de 52 px, pensado para registrar cajones en la barra o el depósito con una mano.

**Vos pasás:** `label` (la bebida), `unit` ("cajones"), `value` + `onChange` (o `defaultValue`), `min` (0 por defecto), `max` (por ejemplo lo que hay en el depósito), `step`, `hint` y `error`.

- La cantidad se muestra en Jost con cifras tabulares y también se puede tipear.
- Poné `max` cuando exista un tope real y explicalo en `hint` ("Hay 36 cajones en el depósito.").
- Los botones se deshabilitan en los topes; no ocultes el control.
