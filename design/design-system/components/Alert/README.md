Aviso en línea, con ícono, título y texto, para confirmar algo, advertir o explicar un error.

**Vos pasás:** `tone` (`info`, `success`, `warning`, `danger`), `title`, `children` (el detalle con qué hacer) y `action` opcional (un `Button` `outline` de tamaño `sm`).

- `danger` y `warning` se anuncian a lectores de pantalla como alertas; `info` y `success`, como estado.
- El texto dice qué pasó y qué hacer; nunca solo "Error".
- Un aviso por vez y cerca de lo que lo causó. Para confirmar algo destructivo, usá `Dialog`.
