# Sistema de diseño (frontend)

Port a TypeScript de `design/design-system`. Las pantallas importan todo desde `src/ds` y usan solo estos componentes; si falta uno, se agrega acá y se muestra en `/_ds`.

- `tokens.css`: copia exacta de `design/design-system/tokens.css` (tema claro y `[data-theme="dark"]`).
- `tipografia.css`: fuentes locales (Poppins y Jost) y las clases `display`, `overline`, `h1`–`h4`, `body*`, `label`, `caption`, `button`, `stat`, `numeral*`, generadas desde `tokens.json`.
- `componentes.css`: `bundle.css` original más los estados agregados en el Sprint 0 (al final del archivo).
- `components/`: los 25 componentes, agrupados como en `bundle.js`.
- `tema.ts`: claro (predeterminado), oscuro o el del dispositivo; la preferencia queda en este navegador.

## Estados agregados en el Sprint 0

El sistema de diseño original no tenía estilo para Contratado, En curso, Cerrado ni Liberada. **Esta es una propuesta para revisar con el equipo:**

| Estado | Código en base | Ícono | Chip | Relleno en la agenda |
|---|---|---|---|---|
| Contratado | `CONTRATADO` | `file-check` | tinte de marca | mitad inferior sólida: entre Señado (claro) y Confirmado (sólido) |
| En curso | `EN_CURSO` | `circle-play` | verde sólido | sólido con un punto central |
| Cerrado | `CERRADO` | `archive` | contorno oscuro | sólido cruzado por una diagonal |
| Liberada | `LIBERADA` | `lock-open` | contorno punteado | vacío, igual que Disponible: no ocupa la unidad (como Cancelado) |

`ESTADO_POR_CODIGO` traduce `evento.estado` al estado del sistema de diseño.
