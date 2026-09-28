Pestañas para cambiar de vista dentro de la misma pantalla: variante `underline` (subrayado) o `segmented` (píldora).

**Vos pasás:** `items` (`[{id,label,icon?,count?}]`), `value` + `onChange` (o `defaultValue`) y `label` (nombre accesible del grupo).

- Usá `underline` para las secciones de una pantalla (Mensual, Semanal, Diaria) y `segmented` para filtros de una lista (Todos, Pre-reservas, Señados).
- Flechas izquierda y derecha cambian de pestaña. Máximo cinco; con más, usá un `Select`.
- No uses pestañas para navegar entre módulos: eso es `Nav`.
