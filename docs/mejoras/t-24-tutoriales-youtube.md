# T-24 — Tutoriales de YouTube

Implementación sobre `origin/main`, después de comprobar la integración del PR #112
(merge `4a5f16a`). Rama `codex/t24-tutoriales-youtube`.

## Catálogo y coherencia editorial

| Tutorial | ID | Artículos | Dimensiones originales |
|---|---|---|---|
| V01 | HaqrhVjUWCg | utilizar-agenda | 1898 × 1170 |
| V02 | _IvVyLp1BkI | ficha-historial | 1898 × 1170 |
| V03 | I7zpEWDAP-I | pre-reservas-senas | 1898 × 1170 |
| V04 | kpOcrk667Ic | confirmacion-reprogramacion | 1898 × 1170 |
| V05 | CnYJ7_p4fmg | existencias, ingresos-ajustes | 1898 × 1170 |
| V06 | NVNaKzVUP3w | retiros, devoluciones | 1080 × 2162 |
| V07 | qygweTi5ZvQ | cierre | 1080 × 1368 |
| V08 | bWLLdKbdjXw | ocupacion, estadisticas-consumo | 1898 × 1170 |

Se contrastaron los títulos y descripciones públicos de los ocho videos con las
descripciones e instrucciones del catálogo existente. Las asociaciones son temáticas
y complementan las instrucciones. V03 está centrado en registrar una pre-reserva;
su artículo también explica las señas. V04 describe confirmación y gestión desde la
ficha; no se presupone que sustituya todas las instrucciones de reprogramación.
V06 y V07 describen correctamente el registro del consumo durante la devolución
y su consolidación posterior en el cierre, respectivamente.

No se modificaron las explicaciones funcionales ni las reglas de consumos y cierre.
Los artículos compartidos conservan sus permisos y el filtrado de secciones por rol.
Los videos son públicos en YouTube; la autorización de Vastio sigue aplicada al artículo.

## Reproductor

Se aceptan enlaces HTTPS de `youtu.be`, `www.youtu.be`, `youtube.com`,
`www.youtube.com` y `m.youtube.com`, con las rutas de video corto, `watch?v=ID`
y `shorts/ID` correspondientes. Se valida el ID y se genera exclusivamente una
inserción `https://www.youtube-nocookie.com/embed/ID`. No se reutiliza HTML externo.
Se conservan los archivos HTTPS directos MP4, WebM y OGV.

El iframe tiene título accesible, carga diferida, permiso de pantalla completa,
política de referencia explícita y enlace alternativo «Abrir video en YouTube».
No usa autoplay. Los artículos sin video no crean reproductores.
La proporción se configura desde metadatos comprobados, con ancho limitado al
espacio disponible y altura máxima equivalente al 75 % de la ventana sin deformar
el video. Los Shorts no se tratan automáticamente como videos 9:16.

## Evidencia y límites de verificación

Verificación realizada el 17/18 de noviembre de 2026, con Chrome instalado y app local.

- YouTube devolvió `playabilityStatus.status = OK` y `playableInEmbed = true`
  para los ocho IDs. Las dimensiones de la tabla provienen de los formatos de
  video de `ytInitialPlayerResponse.streamingData.adaptiveFormats`, no de miniaturas
  ni de las dimensiones predeterminadas de oEmbed.
- Una comprobación automatizada independiente abrió los ocho tutoriales dentro
  de sus artículos contra YouTube real, pulsó «Reproducir video» y verificó
  `currentTime > 2`, `paused = false` y dimensiones de video distintas de cero.
  No se simularon los reproductores en esta comprobación. Sí se simuló la sesión
  de Vastio; no se necesitó backend ni se realizaron operaciones de negocio.
- Dimensiones efectivamente decodificadas al iniciar reproducción: 778 × 480
  para V01–V05 y V08, 320 × 640 para V06, 720 × 912 para V07.
- Se inspeccionaron visualmente capturas reales de V06 y V07 dentro de sus artículos.
  Esto no representa una revisión editorial completa de todos los fotogramas,
  narraciones ni la reproducción hasta el final de los ocho videos.
- Las pruebas Playwright del repositorio simulan sesión y reproductor externo para
  comprobar integración, enlaces, teclado, permisos, proporciones, temas y tamaños
  de pantalla de forma reproducible. No demuestran reproducción real.
- La validación real se limita al inicio de reproducción en Chrome de escritorio.
  No equivale a una prueba manual exhaustiva en teléfonos físicos ni en producción.
  La reproducción sigue dependiendo de YouTube y de la conexión del usuario.

No se descargaron archivos MP4 ni se incorporaron medios o dependencias al repositorio.

## Pruebas automatizadas

- `npm test -- src/features/ayuda`: 108 pruebas aprobadas en cinco archivos.
- Playwright `e2e/ayuda.spec.ts`: 12 pruebas aprobadas con Chrome, en los proyectos
  de escritorio y teléfono. Incluyen acceso por rol, artículos compartidos,
  artículos sin video, proporciones sin desbordamiento y alternativa por teclado.
- `npm run lint` y `npm run build` (TypeScript y Vite).
  El build conserva la advertencia preexistente por un chunk de más de 500 kB.
