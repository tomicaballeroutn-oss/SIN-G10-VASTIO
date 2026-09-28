Botón cuadrado de solo ícono para acciones repetidas y conocidas (editar, cerrar, mes anterior).

**Vos pasás:** `icon`, `label` (obligatoria: es el nombre accesible y el tooltip), `variant` (`outline` por defecto, `text` o `solid`), `size` y `tone`.

- Sin `label` no se dibuja bien para lector de pantalla: es un requisito, no una opción.
- Usalo para acciones que la persona ya reconoce; si la acción es nueva o importante, usá `Button` con texto.
- Mantené el tamaño táctil de 44 px (`md`) o 52 px (`lg`); `sm` solo en escritorio.
