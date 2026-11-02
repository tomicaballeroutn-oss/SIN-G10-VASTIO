# B4-05 — Consultar consumo de bebida por evento

Implementación local del 19 de octubre de 2026. Pendiente de revisión del responsable; sin commits, push ni PR.

## 1. Preparación de Git

- Repositorio: `tomicaballeroutn-oss/SIN-G10-VASTIO`.
- Checkout original: `C:\Users\tomic\SIN-G10-VASTIO`, rama `tomi/fix-s3-observaciones`, HEAD `bf1eb23405c9da9f29bfc4d812251810f7af63b8`.
- Se ejecutó `git fetch origin` antes de crear la rama.
- Los PR #26, #27, #28 y #29 estaban abiertos y sin integrar al verificar GitHub.
- Se recuperó el análisis anterior del chat «Diagnosticar frontend del PR #24»: los cuatro commits de Berni coinciden con los actuales. Tampoco cambiaron durante este fetch.

| Referencia | Commit verificado |
|---|---|
| `origin/main` | `80eacdd5a5348c0d8d5b3a4bba0c6729a1d7f0a2` |
| `origin/berni/A3-registrar-realizacion-evento` | `88bd5923381a6110d2c27389b893022af3ab235c` |
| `origin/berni/B4-registrar-salida-barra` | `3dd016938d1bce4a9ee01b5e0f4440682d71fbad` |
| `origin/berni/B4-registrar-devolucion-bebida` | `d62b08597273b79089d7eaff8842d5d60ae32246` |
| `origin/berni/B4-registrar-cierre-evento` | `cd5ec780757e425d7f556a32c0562626a31be83f` |

Se creó `tomi/B4-05-consultar-consumo` desde la última referencia, en un worktree independiente: `C:\Users\tomic\OneDrive\Documentos\ChatGPT\Seminario\vastio-b4-05`. La rama no tiene upstream: no apunta por defecto a la rama de Berni para publicar.

El checkout original conserva su rama, HEAD, modificación local y stash. Comprobación al inicio y después de implementar:

- Blob del archivo local `frontend/package-lock.json`: `dccd73f0b90ddbfae2f225820eccc8927bb1c1ea`.
- `refs/stash`: `0348f75e17d0bf470e9b52c7e050d4ded1b5b9f5`.
- Stash existente: `On main: Cambios locales antes de revisar PR 24`.

No se aplicó, creó ni eliminó ningún stash. El lockfile local no se copió a la nueva rama.

## 2. Archivos

Modificados:

- `backend/src/main/java/ar/edu/utn/vastio/comun/seguridad/Permisos.java`: permiso específico `CONSULTAR_CONSUMO`.
- `frontend/src/features/eventos/PaginaEvento.tsx`: pestaña «Consumo de bebida» y control de perfiles.

Creados:

- `backend/src/main/java/ar/edu/utn/vastio/bebida/aplicacion/ConsultaConsumoService.java`.
- `backend/src/main/java/ar/edu/utn/vastio/bebida/api/ConsumoEventoController.java`.
- `backend/src/main/java/ar/edu/utn/vastio/bebida/api/ConsumoEventoDto.java`.
- `backend/src/test/java/ar/edu/utn/vastio/bebida/api/ConsultaConsumoIT.java`.
- `frontend/src/api/consumos.ts`.
- `frontend/src/features/eventos/PestanaConsumo.tsx`.
- `frontend/src/features/eventos/PestanaConsumo.test.tsx`.
- `frontend/e2e/consulta-consumo.spec.ts`.
- Este informe.

## 3. Funcionalidad

`GET /api/v1/eventos/{eventoId}/consumo` consulta el consolidado de un evento cerrado. Devuelve detalle por bebida: entregado, devuelto, remanente, consumido y cociente por invitado, junto con total, cantidad de invitados, indicador de segmentos y denominador explícito `INVITADOS`.

- Evento inexistente: 404, `EVENTO_INEXISTENTE`.
- Evento en cualquier estado distinto de Cerrado: 409, `EVENTO_NO_CERRADO`; no se presenta como cero.
- Cerrado sin registros: 200, lista vacía, total cero.
- Cerrado con registros de consumo cero: se conservan las cantidades y el detalle.
- Invitados nulos o cero: cocientes nulos; la interfaz explica que no están disponibles.
- Perfiles permitidos: Dirección, Coordinación, Administración y Compras. Barra, Vendedora, Planner y Cocina reciben 403; sin sesión, 401.

La ficha ofrece la pestaña a los perfiles permitidos, también mediante `?pestana=consumo`. La pantalla muestra tabla, total, consumo por invitado, carga, errores con reintento, falta de cierre y ausencia de consumo. Reutiliza `Tabs`, `Table`, `Card`, `Alert`, `Button`, `Cargando`, `useDatos` y el formateo de cajas/botellas del sistema de diseño. No contiene acciones de edición ni de cierre.

## 4. Decisiones técnicas

- Servicio transaccional `readOnly`, independiente del caso de uso de cierre.
- Reutiliza `ConsumoEventoRepository`, `ConsultaEventoService.operativo()` y `CierreEventoService.datos()`. No accede a repositorios del módulo Agenda.
- Se conserva el consolidado existente: la clave `(evento_id, bebida_id)` ya reúne todas las barras. Se suman únicamente las cantidades congeladas para el total, con orden estable por nombre, presentación e identificador.
- No se consultan movimientos ni stock actual y no se toma una orden de preparación como consumo.
- Cantidades en botellas; cociente con cuatro decimales y `HALF_UP`, consistente con el cierre existente.
- El denominador disponible es cantidad de invitados, incluso para eventos con segmentos. No se afirma que sea asistencia real.
- DTO propio, sin serialización de entidades JPA, importes, precios ni costos.
- Autorización con `@PreAuthorize(Permisos.CONSULTAR_CONSUMO)`. No se amplía `OPERAR_BARRA` ni se habilita a Administración a cerrar eventos.
- Sin migraciones; no se modifican entregas, devolución, realización ni cierre de Berni.
- OpenAPI actualizado mediante anotaciones del endpoint y respuestas de error documentadas.

## 5. Verificación

| Verificación ejecutada | Resultado |
|---|---|
| Backend completo: `mvnw.cmd -B verify`, Java 25.0.3, PostgreSQL 17 en Testcontainers | **53 unitarias + 578 integraciones aprobadas**, cero fallos, errores u omisiones. Incluye la primera versión de B4-05 (24 integraciones). |
| Backend final: `mvnw.cmd -B -Dtest=IngresoDtoTest -Dit.test=ConsultaConsumoIT verify` | **10 unitarias + 25 integraciones aprobadas**, cero fallos, errores u omisiones. Incluye la prueba adicional de varias bebidas. |
| Frontend completo: Node 24.19.0, `node node_modules/vitest/vitest.mjs run` | **34 archivos, 265 pruebas aprobadas**, cero fallos. Incluye las 15 pruebas nuevas de consulta. |
| Playwright: `playwright test e2e/consulta-consumo.spec.ts --workers=1`, Chrome instalado | **2 aprobadas**: escritorio y teléfono, contra Vite y backend reales con PostgreSQL aislado. |
| `npm run build` | TypeScript, Vite y generación PWA aprobados. |
| ESLint final: `node node_modules/eslint/bin/eslint.js .` con Node 24.19 | Sin errores, código de salida 0. |
| `git diff --check` | Sin errores de espacios. |

Los comandos de backend se ejecutaron con `JAVA_HOME=C:\Program Files\Java\jdk-25.0.3`. El Node compatible ya estaba instalado en `C:\Users\tomic\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe`; no se cambió Node global, `package.json` ni `package-lock.json`. Para repetir las pruebas puede anteponerse ese directorio a `PATH` en la sesión.

La primera ejecución frontend con el Node global 24.14.0 terminó con 255 aprobadas y 10 fallos; una repetición secuencial se interrumpió al detectar el requisito de runtime. `jsdom` 30.1.1 declara `^24.15.0` para Node 24. En una copia exacta de `cd5ec78`, sin B4-05 y con Node 24.14, también falló la prueba de firma de contrato (19 aprobadas, 1 fallida). Con Node 24.19, la suite completa de la implementación pasó sin modificar pruebas existentes ni aumentar sus tiempos. Esto verifica el resultado en el entorno compatible; no se atribuyen todos los fallos preliminares a una causa de código demostrada.

En Playwright, el primer intento no encontró los binarios descargables; se usó Chrome instalado. El siguiente detectó un selector incorrecto del propio test para la tarjeta de total, que se corrigió. La ejecución final pasó ambos proyectos. Las capturas de ambos tamaños fueron inspeccionadas visualmente.

Logs locales conservados e ignorados por Git: `backend-verificacion.log`, `backend-b405.log`, `frontend-tests.log`, `frontend-tests-secuencial.log`, `frontend-baseline-tests.log`, `frontend-tests-node24.log`, `frontend-build.log`, `frontend-build-final.log`, `frontend-lint-final.log` y `e2e-consumo.log`. Capturas en `frontend/test-results/`. Los servidores, la base y la copia de comparación se crearon exclusivamente para estas pruebas; se retiraron al terminar. Ninguna prueba apuntó a la base original.

Las pruebas agregadas comprueban cantidades, varias bebidas, varias barras con devoluciones y cierre real, ausencia de consumo, estados no cerrados, invitados nulos/cero, segmentos, redondeo, autorización, aislamiento entre eventos y conservación de tablas después de consultas repetidas. El E2E consulta el backend real desde la ficha como Administración, en escritorio y teléfono.

La prueba de navegador utiliza `E2E_URL`, `E2E_DEMO_CONTRASENA` y `E2E_CONSUMO_EVENTO` (un evento cerrado de la base de prueba). `E2E_BROWSER_CHANNEL=chrome` permite usar Chrome instalado. No modifica datos. Si no se proveen los datos requeridos, se omite explícitamente.

## 6. Riesgos y limitaciones

- **Saldo negativo en devolución:** el análisis previo identificó una posible inconsistencia al devolver cero y declarar remanente cero desde una barra con saldo negativo. Permanece fuera de esta historia y requiere tratamiento con Berni. La consulta muestra el consolidado que efectivamente se guardó, sin corregirlo silenciosamente.
- **Concurrencia retiro–realización–cierre:** sigue pendiente investigar y probar la ventana entre la lectura del estado y las operaciones concurrentes de los productores del dato. B4-05 no cambia esos bloqueos ni sus reglas.
- **Dependencias abiertas:** la rama depende de #26 → #27 → #28 → #29. Las correcciones de Berni o Albert pueden exigir actualizar esta rama y repetir las pruebas antes del PR.
- **Datos de contexto vigentes:** las cantidades están congeladas; nombre/presentación/unidad de bebida y cantidad de invitados se leen de los registros vigentes. No se agrega una fotografía histórica del catálogo ni del denominador. Las vías normales de edición del evento cerrado continúan bloqueadas por las reglas existentes.
- **Asistencia real:** no está implementada en la base de esta historia; la pantalla identifica explícitamente el cociente por invitados. Desde el Sprint 4 (docs/sprint-4.md, decisión 8), si el evento tiene la asistencia real por segmento completa, el divisor es esa suma y `denominador` vale `ASISTENCIA_REAL`.
- **Prototipos anteriores:** UI-34 menciona costos y estadísticas; se aplicó el alcance vigente de cantidades y consulta desde la ficha.

## 7. Estado final de Git

Rama local sin commits propios ni publicación. Los cambios de implementación quedan sin stage para revisión. El checkout original y su stash permanecen separados del worktree nuevo. No se modificaron `main` ni las ramas de Berni; no se realizaron merges, rebases, commits, push ni PR.

## 8. Antes de publicar

1. Revisar el diff y este informe.
2. Acordar por separado el tratamiento de los dos riesgos de los productores de datos.
3. Verificar nuevamente el estado de #26–#29 y las correcciones posteriores; actualizar la base únicamente con autorización para esa integración.
4. Usar una versión de Node compatible con `jsdom` (en Node 24, al menos 24.15); la suite final pasó con 24.19. Repetir controles si se modifica la base o el código tras la revisión.
5. Esperar autorización del responsable para crear commits y publicar.
