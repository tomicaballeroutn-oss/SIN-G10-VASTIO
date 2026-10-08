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

## Cambios del Sprint 1

- `SalonTag` acepta `label`: el nombre del salón se edita en Parámetros (UI-06), pero el color sigue saliendo de `salon.codigo`.
- `AgendaGrid` acepta `resaltar`: los filtros de la agenda atenúan las unidades que no coinciden, sin ocultarlas, para que una unidad ocupada nunca parezca disponible.
- `Combobox`: campo con sugerencias (patrón combobox de WAI-ARIA), para buscar clientes al pre-reservar. Muestra en `/_ds`.

## Cambios del Sprint 2

- `SelectorArchivos`: zona para elegir archivos (en el celular abre la cámara o la galería; en la PC, el explorador o arrastrar y soltar) con la lista de los elegidos, su tamaño y un botón para quitar cada uno. Lo usa «Registrar firma de contrato» (UI-12). Muestra en `/_ds`. El sistema de diseño original no tenía un componente para adjuntar archivos.
- `IconButton` acepta `count`: un contador sobre el ícono (avisos sin leer de la campana, UI-22). Es solo visual: el `label` tiene que decir la cantidad.

## Cambios del Sprint 3

- `CantidadEnCajas`: cantidad de bebida cargada en cajas (o packs) y botellas sueltas, que devuelve botellas. Con 1 botella por bulto, un solo contador. La usan el stock mínimo del catálogo y, más adelante, el ingreso, la carga inicial y el ajuste. Muestra en `/_ds`.
- `cantidadLegible`, `enBultos` y `plural` (`cantidad.ts`): «8 cajas y 3 botellas» a partir de las botellas y de las que trae cada bulto. El backend guarda todo en botellas.
- `LectorCodigo` (UI-30): lector de código de barras con la cámara trasera, en Android y en iOS (`@zxing/browser`; la API nativa no está en Safari). Necesita HTTPS: sin conexión segura, sin cámara o sin permiso lo dice y ofrece la carga manual. El mismo código leído varias veces seguidas cuenta una vez. La librería se carga recién al abrirlo. Muestra en `/_ds` («Simular lectura» funciona sin cámara).
- `Dialog`: con un diálogo abierto desde otro (el lector desde el formulario de la bebida), Escape cierra solo el de arriba.
- `StockLevel`: acepta `actions` (botones al pie), `cantidadTexto` («8 cajas y 3 botellas»), el estado `negativo` («Falta registrar un movimiento», en tono de advertencia y sin culpa) y `status={null}` para no mostrar estado (la encargada de barra no lo ve). Sin `comprometido` no dibuja la barra, que solo tiene sentido contra lo comprometido por la agenda.
