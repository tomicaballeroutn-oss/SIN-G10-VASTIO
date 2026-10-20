# B5-04 — Registrar preparación del evento

## Base y alcance

Implementación original: rama `tomi/B5-04-registrar-preparacion`, worktree `vastio-b5-04`, base
`cd5ec780757e425d7f556a32c0562626a31be83f` de `origin/berni/B4-registrar-cierre-evento`.
Al preparar el entorno, main avanzó a `adb54626b218b21abd2be747e50c7e720e6226f9`:
#26 y #27 integrados; #28, #29 y #30 estaban pendientes. La contribución propia es `d03d510c29f4b49e07afe7d719ab310ec61bc0bd`.

Integración local: rama `tomi/integracion-B5-04-main`, worktree `vastio-integracion-b5-04-main`,
desde `origin/main` en `f6b8876469dd5f521415ae5593146299a9438f1c`, que ya contiene #28, #29 y B4-05 mediante #32.
Se aplica únicamente la contribución B5-04 con `cherry-pick -x`; B4-05 forma parte de la base, no del diff.

Se implementa Definición del Producto v4.0 §4.1 B5-04: carga manual, selección/escaneo sin contar automáticamente,
transferencia desde madre a transición configurada en la barra elegida, varias operaciones e historial.
No implementa B5-02/B5-03, órdenes, sugerencias, reservas, costos ni edición histórica.

## API

- `GET /api/v1/eventos/{eventoId}/preparaciones`: evento, madre activo (o null), circuitos de barras activas del salón e historial.
  Se puede consultar después del cierre. No modifica stock.
- `POST` en la misma ruta. Cabecera `Idempotency-Key`: 1–100 letras ASCII, números, guion o guion bajo.
  Cuerpo: `{ "barraId": 2, "destinoId": 7, "renglones": [{ "bebidaId": 10, "cantidad": 13 }] }`.
  Cantidad en botellas enteras positivas; 1–200 renglones, hasta 99.999.999 botellas por renglón, sin duplicados.
  `destinoId` es el revisado por el usuario: debe seguir coincidiendo con la configuración al confirmar.
- 201 para nueva operación, 200 para recuperación, 409 para clave reutilizada con otro contenido, estado o circuito cambiado;
  403 para perfil no autorizado, 404 para evento inexistente. Validaciones de DTO 400; reglas de dominio 422.
- Respuesta de operación: id, evento, barra, origen, destino, usuario, fechaHora y renglones.
- `REGISTRAR_PREPARACION`: Dirección, Coordinación, Administración y Compras. No amplía `OPERAR_BARRA`.

## Persistencia y transacción

V8 agrega `preparacion_evento`, `movimiento_stock.preparacion_id`, tipo `PREPARACION`, índices y restricciones.
Conserva `CONSUMO` y todos los tipos anteriores. No cambia migraciones existentes.
Un asiento por bebida, sin entrega ni ingreso. Cabecera y movimientos son de solo inserción, con trigger en PostgreSQL.
El historial conserva los identificadores utilizados aunque cambie el circuito. Los nombres y presentación del catálogo
se muestran vigentes, como en ingresos; las cantidades en botellas y la auditoría quedan persistidas.

`OperacionEventoService` pertenece a Agenda: bloquea y refresca el evento sin exponer su repositorio a Bebida.
La preparación se ejecuta en una sola transacción READ COMMITTED:

1. Validar forma de la carga y clave; bloquear/refrescar evento.
2. Recuperar por (`evento_id`, `clave`) antes de exigir estado o actividad actual del catálogo.
3. Para una operación nueva: Confirmado o En curso.
4. Bloquear todas las ubicaciones participantes por id ascendente con el protocolo existente;
   refrescar barra, madre y destino; verificar salón, actividad, tipo y abastecimiento.
5. Bloquear las bebidas por id ascendente en lectura pesimista y comprobar todas antes de mover.
6. Insertar cabecera y registrar movimientos con `MovimientoService`, que aplica ambos saldos mediante UPSERT.
7. Si el origen queda negativo, crear una alerta para Compras y Administración, excluyendo al autor según el servicio existente.
   Un solo aviso por operación; si falla la transacción también se revierte el aviso.

La huella SHA-256 normaliza orden y escala de cantidades: 12 y 12.00 son iguales.
La clave es propia de una operación dentro de un evento; otra clave permite otra preparación intencional.
La respuesta recuperada se obtiene del historial, sin volver a actualizar saldo ni notificar.

## Interfaz y recuperación

Pestaña `preparacion` de la ficha, visible para los cuatro perfiles. Permite elegir barra y muestra madre/destino.
Circuito directo: informa que no requiere preparación. Fuera de los estados permitidos conserva el historial.
Usa Combobox, LectorCodigo, CantidadEnCajas, Dialog, Actor y componentes existentes; cantidades convertidas a botellas.
Ante resultado incierto conserva formulario, cuerpo y clave, bloquea su edición y ofrece «Recuperar preparación».
Solo tras respuesta de éxito se limpia la operación. Un rechazo conocido permite volver a editar.
El cliente HTTP preserva la cabecera de idempotencia también cuando renueva sesión y reintenta un 401.

## Integración y límites

- #28/#29 ya están integrados en la base; V7 permanece intacta y V8 agrega únicamente preparación.
- B4-05 llegó a main mediante #32, después de que #30 se fusionara en una rama secundaria. En `PaginaEvento.tsx`
  se conservan importaciones, permisos, identificadores y renderizados tanto de `preparacion` como de `consumo`.
  Se resolvieron los conflictos de ese archivo y `Permisos.java` sin reemplazar versiones completas.
- El cierre suma movimientos de entregas; PREPARACION tiene `entrega_id` nulo y no participa.
- La transición es stock compartido por ubicación: no se reserva por evento. `salon_id` de transición sigue siendo opcional;
  la asociación operativa se obtiene desde el abastecimiento de la barra del salón del evento.
- No se agrega la regla de impedir preparación tras devolución mientras el evento siga En curso: no está en los criterios.
  El cierre actual tampoco devuelve automáticamente una preparación hecha después de cerrar la última entrega.
- No se cambian las reglas de retiro, devolución, realización ni cierre de Berni. Sus carreras previas no se consideran resueltas por B5-04.

## Contradicciones documentales señaladas

El texto antiguo de B5-04/UI-35 refiere a órdenes y a preparar solo antes del inicio; §4.1 vigente permite carga manual y En curso.
La fila «Orden de preparación» no representa el permiso de registrar un traspaso; se agrega una fila específica B5-04.
Seguimiento marcaba completada la historia antes de existir su código. No se modifica ese documento funcional desde esta rama.
El comentario anterior del cierre que esperaba una entidad de orden desde B5-04 pertenece a una planificación superada:
esta historia no crea ni necesita esa entidad.

## Validación

Pruebas de dominio, HTTP/SQL, permisos, estados, varios circuitos, catálogo, cantidades, auditoría e inmutabilidad;
reintentos con PostgreSQL real y transacciones independientes que verifican esperas con `pg_blocking_pids`;
rollback de cabecera, movimientos, saldos y notificaciones; circuito hasta el consumo fijado.
Interfaz: carga, selección, escaneo, revisión, historial, permisos y respuesta perdida.
E2E `frontend/e2e/preparacion-evento.spec.ts`: requiere demo aislada, `E2E_PREPARACION_AISLADO=1`,
`E2E_DEMO_CONTRASENA` y `E2E_URL`. Configura temporalmente el circuito, pierde la primera respuesta después del commit,
recupera con la misma clave y comprueba historial, ambos saldos y ausencia de desborde en escritorio/teléfono.
No ejecutarlo contra datos operativos: registra movimientos reales.

El entorno utilizó Java 25.0.3, Node 24.19.0 y PostgreSQL 17. La instalación de Node del sistema era 24.14;
se utilizó el runtime 24.19 disponible sin cambiar dependencias ni lockfiles del proyecto.
Se amplió a cinco segundos la espera asíncrona de Testing Library para la primera carga de las rutas lazy:
varios tests existentes agotaban el segundo predeterminado en un arranque frío.
`MigracionesIT` ahora verifica 40 tablas, por la nueva cabecera de V8.

El primer E2E detectó un fallo de Hibernate al refrescar con bloqueo el grafo cíclico bebida/códigos.
La corrección lee `activo` con `SELECT ... FOR SHARE` antes de cargar el artículo, mantiene el bloqueo hasta el commit
y evita refrescar ese grafo. Los fixtures de integración incluyen códigos para cubrirlo.
Se verificaron visualmente capturas de escritorio y teléfono y la ausencia de desborde horizontal.
El E2E se ejecutó con Chrome instalado y una configuración temporal ignorada dentro de `node_modules`,
porque falló la descarga del Chromium de Playwright. No se modificó la configuración compartida de Playwright.
Los servidores y el PostgreSQL de demostración usados para E2E fueron detenidos al terminar.

## Inventario del cambio

### Resultados finales de ejecución — 19/10/2026

| Verificación | Resultado |
|---|---|
| Backend completo, `mvn.cmd -q verify` | 62 pruebas unitarias + 608 integraciones; 0 fallos, errores u omitidas |
| B5-04 dentro de esas suites | 9 unitarias, 45 HTTP/SQL y 9 integraciones concurrentes |
| Frontend completo, Vitest | 34 archivos, 266 pruebas aprobadas |
| E2E real con Chrome | 2 aprobadas: escritorio y teléfono emulado |
| TypeScript, `tsc -b` | Aprobado |
| Vite/PWA, `vite build` | Aprobado; genera service worker |
| ESLint | Aprobado sin errores |
| `git diff --check` | Aprobado |

Evidencia local ignorada por Git: `backend/b5-04-verificacion-final.log`, reportes XML en `backend/target`,
`frontend/b5-04-tests-final.log`, `frontend/b5-04-e2e-verificado.log`, `frontend/b5-04-build-final.log`,
`frontend/b5-04-lint-final.log` y capturas `frontend/test-results/preparacion-escritorio.png` y `preparacion-telefono.png`.
Las pruebas de interfaz simulan el lector de códigos; no se verificó una cámara física.

Comandos usados: Maven instalado con `JAVA_HOME` en JDK 25.0.3; Vitest, TypeScript, Vite, ESLint y Playwright invocados
con el ejecutable Node 24.19.0 disponible en el runtime local. La configuración temporal del E2E está en
`frontend/node_modules/.b5-playwright.config.mjs`, con Chrome del sistema y los proyectos de escritorio/Pixel 7.
El ajuste de esperas asíncronas no cambia aserciones ni reglas funcionales.

### Archivos

16 archivos creados:

- `backend/src/main/java/ar/edu/utn/vastio/agenda/aplicacion/OperacionEventoService.java`
- `backend/src/main/java/ar/edu/utn/vastio/bebida/api/PreparacionController.java`
- `backend/src/main/java/ar/edu/utn/vastio/bebida/api/PreparacionDto.java`
- `backend/src/main/java/ar/edu/utn/vastio/bebida/aplicacion/PreparacionService.java`
- `backend/src/main/java/ar/edu/utn/vastio/bebida/dominio/PreparacionEvento.java`
- `backend/src/main/java/ar/edu/utn/vastio/bebida/infraestructura/PreparacionRepository.java`
- `backend/src/main/resources/db/migration/V8__preparacion_evento.sql`
- `backend/src/test/java/ar/edu/utn/vastio/bebida/api/PreparacionIT.java`
- `backend/src/test/java/ar/edu/utn/vastio/bebida/aplicacion/PreparacionConcurrenciaIT.java`
- `backend/src/test/java/ar/edu/utn/vastio/bebida/aplicacion/PreparacionServiceTest.java`
- `frontend/src/api/preparaciones.ts`
- `frontend/src/features/eventos/PestanaPreparacion.tsx`
- `frontend/src/features/eventos/PestanaPreparacion.test.tsx`
- `frontend/src/features/eventos/preparacion.css`
- `frontend/e2e/preparacion-evento.spec.ts`
- `docs/b5-04-implementacion.md`

13 archivos modificados:

- `backend/src/main/java/ar/edu/utn/vastio/bebida/dominio/MovimientoStock.java`
- `backend/src/main/java/ar/edu/utn/vastio/bebida/dominio/TipoMovimiento.java`
- `backend/src/main/java/ar/edu/utn/vastio/bebida/infraestructura/BebidaRepository.java`
- `backend/src/main/java/ar/edu/utn/vastio/bebida/infraestructura/MovimientoStockRepository.java`
- `backend/src/main/java/ar/edu/utn/vastio/comun/seguridad/Permisos.java`
- `backend/src/test/java/ar/edu/utn/vastio/MigracionesIT.java`
- `frontend/src/api/cliente.ts`
- `frontend/src/api/cliente.test.ts`
- `frontend/src/features/eventos/PaginaEvento.tsx`
- `frontend/src/test/setup.ts`
- `docs/der/diccionario-de-datos.md`
- `docs/perfiles-y-permisos.md`
- `docs/maquina-de-estados.md`

## Estado previo a la publicación original (histórico)

Los cambios permanecen sin staging, commits, push, PR, merges ni rebases. HEAD continúa en el commit base.
La rama nueva no sigue la rama remota de Berni, para evitar publicaciones accidentales sobre ella.
El worktree B4-05 continúa limpio en `b843b80f9a36cb8ddb6f4ff3e6109de081d0a690`.
El checkout original está en `tomi/fix-s3-observaciones` y muestra una modificación de `frontend/package-lock.json`;
ese archivo no fue editado ni revertido por este trabajo. El stash «Cambios locales antes de revisar PR 24» permanece.

Antes de publicar: revisión humana de esta historia, coordinar la integración de #28/#29 y conservar ambas pestañas
al integrar #30. No se abrió ni modificó ningún PR. No se implementaron órdenes de preparación.

## Correcciones posteriores a la auditoría

Se corrigieron los dos P2 de recuperación del formulario:

- Los rechazos de negocio emitidos después de comprobar que la clave no tiene una preparación registrada
  permiten volver a editar sin borrar bebidas ni cantidades. Los resultados inciertos conservan cuerpo y clave.
  Un error de permisos, un conflicto de clave o una respuesta desconocida no descartan un commit previo.
- Las claves usan 16 bytes de `crypto.getRandomValues`, representados por 32 caracteres hexadecimales,
  sin depender de `randomUUID` ni usar `Math.random`. Si la fuente segura falta o falla, se informa el error
  sin hacer el POST. El indicador de envío se libera en `finally`, también ante fallos locales.
- Se ajustó el comentario de `PreparacionConcurrenciaIT` para describir la cobertura efectivamente existente.

Se agregaron 11 regresiones: transporte/500 seguido de rechazo definitivo (3), recuperación incierta ante
permisos/conflicto/respuesta desconocida/500 (4), ausencia de `randomUUID` (1), fuente segura ausente o fallida (2)
y doble envío con respuesta pendiente (1). También se espera explícitamente la inicialización del lector simulado.

Verificación ejecutada sobre las correcciones el 19/10/2026:

| Validación | Resultado |
|---|---|
| Componente de preparación | 26 pruebas aprobadas |
| Suite frontend completa | 277 pruebas, 34 archivos aprobados |
| Unitarias backend seleccionadas | 9 aprobadas (`PreparacionServiceTest`) |
| Integraciones backend relevantes | 146 aprobadas: preparación, concurrencia de preparación, migraciones, cierre, devolución, salida a barra y recuento sin saldo |
| TypeScript, build Vite/PWA y ESLint | Aprobados |
| `git diff --check` | Aprobado |

Las ejecuciones iniciales dentro del sandbox tuvieron problemas de infraestructura: acceso denegado a Docker
y archivos temporales de Vitest ausentes (`ENOENT`). Las repeticiones fuera del sandbox terminaron correctamente.
Los logs de las pruebas están en `backend/b5-04-correcciones-backend.log`,
`frontend/b5-04-correcciones-componente.log` y `frontend/b5-04-correcciones-frontend.log` (ignorados por Git).
No se volvieron a ejecutar los E2E ni se verificó una cámara física en esta corrección.

En esa etapa seguían pendientes #28/#29 y la conciliación de ambas pestañas; la integración local indicada al inicio las incorpora.
No se modificaron servicios backend, migraciones, otras historias, dependencias ni lockfiles en esta corrección.

## Verificación de la integración final sobre main — 19/10/2026

La rama temporal parte de `f6b8876469dd5f521415ae5593146299a9438f1c` (merge de #32).
Se conserva la rama original en `d03d510c29f4b49e07afe7d719ab310ec61bc0bd`.
Los únicos conflictos fueron `PaginaEvento.tsx` y `Permisos.java`: se conservaron ambos permisos,
ambos componentes, los dos valores de URL y una sola llamada a `useSesion()`.

Se agregó `frontend/src/features/eventos/IntegracionPestanasBebida.test.tsx`, con ocho regresiones:
cuatro perfiles navegan entre las pestañas sin escrituras, ambas URL directas funcionan y cada permiso
se verifica independientemente, incluso simulando políticas distintas. Los perfiles reales autorizados
continúan siendo los cuatro definidos para cada historia, sin ampliar acceso.

`PreparacionIT.prepararNoConstituyeConsumo` ahora también consulta B4-05 después del cierre,
verifica que el consumo se registró con la devolución una sola vez y que consultar no altera saldos.
Los archivos propios de B4-05, el motor de movimientos, devolución, cierre y V7 permanecen intactos.
La única migración nueva es V8 de B5-04, sin renumeraciones ni duplicados.

| Verificación ejecutada en el worktree temporal | Resultado |
|---|---|
| Backend completo, `mvn verify` | 62 unitarias y 633 integraciones aprobadas; cero fallos, errores u omitidas |
| Frontend completo, `vitest run` | 300 pruebas en 36 archivos aprobadas |
| TypeScript, build Vite/PWA y ESLint | Aprobados |
| E2E Preparación con backend real | 2 aprobados: escritorio y teléfono emulado, con respuesta perdida e idempotencia |
| E2E Consulta de consumo con backend real | 2 aprobados: escritorio y teléfono emulado |
| Circuito real preparación → retiro → devolución → cierre → consulta | Aprobado; incluye remanente heredado y verifica que el cierre no vuelve a descontar consumo |
| Comparación de stock, movimientos y consolidado antes/después de consultar | Sin cambios |
| `git diff --check` y revisión de conflictos/secretos/temporales | Correctos |

Los E2E usaron Chrome y PostgreSQL efímero exclusivos de este worktree; los servidores y la base se detuvieron
al terminar. La configuración temporal vive bajo `frontend/node_modules` y los logs/capturas están ignorados.
La cámara física no se verificó. La advertencia de tiempos del plugin PWA no impidió completar el build.

La integración agrega únicamente B5-04, sus pruebas y documentación sobre main. No se modifica la observación
documental previa sobre proveedores, ajena a esta historia. No se cambian B5-02/B5-03 ni la regla acordada:
CONSUMO se registra al devolver; el cierre consolida sin descontar stock otra vez.

Esta etapa termina localmente: no publica ramas ni cambia la base o el contenido remoto de #31.
Para usar esta reconstrucción en el head existente habrá que autorizar expresamente la actualización remota,
comprobar que siga en `d03d510`, usar una protección de lease y cambiar el destino del PR a main.
