# Sistema de diseño (frontend)

Port a TypeScript de `design/design-system`. Las pantallas importan todo desde `src/ds` y usan solo estos componentes; si falta uno, se agrega acá y se muestra en `/_ds`.

- `tokens.css`: tema claro y `[data-theme="dark"]`. Desde la v2 sale del `tokens.json` de «Vastio v2 · Fichero» (ver abajo), no de `design/design-system/tokens.css`.
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

## v2 · Fichero (mejora visual, rama `berni/mejora-visual-fichero`)

Lavado de cara con el sistema de diseño «Vastio v2 · propuesta». Solo cambia lo que cambió en el sistema de diseño; la lógica de las pantallas es la misma.

- **Tokens:** `surface`, `surface-raised` y `surface-sunken` más cálidos (claro y oscuro). `line` más oscura que en el sistema de diseño (`sand-500` en claro, `blue-600` en oscuro): con la del sistema los bordes casi no se veían. Nuevos: `deep`, `deep-raised`, `on-deep`, `on-deep-muted` (riel, barra inferior y ficha destacada), `tab` y `on-tab` (pestaña activa), las medidas de forma (`rail-*`, `tab-*`, `notch-size`, `fillet`) y la paleta base (`blue-*`, `sand-*`, `brass-*`, `teal-*`, `amber-*`, `red-*`), de la que salen los semánticos. Las pantallas usan los semánticos.
- **`componentes.css`:** al final, el bloque «v2 · Fichero» del sistema de diseño (sin los tonos de chip, que ya estaban). En la barra inferior, el círculo del activo descuenta su alto para que la etiqueta quede alineada.
- **`Nav layout="rail"`:** riel flotante de escritorio. Cerrado muestra íconos y el activo es una pestaña unida al borde; abierto, el activo es un recuadro simple; se abre por encima del contenido con el mouse o con el foco del teclado; un clic no lo deja abierto al sacar el cursor (no lo empuja: el marco reserva `rail-w`). Sin las pestañitas de índice (A, B, G) del sistema de diseño: no se entendían. El texto queda en el DOM con el riel cerrado. La lateral clásica (`sidebar`) sigue para «Más».
- **Barra inferior flotante:** mismo componente, estilo nuevo; el activo sube en un círculo del color del lienzo.
- **`Card`:** `tone` (`surface`, `deep`, `brand-tint`, `accent-tint`, `success-tint`, `sunken`), `tab` (solapa) y `notch` (muesca con un `IconButton`). Solapa o muesca, no las dos; como mucho una ficha `deep` por pantalla.
- **`Tabs variant="folder"`:** solapas que bajan a la `Card` que va justo debajo. `segmented` ahora tiene la pastilla activa en azul sólido.
- **`MiniCalendar`:** el mes de un vistazo (`reservado` / `prereserva`). Lo usa el Inicio (UI-04).
- **`Atajo`** (rama `mateo/inicio-tablero`): fila de una lista de atajos, con ícono en círculo (`tone` `brand`, `accent` o `warning`), qué hacer con verbo y sobre qué, y flecha. Lleva a la pantalla donde se hace; no ejecuta la acción. Es «Te toca a vos» en el Inicio.
- **`MiniBarras`** (rama `mateo/inicio-tablero`): barritas verticales para la ficha `deep` (eventos por día de la semana), con `label` para lectores de pantalla. Un cero deja el riel vacío.
- **`Logo reverse`:** siempre la versión clara, para fondos azules (riel y login) en los dos temas.
- **Login (UI-01):** fondo azul de marca (`deep`) con el logo claro y la ficha del formulario. Se probó el relieve de la V del sistema de diseño y se descartó: se veía como una foto de baja calidad.
- **Agenda:** cada día muestra sus seis unidades como puntos (9 px en teléfono, 11 px desde 960 px) con el mismo relleno por estado. Arriba de la grilla, qué salón es cada columna (`AgendaGrid salones`, con los nombres de Parámetros). Qué significa cada relleno va aparte en `AgendaLeyenda`, un machete desplegable: en Agenda y Reprogramar está debajo del detalle del día, abierto desde 1200 px y cerrado en el teléfono. Se sacó «fila superior: mediodía / inferior: noche». `AgendaGrid` ya no tiene `legend`.
- **Riel y contenido:** el riel abierto se superpone al contenido, no lo corre. Abrirse con solo pasar el mouse y empujar la pantalla haría saltar la grilla y el texto en cada roce. Correr el contenido tiene sentido solo con un «fijar abierto» por clic.

## Cambios del Sprint 4

- `Input` acepta `labelHidden`: la etiqueta queda para los lectores de pantalla pero no se ve. Lo usan los renglones de servicios (UI-13, M-09), que tienen un encabezado «Qué / Cantidad» propio.
