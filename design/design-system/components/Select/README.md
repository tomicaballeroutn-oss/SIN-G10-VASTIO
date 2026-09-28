Lista desplegable nativa con la misma altura, etiqueta y errores que `Input`.

**Vos pasás:** `label`, `options` (`[{value,label,disabled?}]`), `value` / `defaultValue` y `onChange`, `placeholder` (opción inicial deshabilitada), `hint`, `error`, `size`.

- Usala para elegir una opción entre 3 y unas 12 (salón, turno, tipo de evento). Con menos de 3, usá botones o casillas; con muchas más, un campo de búsqueda.
- Es un `<select>` nativo a propósito: en teléfonos abre el selector del sistema, que es lo más cómodo con guantes o de noche.
