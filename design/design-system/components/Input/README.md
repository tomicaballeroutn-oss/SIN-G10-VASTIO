Campo de texto con etiqueta arriba, ayuda y error debajo; también sirve como área de texto con `multiline`.

**Vos pasás:** `label` (siempre), `value` / `defaultValue` y `onChange`, `hint`, `error` (texto que dice qué pasó y qué hacer), `icon`, `suffix` (unidad, como "invitados"), `type`, `inputMode`, `optional`, `disabled`, `size` (`md` o `lg`).

- El texto del campo es `body` (16 px): no lo achiques, evita el zoom en teléfonos.
- Marcá como "(opcional)" lo opcional en lugar de asteriscos en lo obligatorio.
- El error se muestra en el campo, con ícono y texto, y reemplaza a la ayuda. Ej.: "Ingresá el monto de la seña para confirmar la reserva."
- No uses el placeholder como etiqueta: desaparece al escribir.
