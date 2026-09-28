Botón de acción con tres variantes: `solid` (con fondo), `outline` (contorno) y `text` (solo subrayado).

**Vos pasás:** `children` (verbo + objeto: "Confirmar seña"), `variant`, `tone` (`brand` o `danger`), `size` (`sm` 36 px, `md` 44 px, `lg` 52 px), `icon` / `iconEnd` opcionales, `block` para ancho completo en teléfono, `loading`, `disabled`, y `onClick` o `href`.

- Un solo `solid` de tono `brand` por vista: es la acción principal. Las secundarias son `outline`; las terciarias y las de menú, `text`.
- Usá `tone="danger"` solo para acciones que liberan, cancelan o dan de baja, y nombrá el objeto ("Cancelar evento").
- En barra y depósito usá `size="lg"`; `sm` es solo para tablas densas en escritorio.
- Con `loading` el botón se bloquea y muestra un aro; no lo reemplaces por un texto "Cargando…".
- No inventes variantes nuevas ni cambies alturas: son tres variantes y tres tamaños.
