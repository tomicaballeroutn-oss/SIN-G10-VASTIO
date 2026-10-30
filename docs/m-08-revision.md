# M-08 — Notificaciones e integración con Google Calendar

## Alcance y contrato

M-08 aplica un color por tipo al ícono y al indicador sin leer. El mensaje, el trazo del ícono y el texto accesible «Sin leer» conservan el significado sin depender del color.

Después de incorporar main con M-06 (merge de #51: `18501bbd`), los diez tipos de la API frontend coinciden con el enum del backend. `ICONO` y `COLOR` son mapas exhaustivos de `TipoNotificacion`; `CALENDAR_PENDIENTE` ya es un tipo vigente, sin unión anticipada ni parche separado.

Calendar usa `calendar-clock` y el token `--warning`, que tiene variantes clara y oscura. Comparte trazo con reprogramación: los mensajes distinguen ambos avisos. Abrir un aviso Calendar conserva la lectura local, el contador, el cierre del panel y la navegación a la ficha. Leerlo no publica ni actualiza Google y no equivale a resolver el pendiente de exportación. Se preservan los permisos, la deduplicación y la resolución de notificaciones de M-06.

Los cambios de actualización no modifican el cliente Google, las migraciones ni la autorización de main. La auditoría histórica incorporada por #64 pertenece a main y no forma parte del diff de M-08.

## Contraste e íconos

Los colores por tipo tienen variantes en ambos temas. El selector de hijo directo `> .v-icon` alcanza el SVG real de `Icon`, cuyo stroke usa currentColor. El tema sistema con SO oscuro aplica `data-theme="dark"`.

La medición usa luminancia relativa sRGB y `(Lmayor + 0.05)/(Lmenor + 0.05)`. Se exige contraste mínimo 3:1 sin redondear antes de comparar, sobre fondo efectivo, leído/sin leer y hover.

| Tema | Mínimo medido en Chrome, escritorio y teléfono |
|---|---:|
| Claro | 3.513:1 |
| Oscuro | 3.501:1 |
| Sistema con SO oscuro | 3.501:1 |

El E2E recibe los **diez tipos reales desde su API simulada**, incluido Calendar, con veinte avisos (leído y sin leer por tipo). No clona HTML para anticipar tipos futuros. Las 240 mediciones están en [contrastes.json](m-08-capturas/contrastes.json).

Capturas renovadas en `m-08-capturas/`: claro, oscuro y sistema, escritorio y teléfono. Solo para capturar se expande la lista del fixture después de las aserciones; la aplicación conserva su scroll.

![Claro](m-08-capturas/escritorio-claro.png)
![Oscuro](m-08-capturas/escritorio-oscuro.png)

No se ejecutó un simulador dedicado de daltonismo y no se afirma que los tonos cálidos sean distinguibles por sí solos. El contenido y los indicadores adicionales conservan su significado sin usar el color como único canal.

## Regresiones y validación

- `PanelNotificaciones.test.tsx`: lectura, navegación, paginación, contador, perfiles combinados de Cocina y avisos Calendar de Dirección/Coordinación; abrir Calendar solo envía el POST de lectura y no realiza escrituras de exportación.
- `contraste.test.ts`: 22 casos sobre tokens reales, superficies y mezclas sin leer.
- `Calendar.test.tsx`: permisos, estados y operaciones manuales de M-06 con API simulada.
- `e2e/notificaciones-colores.spec.ts`: seis escenarios en Chrome escritorio/teléfono, claro/oscuro/sistema oscuro, diez tipos, color calculado, SVG, mensajes y leído/sin leer.
- La suite backend de main comprueba que leer avisos no resuelve pendientes Calendar y conserva deduplicación, roles e integración desactivada. Las pruebas externas de Google permanecen optativas y no se habilitan en este cierre.

Comandos: `npm ci`, `npm run lint`, `npm run build`, `npm test`; E2E de colores con API simulada y backend `mvnw verify` con PostgreSQL descartable. Los resultados completos, el HEAD exacto, las limitaciones locales y CI se registran en la descripción y conversación de #52. Los E2E simulados no sustituyen aceptación contra un despliegue real.
