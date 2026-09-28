Vastio es el sistema web interno de un complejo de tres salones de eventos (Avril, Club de Campo y Santa Bárbara) con dos módulos: **Agenda** (Módulo A: calendario único, pre-reserva, seña, confirmación) y **Bebidas** (Módulo B: depósito, barras, entregas, devoluciones y consumo real por evento). Lo usan vendedoras, administración, planners, el encargado de compras y las encargadas de barra: personas sin formación informática, muchas veces de noche, de pie y desde una tableta o un teléfono. Todo el sistema de diseño se decide para ese usuario: texto grande y claro, botones y zonas táctiles amplios, palabras del negocio y nada que haya que interpretar.

## Fundamentos de contenido

- Hablá de "vos", en imperativo rioplatense: "Registrá la seña", "Elegí un turno", "Volvé a intentarlo". Nunca "usted" ni "tú".
- Usá el vocabulario del negocio tal como lo dicen en el salón: pre-reserva (con guion), seña, señado, confirmado, planner, barra, depósito, entrega, devolución, retiro adicional, remanente, consumo real, orden de preparación, ajuste. No traduzcas al inglés ni inventes sinónimos técnicos ("booking", "stock in", "entidad").
- Escribí en minúsculas salvo la primera letra (sentence case) en botones, títulos, etiquetas y chips. "Vastio" lleva mayúscula inicial en el texto; "VASTIO" en versales solo existe como arte del logo.
- El botón dice el verbo y el objeto: "Confirmar seña", "Entregar a barra", "Registrar devolución", "Cancelar evento". Nunca "Aceptar", "OK" ni "Enviar". Una acción destructiva nombra lo que destruye y usa `tone="danger"`.
- Todo error dice qué pasó y qué hacer, con palabras del negocio (RNF-DIS-03): "Esa fecha ya está tomada. Elegí otro salón, otra fecha u otro turno." Nunca códigos ni términos técnicos ("Error 409", "constraint").
- Mostrá quién y cuándo en cada acción que compromete o libera una fecha, y en cada movimiento de mercadería (RNF-DIS-02, RNF-SEG-03): usá `Actor` con el formato "Lucía Ferreyra · apartó la fecha · hoy 14:32".
- Escribí la conciliación de bebida como atribución de costos, no como vigilancia: decí "consumo real", "quedó registrado", "se corrige con un ajuste". Evitá "faltante", "pérdida", "sospechoso" o "diferencia detectada" salvo que exista una merma declarada. El sistema respalda a quien retira mercadería, no lo acusa.
- Fechas: "sáb 26 sep", "26/09" en tablas; horas en 24 h ("21:00"); decimales con coma ("3,4"); miles con punto ("1.250"). Toda cantidad de bebida lleva su unidad: "36 cajones", no "36".
- No uses emoji. Los íconos vienen de `Icon`.

Ejemplos de tono: "Seña registrada. El evento pasó a Señado." · "Todavía no hay entregas para este evento." · "Ya hay 6 cajones de Fernet comprometidos por la agenda." · "No editable · se corrige con un ajuste."

## Fundamentos visuales

**Color.** El lienzo de toda pantalla y documento es `canvas` (#e6e2dd); las tarjetas y paneles van en `surface` con un filo `line`; lo que flota va en `surface-raised`; lo hundido (encabezados de tabla, pistas) en `surface-sunken`. El azul de marca `brand` (#153e6c) es el color de acción: un solo botón sólido por vista, la pestaña activa, el ítem de navegación activo. Usá `brand-ink` para links y texto de marca, `brand-tint` para selecciones suaves. El segundo color, latón (`accent`), viene de los destellos del logo: usalo con cuentagotas (marcador de "hoy", contador de novedades, ícono `sparkles`) y nunca para comunicar un estado. Texto: `ink` para lo principal, `ink-muted` para ayudas y metadatos; no inventes grises intermedios. Los tonos semánticos `success`, `warning`, `danger` e `info` traen su familia (`*-tint`, `*-ink`, `on-*`) y siempre van acompañados de un ícono y una palabra. Los tres colores de salón (`salon-avril`, `salon-club`, `salon-santa-barbara`) son identidad heredada del calendario compartido de la organización: aparecen como punto, pastilla o celda, siempre junto al nombre del salón, y jamás significan un estado.

**Temas.** El tema claro (`data-theme="light"`) es el predeterminado. El tema oscuro (`data-theme="dark"`) existe para la operación nocturna en depósito y barras (jornada de 20:00 a 06:00): activalo por preferencia del usuario o del dispositivo, y usá las variantes "reverse" del logo. Todo texto cumple 4,5:1 sobre las superficies que su token nombra en ambos temas, y todo borde, foco o ícono con significado cumple 3:1.

**Tipografía.** Poppins para todo lo funcional: títulos H1 a H4, texto, botones, tablas y formularios (`h1`, `h2`, `h3`, `h4`, `body`, `body-sm`, `label`, `caption`, `button`). Jost, la geométrica libre más cercana a las letras del logo, para tres usos y nada más: `display` y `display-sm` en momentos de marca (login, portadas de reporte), `overline` (versales con espaciado ancho, eco del wordmark) sobre títulos y secciones, y los numerales tabulares `stat`, `numeral` y `numeral-sm` para cantidades, fechas y horas, porque Poppins no tiene cifras tabulares y las columnas de cajones no alinearían. El texto de entrada nunca baja de `body` (16 px, evita el zoom en teléfonos) y nada baja de `caption` (12 px). Un solo `h1` por pantalla.

**Espaciado, radios y sombras.** Base de 4 px (`space-1` a `space-20`). Alturas de control: `control-md` (44 px) es el mínimo táctil y el estándar; `control-sm` (36 px) solo en tablas densas de escritorio; `control-lg` (52 px) para las pantallas de barra y depósito. Radios: `radius-md` en botones e inputs, `radius-lg` en tarjetas, `radius-xl` en diálogos, `radius-pill` en chips. Separá con `line` antes que con sombra; las sombras (`shadow-xs` a `shadow-lg`) son solo para lo que flota o se puede abrir.

**Layout.** Diseñá primero para teléfono (< 640 px), luego tableta (640–959) y escritorio (≥ 960). En escritorio, `Nav` en barra lateral (`sidebar-w`); en teléfono y tableta, `Nav` inferior con cinco ítems como máximo. El contenido no supera `content-max` (1200 px). Márgenes de página: `space-4` en teléfono, `space-6` desde tableta. Un formulario es una sola columna en teléfono y hasta dos columnas desde tableta; la etiqueta va arriba del campo y el error debajo.

**Foco, estados y movimiento.** Todo elemento interactivo muestra el anillo `focus-ring` (2 px sólido con separación de 2 px). Hover aclara u oscurece un paso (`brand-hover`, `brand-tint`); presionado va un paso más (`brand-pressed`); deshabilitado usa `opacity-disabled`; cargando reemplaza el ícono por un aro giratorio y bloquea el botón. La transición es de 120 ms (controles) o 200 ms (paneles) con `cubic-bezier(.2,.7,.2,1)`; respetá `prefers-reduced-motion`. No hay animación decorativa.

**Cómo se lee la agenda.** Cada día muestra sus seis unidades comercializables en una grilla de 3 × 2: la columna es el salón (Avril, Club de Campo, Santa Bárbara, siempre en ese orden), la fila superior es mediodía y la inferior es noche. El estado se lee por relleno y no solo por color: vacío = disponible, contorno punteado = pre-reserva, relleno claro = señado, relleno sólido = confirmado, sólido con anillo = realizado, rayado = bloqueado. Una fecha cancelada vuelve a verse como disponible.

## Logo

El logo es una "V" de cinta con tres destellos y el wordmark VASTIO en versales muy espaciadas. Los archivos están en el grupo Logos: `vastio-logo` (marca + wordmark, sobre claro), `vastio-mark` (solo la V con los destellos, sobre claro) y sus versiones `-reverse` en `sand-100` para fondos `brand`, azules oscuros y el tema oscuro.

- Usá la marca sola (`vastio-mark`) en la barra lateral, favicon, ícono de app y cualquier lugar de menos de 120 px de alto; usá el logo completo en login, portadas y documentos.
- Reservá alrededor del logo un espacio libre de al menos un cuarto de la altura de la marca.
- Altura mínima de la marca: 24 px. No achiques el logo completo por debajo de 120 px de alto: el wordmark deja de leerse.
- Sobre `canvas`, `surface` y `surface-raised` usá las versiones azules; sobre `brand`, `ink` o el tema oscuro, las `-reverse`.
- No recolorees, no estires, no agregues sombra ni contorno, no lo pongas sobre fotos sin contraste, y no reescribas el wordmark con tipografía: usá siempre el archivo. Donde haga falta el nombre como texto, escribí "Vastio" en `display` (Jost).

## Iconografía

Los íconos son de Lucide (licencia ISC): trazo de 2 px, extremos y uniones redondeados, 20 px por defecto (16 en chips y badges, 22 en botones de ícono y navegación inferior). Se usan con `Icon` y heredan el color del texto; nunca los pintes con un color de salón. Acompañá siempre el ícono con una palabra, salvo los universales (cerrar, sumar, restar, chevrones). Cada estado tiene su ícono fijo: pre-reserva `hourglass`, señado `banknote`, confirmado `circle-check`, realizado `badge-check`, cancelado `circle-x`, bloqueado `lock`, disponible `circle-dashed`, stock bajo `triangle-alert`. `sparkles` está reservado para novedades y momentos de marca. No hay ilustraciones ni emoji.

## Accesibilidad

- Contraste: texto 4,5:1, texto de 24 px o más y todo borde, foco o ícono con significado 3:1, en los dos temas.
- Ningún estado se comunica solo con color: cada uno lleva ícono, palabra y, en la agenda, un patrón de relleno. El éxito es un verde azulado (teal) y el peligro un ladrillo, para que se distingan también con daltonismo rojo-verde.
- Objetivos táctiles de 44 × 44 px como mínimo; 52 px en barra y depósito.
- Todo se opera con teclado; `Tabs` y `Nav` usan flechas, `Dialog` cierra con Esc, y las etiquetas de `IconButton` son obligatorias.
- Cada campo con error lo anuncia (`role="alert"`) y lo vincula con el input.
