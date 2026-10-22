# Sprint 4 — Servicios, vista de cocina y seña (tareas de Berni)

Cuatro tareas, cada una en su rama y su PR. El comprobante de la seña queda aparte: se suma solo si el equipo lo decide.

| Orden | Tarea | Rama | Base |
|---|---|---|---|
| 1 | M-09 · Más detalle al cargar los servicios (UI-13) | `berni/A5-detalle-servicios` | `main` |
| 2 | A7-02 + mejora 5 · Consultar vista de cocina (UI-23) | `berni/A7-consultar-vista-cocina` | la 1 |
| 3 | M-02 · Seña en dólares | `berni/A3-sena-en-dolares` | `main` |
| 4 | M-07 · Comprobante interno de la seña (opcional) | `berni/A3-comprobante-sena` | la 3 |

## 1 · M-09 · Servicios con renglones

**Qué cambia.** Dentro de cada categoría se cargan renglones libres con cantidad opcional («Celíaco · 2», «Sin lactosa · 3») y un detalle libre. Todo es opcional y nada es predefinido.

**Decisiones (Berni, 22/10/2026).**

1. **Sin opciones predefinidas.** Se descartó la lista de opciones por categoría con «Otro» (decisión del 18/09): la carga tiene que ser lo más flexible posible. Por eso no hay ABM de opciones en Parámetros.
2. **Categorías.** Quedan activas Menú, Menús especiales, Bodega, Tipo de barra y Otros (nueva). Se dan de baja Recepción, Postre, After, Técnica, Mobiliario y Extras, sin borrar lo cargado: la ficha lo sigue mostrando como «Categoría dada de baja». «Plato principal» se renombra a «Menú» y conserva lo cargado.
3. **Requisito para confirmar.** Se mantiene: Menú y Tipo de barra hacen falta para confirmar el evento.
4. **Qué ve Cocina.** De las activas, Menú y Menús especiales (`visible_en_cocina`). Bodega, Tipo de barra y Otros, no; se cambia desde Parámetros. Recepción, Postre y After quedan visibles en cocina para que un evento con algo cargado ahí no lo pierda. Extras pasa a visible en cocina, como en el prototipo UI-23 y la historia A7-02; como está dada de baja, solo se nota si se la reactiva.
5. **Observaciones internas.** Siguen como texto libre del evento, que Cocina no ve.
6. **Lo ya cargado.** El texto de cada categoría pasa a ser su detalle (`servicio_contratado.descripcion`, ahora opcional). No se pierde nada.

**Modelo.** V10: `servicio_renglon` (texto y cantidad por renglón), `servicio_contratado.descripcion` nullable y el ajuste de categorías. Ver el diccionario de datos.

**Historial.** Cada categoría que cambia queda en `modificacion_evento` como `servicio.<categoría>`, con lo cargado en una línea: «Celíaco (2), Sin lactosa (3) · Avisar en la mesa 4».
