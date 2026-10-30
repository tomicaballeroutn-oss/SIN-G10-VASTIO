# M-06 — Observaciones finales de Albert (30/10/2026)

## Estado consultado en GitHub

- [#51](https://github.com/tomicaballeroutn-oss/SIN-G10-VASTIO/pull/51): abierto, Draft, M-06 `6185c08` hacia main `f94fd73`. Albert dejó una [aprobación con tareas de seguimiento](https://github.com/tomicaballeroutn-oss/SIN-G10-VASTIO/pull/51#pullrequestreview-5706715462); no equivale a aprobar los cambios nuevos de esta rama.
- [#56](https://github.com/tomicaballeroutn-oss/SIN-G10-VASTIO/pull/56): fusionado mediante `c50b4dc`; [#57](https://github.com/tomicaballeroutn-oss/SIN-G10-VASTIO/pull/57): aprobado y fusionado mediante `6185c08`. Ambos y main son ancestros de la base de esta corrección.
- #49 (`d136cc6`) y #52 (`0f5eb45`) siguen abiertos. No se integran ni modifican sus ramas. No hay hilos inline pendientes en #51/#56/#57; las observaciones están en las revisiones generales.
- Rama independiente: `codex/m06-observaciones-finales`, dirigida a `codex/m-06-google-calendar`. No se modifica el estado, descripción ni rama del #51 directamente.

## Clasificación y recuperación

| Momento | Resultado y tratamiento |
|---|---|
| Preparación transaccional local | Si falla, rollback antes de llamar a Google. No existe respuesta remota que guardar. |
| Lecturas de destino/identidad, antes de escribir | Un RuntimeException inesperado produce `EJECUCION_CALENDAR_FALLIDA`, no `RESULTADO_NO_PERSISTIDO`. No se confirma publicación. |
| OAuth en `initialize()` | `OAUTH_GOOGLE`, cierto para esta solicitud: no llegó al transporte Calendar. Conserva la fase OAuth y las causas sanitizadas. |
| Escritura iniciada, sin resultado determinable | RuntimeException inesperado o fallo de transporte incierto conserva INCIERTO. No se presupone que Google no escribió. |
| Respuesta de escritura inválida o imposible de interpretar | No se confirma éxito; se conserva INCIERTO, incluida identidad ajena/contenido inválido en esa respuesta. |
| Guardado de resultado confirmado | `RESULTADO_NO_PERSISTIDO` reservado a fallos de acceso/persistencia/transacción. Un error de ejecución, incluido `InvalidDataAccessApiUsageException` que Spring genera al traducir un error de programación, usa `EJECUCION_CALENDAR_FALLIDA`. Ambos requieren reconciliación. |
| Falla el registro local del error | No se sustituye la causa remota por un falso fallo de guardado de resultado. Permanece EN_PROCESO durable y se exige reconciliación. |

Un error cierto de OAuth o una lectura fallida **no resuelve un intento anterior incierto**. Hasta consultar su identidad, permanece INCIERTO. Se conserva el algoritmo existente: destino explícito, identidad estable, lectura antes de escritura, huellas, ETag/If-Match y recuperación manual. No hay nuevos identificadores, publicación automática ni reintentos HTTP. El progreso usado para clasificar una excepción inesperada solo vive dentro de la solicitud y no habilita acciones.

Los logs de ejecución contienen fase cerrada y clase de excepción; el diagnóstico del adaptador mantiene categorías DNS/TLS/timeout/conexión/HTTP/OAuth y clases causales. No registra mensajes, cuerpos, rutas privadas, URLs, claves, tokens ni trazas completas.

## Resolución de cada observación

| Observación | Resolución |
|---|---|
| RuntimeException remoto rotulado como persistencia | Corregida: bloques remotos y de confirmación separados, códigos y regresiones por fase. |
| Rutas públicas con usuario local | Corregida: marcadores de worktree/JDK, sin nombres de usuario locales. |
| Relación entre los dos documentos OAuth | Corregida: diagnóstico como registro histórico, enlaces recíprocos y enlace a este estado de seguimiento. Los resultados de cada etapa no se reescriben como si nunca hubieran fallado. |
| Primer guardado de hora particular | No reproduce defecto del formulario: regresiones de alta/limpieza de hora y control nativo Chrome con API simulada. La evidencia histórica solo acredita un valor visible sin cambio efectivo del estado en aquel intento de automatización; no permite atribuir definitivamente su causa al navegador o al formulario. No se modifica el formulario comercial. |
| OAuth previo al envío clasificado incierto | Corregida para IOException y RuntimeException en initialize(); se comprueba ausencia de solicitudes de transporte en crear/actualizar/retirar. No elimina incertidumbre previa. |
| Credenciales registradas dos veces | Corregida: un único diagnóstico sanitizado y prueba de captura que excluye la ruta del archivo. |
| Estilo del código nuevo | Normalizados los bloques modificados de diagnóstico, OAuth, gzip y clasificación, sin reformatear funcionalidades ajenas. |
| Reintento periódico de la cola | Ya existe: `SeguimientoCalendarProgramado`, cada cinco segundos, y backoff en cola. No reproduce ausencia de implementación; no se agrega otro scheduler. Las regresiones existentes ejercitan recuperación de pendientes sin nuevo evento Spring. Nunca llama a Google. |
| Triggers de catálogo y pruebas de salón/tipo | Corregida en **V16**: WHEN/IS DISTINCT FROM para turno, salón y tipo. Regresión parametrizada comprueba guardado sin cambio, cambio efectivo y cero llamadas a Google. |
| Descripción desactualizada y Draft del #51 | Pendiente de integración/revisión humana. El nuevo PR describe el ciclo real ya ejecutado y las pruebas actuales; no se cambia #51 directamente. |
| Compartir evidencia en Drive del equipo | Pendiente de destino y publicación autorizada. No se comparten archivos privados ni se envían mensajes al equipo en este trabajo. |

## Migraciones y otros PR

Main actual solo tiene V1–V13. #49 y #52 tampoco añaden versiones posteriores. V14/V15 permanecen byte a byte iguales a M-06; V16 solo reemplaza tres triggers, sin modificar tablas, datos comerciales, publicaciones ni auditoría. Las pruebas usan exclusivamente PostgreSQL descartable de Testcontainers: instalación desde cero y actualización desde V13, V14 y V15, validación de checksums, repetición sin migraciones nuevas y conservación de datos/identidad/historial/cola donde ya existían.

Los árboles combinados de comparación con #49 y #52 se generaron sin conflictos textuales y fuera de las ramas publicadas. #49 conserva `UsuarioActual(long, Set)`/`accesoTotal`; sus políticas globales de contraseña no se eluden desde Calendar. Se comprueba TypeScript del árbol combinado, sin incorporar su funcionalidad.

**#52 tiene una incompatibilidad real de compilación**: su mapa COLOR exhaustivo omite CALENDAR_PENDIENTE (TS2741 reproducido). El [parche de integración limitado a M-06](compatibilidad/m-06-pr52.patch) añade esa entrada y un selector que usa el token warning de ambos temas. Se verifica sobre el árbol combinado descartable, sin importar los colores de otros tipos a esta rama. El parche debe incorporarse al integrar ambos PR; no se presenta la combinación sin ese parche como compatible. Las suites y compilación del PR de corrección validan M-06 por separado, no una integración aprobada de PR pendientes.

## Validación de esta corrección

Los resultados definitivos y CI se registran en el PR. No se reutiliza como aprobación de estos cambios el CI de #57. Las pruebas optativas externas permanecen desactivadas; ninguna prueba de esta tarea obtiene tokens ni consulta/escribe Google. No se usa la base comercial ni la base de validación real existente.

Comprobaciones previas a publicar: 89 unitarias backend aprobadas y tres optativas externas omitidas expresamente; 23 regresiones frontend aprobadas; seis E2E Chrome de escritorio/móvil aprobados contra API simulada; TypeScript, ESLint y build de producción aprobados. #49 combinado compila en backend y TypeScript; #52 combinado falla con TS2741 y compila TypeScript después del parche, cuya aplicación se verifica. Diff inspeccionado sin material de claves/tokens ni rutas personales en los documentos M-06; `git diff --check` aprobado. El resultado del verify completo local final, la repetición completa frontend y CI se añaden al PR al terminar, sin contar como aprobadas ejecuciones pendientes.

Durante el desarrollo se conservaron fallos intermedios: aserción OAuth que exigía la incertidumbre anterior; traducción de IllegalStateException por Spring detectada por la nueva regresión; selectores de tests de hora que omitían el sufijo accesible «opcional»; y timeout del lector/arranque de un worker en la suite Windows bajo carga. Se corrigen las causas de las nuevas regresiones, sin aumentar timeouts, omitir suites ni debilitar aserciones del producto.

## Condiciones para solicitar otra revisión a Albert

1. Revisar el HEAD exacto de este PR Draft y sus checks Backend/Frontend; no dar por vigente para este diff la aprobación previa del #51.
2. Revisar la matriz de errores, la conservación de incertidumbre anterior, la recuperación por la misma identidad y las regresiones de creación/actualización/retirada.
3. Revisar V16 y los ensayos desde cero/V13/V14/V15, sin modificar migraciones ya aplicadas.
4. Coordinar la aplicación del parche de CALENDAR_PENDIENTE cuando se integren #51/#52 y volver a verificar el árbol combinado con las ramas vigentes; #49 tampoco se integra automáticamente.
5. Después de la integración humana, actualizar la descripción obsoleta del #51 y decidir su paso de Draft a Ready. Esta tarea no fusiona, aprueba ni cambia ese estado.
6. Acordar aceptación externa adicional autorizada: permisos insuficientes, conflicto por edición externa, turno nocturno y activación/desactivación; verificar además los permisos efectivos de la cuenta y que no tenga acceso a calendarios ajenos al alcance previsto. Compartir solo evidencia sanitizada en el destino del equipo autorizado.

El [ciclo real anterior](m-06-validacion-real.md) sigue siendo evidencia de exportación, idempotencia, actualización, privacidad y retirada en el calendario aislado. No se repite aquí, no acredita los escenarios externos pendientes y no se declara M-06 terminada.
