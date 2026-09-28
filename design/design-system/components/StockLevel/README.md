Existencias de una bebida en una ubicación frente a lo que necesita la agenda, con barra de nivel y estado.

**Vos pasás:** `name`, `presentacion` ("Caja x 12"), `ubicacion` ("Depósito", "Barra Avril"), `cantidad`, `comprometido` (cajones que ya piden los eventos agendados), `unidad` / `unidadUno` (por defecto "cajones" / "cajón") y opcionalmente `status`.

- Sin `status`, se calcula: sin stock si la cantidad es 0, bajo si no cubre lo comprometido, en stock en otro caso.
- La marca vertical de la barra es lo comprometido por la agenda; el relleno, lo que hay.
- Siempre con unidad. Pasá `comprometido` para que la barra tenga sentido; sin ese dato mostrá una fila de `Table`.
