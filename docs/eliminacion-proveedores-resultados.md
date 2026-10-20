# Eliminación de proveedores — Resultado de Fases 3 y 4

Fecha: 20/10/2026. Rama local `codex/eliminar-proveedores`, worktree `vastio-eliminar-proveedores`, base `02c35649a156c2eea073106a9b9f8a2d28c53860` (`origin/main` auditado, con B5-04 y PR #33). Autorización de implementación recibida después de la auditoría.

## Resultado funcional

Se eliminó el ABM completo de proveedores y sus seis endpoints, servicio, DTO, entidad, repositorio, cliente API y tipos frontend. Bebida e Ingreso ya no tienen relaciones de proveedores ni campos correspondientes en los contratos. Catálogo conserva altas, modificaciones, baja/reactivación, códigos, unidad y stock mínimo. Inventario inicial conserva guardado/correcciones por ubicación con un único botón, sin segundo paso. Ingresos conservan remito, fecha, usuario, destino y renglones. No se alteraron las reglas ni los registros de movimientos, preparación, devolución, cierre o consulta.

Se conservaron los permisos compartidos de catálogo, ubicaciones e ingresos. Se retiraron validaciones y mensajes exclusivos de proveedores, incluidos CUIT y deserialización de su ID. La validación de documento DNI/CUIT de clientes es independiente y sigue vigente.

Se actualizaron datos de demostración, fixtures y regresiones, prototipo UI-28 y documentación vigente. Las referencias históricas permanecen identificadas en la auditoría, las migraciones V1–V8 y el registro de decisiones. El DER PNG se regeneró después de la revisión de Albert desde el esquema V9 y se agregó una fuente DOT editable; el resumen Mermaid complementa las relaciones detalladas. No se modificó Google Drive.

Inventario previo: [auditoría de dependencias](eliminacion-proveedores-auditoria.md). Lista exacta final: [archivos agregados, modificados y eliminados](eliminacion-proveedores-archivos.md). El test combinado de carga inicial/proveedores se reemplazó por `CargaInicial.test.tsx`, conservando las verificaciones de inventario. Las pruebas exclusivas del ABM fueron sustituidas por ausencia de mappings/endpoints y navegación.

## Datos, protección y recuperación

V9 elimina el maestro y las dos columnas/FK; conserva ingresos, movimientos y demás entidades. V1–V8 se verificaron con SHA-256 contra la auditoría y no fueron modificadas.

`scripts/resguardo_proveedores.py` crea un dump completo custom, un extracto JSON de todos los proveedores y ambas relaciones y un manifiesto SHA-256. Restaura el dump en PostgreSQL 17 descartable y compara el extracto completo. `authorize` vuelve a restaurar y comprobar antes de instalar un recibo transitorio para esa base y huella, con escrituras detenidas. V9 verifica nuevamente bajo bloqueo exclusivo y rechaza ausencia de recibo, datos cambiados o recibo de otra base. No usa CASCADE; una dependencia externa inesperada revierte la transacción.

Se ensayó el mecanismo real con tres proveedores (activo, inactivo y sin referencias), dos bebidas y dos ingresos, con relaciones nulas y no nulas, remito, movimientos y stock. La restauración y migración autorizada conservaron los datos restantes. También se verificaron archivo alterado, fuente obsoleta y falta de declaración de pausa de escrituras. El respaldo de prueba está ignorado en `backend/target/resguardo-v9/`; no contiene datos reales y no reemplaza el respaldo de ninguna instalación.

El [procedimiento completo de respaldo y restauración](eliminacion-proveedores-despliegue.md) define el paso manual y el punto de detención antes del despliegue. Una recuperación se hace primero en una base vacía separada con la versión anterior compatible con V8; jamás se restaura automáticamente sobre producción. Si hubo operaciones posteriores, exige reconciliación.

## Pruebas ejecutadas

| Verificación | Resultado |
| --- | --- |
| Backend Java 25: `mvn -B verify` | BUILD SUCCESS; 47 unitarias y 628 integraciones, 0 fallos/errores/omitidas. PostgreSQL 17.11 efímero. |
| Migraciones Flyway | Cadena V1–V9 desde cero y V8→V9; bloqueo sin archivo válido, recibo obsoleto, rollback ante dependencia inesperada, preservación de filas e instalación sin proveedores. |
| Resguardo real | Dump, restauraciones descartables, comparación del extracto, autorización y casos negativos; ejecutado con `VASTIO_TEST_PYTHON` definido. |
| Regresiones críticas backend | `DevolucionIT`, `CierreIT`, `ConsultaConsumoIT`, `PreparacionIT` y concurrencia; `DevolucionSaldoNegativoIT`: 11 casos correctos. |
| Frontend Vitest | 36 archivos, 292 pruebas correctas, sin omitidas. Ejecución final con `--testTimeout=15000`. |
| TypeScript | `tsc -b`, salida 0. |
| Lint | `eslint .`, salida 0. |
| Build Vite/PWA | Salida 0, bundle y service worker generados. |
| E2E catálogo/ingreso/ajuste | Correcto en Chrome escritorio y emulación de teléfono. |
| E2E inventario y ausencia de proveedores | Correcto en escritorio y teléfono: guardado y corrección compensatoria, sin pestaña, selector ni solicitudes al API retirado. |
| E2E B5-04 | Correcto en escritorio y teléfono: preparación, respuesta perdida e historial sin duplicación. |
| E2E B4-03/B4-04 | Correcto en escritorio: salida, retiro, devolución, consumo, cierre y remanente. |
| E2E B4-05 | Correcto en escritorio y teléfono contra el evento cerrado por el E2E anterior. |
| Total E2E | 9 casos correctos (6 + 1 + 2), sin omitidos. |
| Ausencia de funcionalidad | Búsqueda sin coincidencias de proveedor en Java/TypeScript operativo; mappings y seis variantes de endpoint retirados verificadas con Dirección autenticada; documentación OpenAPI cubierta en perfil dev. |
| Integridad | V1–V8 idénticas, lockfile sin cambios y revisión `git diff --check`. |

Comprobación final ampliada: `mvn -B -Dtest=IngresoDtoTest -Dit.test=MigracionProveedoresIT,ProveedoresEliminadosIT,PerfilDevIT verify`, con `VASTIO_TEST_PYTHON` definido, terminó en **BUILD SUCCESS**. Pasaron 1 unitaria y 14 integraciones (7 de migración/resguardo, 1 de endpoints retirados y 6 de perfil dev/OpenAPI), sin fallos, errores ni omitidas. Incluye dos casos nuevos adicionales a la corrida completa: base poblada sin proveedores y recibos de otra base/con hashes inválidos. El log del ensayo Python se captura en UTF-8 sin interferir con el canal de Failsafe.

Se ejecutaron sobre contenedores nuevos de Testcontainers y un contenedor E2E exclusivo `vastio-v9-e2e-20261020`, sin volúmenes persistentes. Los servidores E2E usaron puertos 18089/5179 y PostgreSQL 55439; no usaron el contenedor existente `vastio-db`. Las bases de restauración se crean sin red ni puertos. Los servidores y el contenedor E2E fueron detenidos/eliminados al terminar; Testcontainers libera sus recursos.

Incidencias resueltas durante la validación: Docker inicialmente no disponible; restricciones del sandbox sobre temporales/procesos de Vitest; espera asíncrona faltante en una prueba adaptada; dos timeouts de 5 segundos de catálogo bajo carga (pasaron con 15 segundos, sin cambiar aserciones); bloqueo de empaquetado de Windows por el JAR abierto del servidor E2E, que requirió detenerlo; y lectura UTF-8 del log Python en Windows, fijada explícitamente para el proceso de prueba. Estas corridas iniciales no se contaron como resultados aprobados. Node usado en la validación final: 24.19.0, compatible con el lockfile; no se actualizaron dependencias.

Evidencia local (fuera del diff, sin datos reales):

- `../vastio-proveedores-backend-verify.log`, respecto de la raíz del worktree: suite backend completa.
- `../vastio-proveedores-migracion-final.log`: comprobación ampliada final.
- `../vastio-proveedores-frontend-test-final.log`, `../vastio-proveedores-frontend-lint.log`, `../vastio-proveedores-typescript.log`, `../vastio-proveedores-frontend-build.log`.
- `../vastio-proveedores-e2e-inicial.log`, `../vastio-proveedores-e2e-operacion.log`, `../vastio-proveedores-e2e-consulta.log`.
- `backend/target/surefire-reports/`, `backend/target/failsafe-reports/`, `backend/target/resguardo-v9/ejecucion.log` y `frontend/test-results/`.

## Riesgos y operaciones pendientes

1. No se inspeccionaron bases reales: volumen de información, dependencias manuales/externas y estado de cada instalación quedan por verificar.
2. Antes de desplegar hay que acordar custodio, almacenamiento protegido, permisos/cifrado, retención y prueba de recuperación del respaldo real. Los archivos de este ensayo solo respaldan datos sintéticos.
3. El recibo protege contra ejecución accidental con datos sin validar; un DBA con permisos puede falsificarlo. La disponibilidad y custodia del archivo externo requieren revisión humana. La huella corresponde a datos eliminados y relaciones, no certifica ausencia de cambios ajenos a ellos: detener toda escritura sigue siendo obligatorio.
4. Backend/frontend anteriores son incompatibles con V9; coordinar despliegue, jobs y clientes/PWA. Rollback después de nuevas operaciones exige reconciliación.
5. El ensayo externo de respaldo de la suite requiere Python y `VASTIO_TEST_PYTHON`; sin esa variable ese único caso se omite explícitamente. En esta ejecución estuvo habilitado. Para la publicación se configuró Python 3.12 en CI y se pasa la ruta explícita del ejecutable a Maven, con comprobación previa para evitar una omisión accidental por variable vacía. El ensayo usa exclusivamente PostgreSQL descartable; no cambia V9 ni usa secretos de bases reales.
6. `npm ci` informó una vulnerabilidad alta del lockfile existente; no se aplicaron actualizaciones automáticas fuera del alcance. Los checks funcionales y de compilación no constituyen una auditoría de dependencias.

**Detención operativa:** no se ejecutó V9 ni ninguna otra operación destructiva sobre bases reales/productivas. El próximo paso de despliegue es manual y necesita autorización específica para la instalación, después del respaldo verificado. La autorización posterior permite commit, push y PR con revisión del equipo, sin fusionar. No habilita despliegues.

## Diff y rama

El inventario del cambio contiene 67 archivos: 9 eliminados, 43 modificados y 15 agregados. Los cambios adicionales para publicar y atender la revisión están limitados a CI, documentación y regeneración del DER; el código operativo, V9 y los scripts de resguardo permanecen como fueron auditados.

La base remota confirmada con `git fetch origin` para publicar es `02c35649a156c2eea073106a9b9f8a2d28c53860`; coincide con el HEAD previo a crear el commit de esta entrega, por lo que no hay cambios remotos pendientes ni conflictos de integración. El diff publicado se consulta en el PR contra `main`. El checkout original, sus cambios y stash y los demás worktrees no fueron modificados.

El detalle de las suites completas y E2E de arriba corresponde a la implementación previa. La preparación del PR ejecutó nuevamente `mvn -B -Dtest=IngresoDtoTest -Dit.test=MigracionProveedoresIT,ProveedoresEliminadosIT,PerfilDevIT verify` con Python 3.12.14 explícito: **BUILD SUCCESS**, 1 unitaria y 14 integraciones, 0 fallos, errores u omitidas (20/10/2026, 15:56 ART). Incluye los 7 casos de migración y el ensayo real de resguardo/restauración. Evidencia local ignorada: `vastio-proveedores-ci-verificacion.log`, en la raíz del worktree. El CI del PR debe comprobarse por separado y no se considera aprobado por la sola configuración del workflow.

## Correcciones de la revisión de Albert

Se agregó en `docs/despliegue.md` una receta manual de recreación solo para bases de desarrollo descartables, con identificación de contexto/proyecto, contenedor, montaje, base y usuario reales; pausa por pérdida completa de datos y comprobación de Flyway V9. Las bases con información histórica mantienen el respaldo y autorización obligatorios. No se ejecutaron borrados de bases o volúmenes existentes.

Se regeneró `DER_3_bebida.png` y se incorporó su fuente DOT desde metadatos de PostgreSQL descartable migrado con el backend: 14 tablas, 90 columnas y 39 FK del módulo, más cinco referencias externas. Se preservaron V1–V9 y todo el código operativo. El anexo de 625 coincidencias se redujo a búsqueda reproducible, síntesis y pendientes de Drive; el original permanece en Git. Auditoría, hashes y procedimiento de respaldo/restauración se conservan por su función de seguridad.

Verificación de esta corrección: arranque real del backend sobre un PostgreSQL 17 nuevo y descartable, con las nueve migraciones exitosas y validación Hibernate; comparación automática del DOT con columnas, tipos, nulabilidad, PK y las 39 FK de `pg_catalog`; renderizado y revisión visual del PNG; sintaxis de los cuatro bloques PowerShell y `git diff --check` correctos. Se repitió `mvn -B -Dit.test=MigracionProveedoresIT failsafe:integration-test failsafe:verify` con `VASTIO_TEST_PYTHON` explícito: 7 integraciones, 0 fallos, errores u omitidas, BUILD SUCCESS (20/10/2026, 18:37 ART). Incluye el ensayo real de dump/restauración. Log local ignorado: `vastio-revision-albert-migracion.log`. El proceso y contenedor exclusivos del DER se retiraron después de consultar el esquema; ninguna base existente fue modificada.

Los comandos destructivos de la receta de recreación no se ejecutaron, tampoco sobre una base local existente. No se repitieron localmente frontend ni E2E en esta corrección solo documental; el CI del commit adicional debe comprobarse separadamente. La aprobación de Albert sigue pendiente; no se descarta ni se sustituye su revisión solicitando cambios.

La primera corrida CI de la corrección documental (ejecución 61, commit `08841d2`) falló en una restauración descartable: `pg_restore` se conectó por socket al servidor temporal del entrypoint de `postgres:17`, que fue reiniciado durante la restauración. Se confirmó el comportamiento leyendo el entrypoint de la imagen. Se ajustó únicamente la conexión de espera/restauración de ese contenedor a TCP loopback, que solo acepta el servidor definitivo. No se agregaron reintentos de restauración ni se relajaron comprobaciones de datos, hashes, recibo o V9; no se publican puertos ni se cambia la fuente. Tras el ajuste se repitieron los 7 casos con el resguardo real: BUILD SUCCESS, 0 fallos, errores u omitidas (18:44 ART), log local `vastio-revision-albert-restauracion-tcp.log`. El CI del nuevo commit se verifica por separado.
