# B5-01 — Estadísticas históricas de consumo, Bloque A

Implementación del 24/10/2026, pendiente de revisión. Sin commits, push ni PR.

## Base y alcance

- Repositorio: `tomicaballeroutn-oss/SIN-G10-VASTIO`.
- Se actualizó `origin/main` mediante `git fetch origin main` y se verificó contra GitHub.
- Base y HEAD: `c05c8efa9f079502481c19ac706394a49b05f7e5` (PR #40).
- Rama nueva: `codex/b5-01-estadisticas-consumo`, sin upstream.
- Worktree independiente: `C:\Users\tomic\OneDrive\Documentos\ChatGPT\Seminario\vastio-b5-01`.
- No se usó `codex/m-10-respaldo-local` como base. Su rama, HEAD y archivos permanecen intactos.
- No se agregaron migraciones ni dependencias. No se modificaron reglas de cierre, movimientos, inventario, preparación, B4-05 ni A5-06.

Se implementó solo la consulta histórica: selección de eventos cerrados, filtros, indicadores, agrupaciones, autorización y pantalla independiente. No incluye compras sugeridas, demanda futura, stock disponible, precios ni costos.

## Fuente y denominadores

La población se selecciona por `evento.estado = CERRADO`, fecha inclusiva de `evento.unidad.fecha`, salón de esa misma unidad y tipo del evento. No se usa la fecha de cierre, creación ni movimientos.

El consumo proviene exclusivamente de `consumo_evento.cantidad_consumida`. La clave `(evento_id, bebida_id)` ya consolida todas las barras. Una fila ausente aporta consumo cero; no elimina al evento.

La tabla `asistencia_segmento` existe desde V1, pero no está implementado el registro operativo de asistencia real. Esta historia no la consulta, aunque contenga datos parciales. El denominador es siempre **`INVITADOS_REGISTRADOS`**, tomado de `evento.cantidad_invitados`.

Para cada conjunto o grupo:

```text
promedioPorEvento = suma(consumo) / cantidad(eventos cerrados seleccionados)
porInvitadoRegistrado = suma(consumo de eventos con invitados > 0)
                         / suma(invitados de esos mismos eventos)
```

Los eventos con invitados nulos o cero se excluyen de ambos términos del segundo cociente. Los eventos válidos sin consumo sí aportan invitados. No se promedian cocientes individuales ni se repiten invitados por cada bebida.

Las sumas usan `BigDecimal`; los cocientes se redondean solo al final, a cuatro decimales con `HALF_UP`. Los contadores y la suma de invitados usan `long`.

Ejemplo: eventos A (10 botellas, 100 invitados), B (sin consumo, 200 invitados) y C (9 botellas, invitados desconocidos): total 19; promedio por evento 6,3333; por invitado 0,0333, sobre 2 eventos y 300 invitados. El consumo elegible para ese último indicador es 10.

El filtro de bebida restringe las cantidades, nunca la población seleccionada por fecha, tipo y salón.

## Arquitectura y consultas

1. `ConsultaHistoricaEventoService`, en Agenda, expone valores escalares de eventos cerrados. Solo ese módulo usa `EventoRepository`.
2. `EstadisticaConsumoService`, en Bebida, llama al servicio de Agenda y lee proyecciones de consumos en lotes de hasta 1.000 identificadores. No realiza consultas individuales por evento o bebida.
3. Se leen tipos mediante `CatalogoService` y referencias de bebidas mediante una proyección sin entidades, códigos, unidades ni stock.
4. Se construye la población antes de acumular consumos. Los mismos acumuladores generan resumen, grupos y detalle.
5. La transacción de producción es `readOnly` y `REPEATABLE_READ`: población, referencias y consumos pertenecen a una instantánea consistente, sin bloqueos de escritura.

La consulta sin filtro de bebida requiere `ceil(eventos / 1000)` lecturas de consumos, más las lecturas de población y catálogos. Las validaciones de referencias agregan consultas constantes. Sin eventos, no se consulta el consolidado.

Se muestran bebidas y tipos activos aun sin historial, y referencias inactivas con historial en la selección. Una referencia inactiva seleccionada explícitamente también se devuelve sin historial. Los nombres y presentaciones son los vigentes del catálogo; no se agregó una fotografía histórica de esas etiquetas ni de invitados.

## API

`GET /api/v1/estadisticas/consumo`

| Parámetro opcional | Tipo | Significado |
|---|---|---|
| `desde` | Fecha ISO `AAAA-MM-DD` | Primer día de jornada, inclusive |
| `hasta` | Fecha ISO `AAAA-MM-DD` | Último día de jornada, inclusive |
| `tipoEventoId` | `Short` positivo | Tipo de evento |
| `salonId` | `Short` positivo | Salón de la unidad |
| `bebidaId` | `Long` positivo | Bebida cuyo consumo sumar |

Sin fechas se incluye todo el historial; se admite un solo límite. Las fechas se restringen a los años 1–9999. Se permite `desde = hasta`.

- 200: consulta válida, incluso sin eventos o consumo.
- 400: formato/rango inválido, identificador no positivo o período invertido. Las reglas propias devuelven `FILTROS_INVALIDOS`; los errores de conversión usan el manejador existente.
- 401: falta de autenticación.
- 403: perfil sin permiso.
- 404: bebida, tipo o salón inexistente; se aceptan referencias dadas de baja.

La respuesta contiene:

```text
unidad: BOTELLAS
denominador: INVITADOS_REGISTRADOS
filtros: filtros recibidos
resumen: Medidas
porBebida: [{ bebidaId, nombre, presentacion, activo, medidas }]
porTipoEvento: [{ tipoEventoId, nombre, activo, medidas }]
detalle: [{ tipoEventoId, bebidaId, medidas }]
```

Cada `Medidas` expone `eventos`, `eventosConConsumoRegistrado`, `consumoTotal`, `promedioPorEvento`, `eventosConInvitadosValidos`, `invitadosRegistrados`, `consumoConInvitadosValidos` y `porInvitadoRegistrado`.

`eventosConConsumoRegistrado` cuenta eventos distintos con una fila del consolidado para la selección, incluso si su cantidad es cero. No significa eventos con consumo positivo.

Sin eventos: total cero y promedios `null`. Con eventos y consumo cero: promedio por evento cero. Sin invitados válidos: cociente por invitado `null`; con invitados válidos y consumo cero: cero. Los promedios de cada tipo usan solo sus eventos; los de cada bebida usan toda la población seleccionada.

OpenAPI documentado mediante anotaciones del controlador.

## Seguridad y pantalla

`Permisos.CONSULTAR_ESTADISTICAS_CONSUMO` permite exclusivamente `DIRECCION`, `COORDINACION`, `ADMINISTRACION` y `COMPRAS`. No amplía permisos de operación ni cierre. Se conserva la unión de roles de la sesión.

Ruta: `/bebidas/estadisticas`, ítem «Estadísticas de consumo» del grupo Bebidas. La ruta usa `ConPermiso`; los perfiles excluidos no montan la pantalla ni solicitan catálogos o estadísticas.

La pantalla reutiliza `Card`, `Stat`, `Table`, `Input`, `Select`, `Button`, `Alert`, `EmptyState`, `Encabezado`, `Cargando` y `useDatos`.

- La consulta se envía con «Consultar estadísticas», no al editar campos.
- Cada envío monta sus resultados con clave propia; se descartan respuestas anteriores y se preservan filtros explícitos para reintento.
- Se muestran los filtros aplicados junto al resultado, aunque se edite el formulario.
- Incluye indicadores generales, tablas por bebida y tipo, y detalle tipo–bebida.
- Distingue ausencia de eventos, ausencia de registros, consumo registrado cero y cocientes no disponibles.
- Muestra eventos válidos, invitados y consumo utilizados en el cociente por invitado.
- Tablas con desplazamiento horizontal en pantallas estrechas, cabeceras multilínea y aviso de desplazamiento.
- Sin acciones de escritura ni consultas a stock o asistencia.

## Archivos creados

Los paquetes backend siguientes se ubican bajo `backend/src/main/java/ar/edu/utn/vastio/`:

- `agenda/aplicacion/ConsultaHistoricaEventoService.java`.
- `bebida/aplicacion/EstadisticaConsumoService.java`.
- `bebida/api/EstadisticaConsumoController.java`.
- `bebida/api/EstadisticaConsumoDto.java`.

Pruebas backend bajo `backend/src/test/java/ar/edu/utn/vastio/`:

- `bebida/aplicacion/EstadisticaConsumoServiceTest.java`.
- `bebida/api/EstadisticaConsumoIT.java`.

Frontend y documentación:

- `frontend/src/api/estadisticas.ts`.
- `frontend/src/features/bebidas/PaginaEstadisticas.tsx`.
- `frontend/src/features/bebidas/estadisticas.css`.
- `frontend/src/features/bebidas/PaginaEstadisticas.test.tsx`.
- `frontend/e2e/estadisticas-consumo.spec.ts`.
- `frontend/e2e/fixtures/estadisticas-consumo.sql`.
- `docs/b5-01-estadisticas-consumo.md`.

## Archivos modificados

- `backend/src/main/java/ar/edu/utn/vastio/agenda/infraestructura/EventoRepository.java`: proyección de población cerrada.
- `backend/src/main/java/ar/edu/utn/vastio/bebida/infraestructura/ConsumoEventoRepository.java`: lectura masiva escalar.
- `backend/src/main/java/ar/edu/utn/vastio/bebida/infraestructura/BebidaRepository.java`: referencias escalares del catálogo.
- `backend/src/main/java/ar/edu/utn/vastio/comun/seguridad/Permisos.java`: permiso específico.
- `frontend/src/rutas.tsx`: pantalla para el nuevo ítem.
- `frontend/src/features/layout/menu.ts`: navegación y roles.
- `frontend/src/features/layout/menu.test.ts`: listas esperadas del menú.

## Verificación de la implementación inicial

Entorno: Java 25.0.3, Node 24.22.1, PostgreSQL 17 en Docker/Testcontainers, Chrome instalado. Solo bases de prueba descartables; no se utilizaron datos de producción.

| Verificación | Resultado |
|---|---|
| Compilación backend | Aprobada |
| `mvnw.cmd -B -Dtest=EstadisticaConsumoServiceTest -Dit.test=EstadisticaConsumoIT,ConsultaConsumoIT,CierreIT verify` | 2 unitarias y 78 integraciones aprobadas, sin omisiones: estadísticas 36, B4-05 25, cierre 17 |
| Backend completo: `mvnw.cmd -B verify`, con `VASTIO_TEST_PYTHON` configurado | 49 unitarias aprobadas; 676 integraciones ejecutadas, 675 aprobadas y 1 fallida. Cero errores u omisiones. **Suite completa no aprobada** |
| Repetición aislada de `DevolucionSaldoNegativoIT`, sin modificarla | 11 integraciones: 9 aprobadas y 2 fallidas por la misma aserción aleatoria; las 2 unitarias nuevas volvieron a pasar |
| Frontend completo | 40 archivos y 346 pruebas aprobadas; repetición tras el ajuste visual también 346/346 |
| Frontend dirigido final | 36/36 (pantalla y menú), después de precisar el tipo nullable de los filtros y esperar explícitamente la respuesta obsoleta en el test |
| TypeScript `tsc -b` | Aprobado, también después del ajuste visual |
| ESLint | Aprobado, también después del ajuste visual |
| Build Vite y PWA | Aprobado, también después del ajuste visual; junto con `tsc -b` equivale al script `npm run build` |
| Playwright: estadísticas y regresión B4-05 | 8 aprobadas: 6 de estadísticas y 2 de B4-05, en escritorio y teléfono |
| Revisión visual | Capturas a 390, 768 y 1280 px; sin desbordamiento horizontal de página, tablas con desplazamiento interno |
| `git diff --check` | Sin observaciones |

Cobertura nueva backend: sumas, agrupaciones, fechas inclusivas, límites individuales, filtros combinados, exclusión de todos los estados no cerrados, ceros, referencias inactivas, invitados nulos/cero, exclusión coherente de numeradores, asistencia parcial ignorada, precisión y redondeo, validación, 401/403, conservación de tablas y lotes sin N+1. El test de lotes usa 2.001 eventos y suma de invitados superior al rango de `int`.

Cobertura nueva frontend: envío explícito y URL de filtros, resultados y grupos, estados vacíos/cero, denominador no disponible, períodos invertidos y corrección, reintento de consulta/catálogos, cuatro roles autorizados, cuatro excluidos, unión de perfiles, carga, respuestas obsoletas y solicitudes de negocio exclusivamente GET.

El E2E usa el fixture SQL sintético sobre una base exclusiva. Verifica cálculos contra valores conocidos, filtros combinados, evento cerrado sin consumo, evento no cerrado excluido, vacío, período invertido, reintento después de falla de red, acceso directo denegado y ausencia de escrituras HTTP de negocio. B4-05 se ejecutó contra un evento del mismo fixture.

### Falla preexistente de la suite global

`DevolucionSaldoNegativoIT.saldoNegativoRechazaCerosYPositivosSinConsumoNiEfectos`, línea 84, exige que todo `$.detail` no contenga `-3`. El fixture construye el nombre de bebida con `UUID.randomUUID()`, y el mensaje incluye ese nombre.

En la suite completa falló con el nombre `Z negativa 3148a273-3296-4a03-9550-71f769af0f43`. La cadena prohibida está en el UUID, no en un saldo serializado. En la repetición aparecieron `c2c55fe8-34e8-4293-b8c1-49f0a9d98df9` y `2b33b7cb-3bc9-4cf2-b698-0894a2319d55`, con el mismo resultado. El código HTTP y el código de negocio esperados habían pasado.

El archivo de esa prueba y el productor del mensaje coinciden con `main`; no se modificaron para esta historia. Queda pendiente corregir la fragilidad del fixture/aserción en un cambio autorizado por separado. No se ocultó el fallo ni se repitió la suite buscando un resultado aleatorio favorable.

### Incidencias de validación resueltas

- El primer caso nuevo de referencia inactiva usaba `desde=2039` con `hasta=2038` del helper. El backend devolvió correctamente 400; se corrigió el rango del test. La repetición dirigida aprobó todos los casos.
- La primera ejecución Vitest en sandbox no pudo leer un temporal de transformación y no ejecutó la pantalla. Se repitió fuera de ese aislamiento con el runtime compatible.
- El primer E2E falló solo en la simulación de error: `StrictMode` hacía una segunda solicitud que escapaba a una intercepción de una sola vez. Se mantuvo la falla hasta observar el error y luego se retiró para reintentar contra el servidor real. La ejecución final pasó 8/8.
- Docker estaba detenido; se inició el ejecutable instalado y se usó acceso autorizado al daemon.
- `npm ci` finalizó sin cambios del lockfile, pero su lanzador usó Node global 24.14.0 y emitió avisos de engine. Tests, TypeScript, lint, build y E2E se ejecutaron explícitamente con Node 24.22.1.
- `npm ci` reportó 8 vulnerabilidades del árbol existente (1 baja, 3 moderadas, 4 altas). No se ejecutó `npm audit fix` ni se alteraron dependencias fuera de alcance.

## Reproducción

Backend desde `backend/`, con Java 25, Docker disponible y Python configurado para el ensayo existente de resguardo:

```powershell
.\mvnw.cmd -B -Dtest=EstadisticaConsumoServiceTest -Dit.test=EstadisticaConsumoIT,ConsultaConsumoIT,CierreIT verify
.\mvnw.cmd -B verify
```

Frontend desde `frontend/`, con una versión de Node compatible con el lockfile:

```powershell
npm ci
npm test
npm run lint
npm run build
```

Para E2E: crear una base PostgreSQL 17 descartable, iniciar el backend con perfil `dev`, usuarios demo y realización automática deshabilitada; cargar `e2e/fixtures/estadisticas-consumo.sql` solo en esa base. Arrancar Vite con `VASTIO_API_TARGET` apuntando al backend de prueba. Definir `E2E_URL`, `E2E_DEMO_CONTRASENA`, `E2E_ESTADISTICAS_FIXTURE=1`, `E2E_BROWSER_CHANNEL=chrome` y `E2E_CONSUMO_EVENTO` con el id de `E2E B501 1`:

```powershell
npm run e2e -- e2e/estadisticas-consumo.spec.ts e2e/consulta-consumo.spec.ts --workers=1
```

El fixture no es idempotente: está diseñado para una base nueva. El flag E2E declara que el operador preparó esa base; las pruebas no cargan datos automáticamente.

Logs de esta ejecución conservados en la raíz del worktree como `vastio-b5-01-*.log` (ignorados por Git), y capturas/trazas en `frontend/test-results/` (también ignoradas).

## Diferencias respecto del plan y límites

- Se agregó una proyección a `BebidaRepository` para no cargar entidades ni relaciones del catálogo en el cálculo. Es una lectura del propio módulo, sin ampliar alcance.
- No fue necesario modificar `useDatos`, `Cargando`, el manejador de errores, el cierre ni componentes compartidos del sistema de diseño.
- Se agregó un fixture E2E reproducible exclusivamente sintético.
- Sin prueba de carga de un historial de producción ni benchmark de índices. El batching está cubierto por prueba, pero toda la población y las filas seleccionadas se agregan en memoria; se deberá medir si el volumen crece significativamente.
- Las cantidades son históricas congeladas; las etiquetas del catálogo y los invitados son datos vigentes según el comportamiento existente. No se incorporan snapshots nuevos.
- La suite global backend queda con una falla ajena a B5-01, detallada arriba. Esto impide afirmar que todos los checks globales están aprobados.

## Estado al terminar la implementación inicial

Cambios locales sin stage en la rama solicitada; HEAD continúa en la base `c05c8ef`. Sin commits, push, PR ni cambios directos en `main`. Pendientes: revisión del responsable, tratamiento separado de la aserción aleatoria de devolución y autorización posterior antes de publicar.

Se detuvieron los servidores locales de prueba (18081 y 15173) y se eliminó el contenedor propio `vastio-b501-e2e` con su volumen descartable. Se conservaron logs y capturas ignorados por Git. Docker Desktop queda iniciado.

Inventario final: 13 archivos nuevos y 7 modificados, todos dentro del alcance descrito. Sin archivos staged ni commits propios. `git diff --check` sin observaciones. El worktree M-10 permanece limpio y en su rama original.

## Ronda limitada de ajustes posteriores a la auditoría

Se trabaja sobre la misma rama `codex/b5-01-estadisticas-consumo`, sin commits ni publicación. La única modificación de comportamiento está en la validación del formulario de estadísticas; no se cambian fórmulas ni componentes compartidos.

### Agrupaciones

`EstadisticaConsumoIT.verificaTodasLasMedidasDeLaMatrizTipoBebidaConCerosYSinHistorial` comprueba los ocho campos de `Medidas`, no solo totales representativos:

- Tipo con dos eventos y 300 invitados válidos, con varias bebidas.
- Bebida con un registro, otra con dos registros incluyendo un cero explícito, y otra cuyo único registro es cero.
- Bebida sin ningún consumo registrado.
- Tipo con consumo pero sin invitados válidos: promedio por evento existente y por invitado nulo.
- Tipo activo sin eventos: totales cero y promedios nulos para todas las bebidas del escenario.
- Repetición con filtros de tipo y bebida sin consumo: la población sigue incluyendo los dos eventos.

Se comprueban eventos, eventos con registro, eventos con invitados válidos, suma de invitados, consumo total, consumo elegible y ambos promedios. Los valores esperados son explícitos e independientes del algoritmo de producción.

### Solo lectura con transacciones efectivamente cerradas

La prueba `consultasRepetidasNoModificanDatosNiDependenDelStock` ahora:

1. Prepara datos y token; ejecuta `EntityManager.flush()`.
2. Confirma el fixture con `TestTransaction.flagForCommit()` y termina la transacción exterior con `TestTransaction.end()`.
3. Verifica que no hay una transacción exterior activa.
4. Obtiene la fotografía inicial mediante una transacción observadora independiente.
5. Hace tres peticiones HTTP con MockMvc. Cada transacción del servicio termina antes de devolver el resultado.
6. Compara las 12 tablas mediante nuevas transacciones observadoras después de cada petición, además de comprobar respuestas idénticas y totales conocidos.

Se agregaron a las ocho tablas originales `bebida`, `cliente`, `unidad_comercializable` y `entrega`. `@DirtiesContext(AFTER_METHOD)` retira el contexto y el PostgreSQL descartable que contiene el fixture confirmado; no quedan datos confirmados contaminando las pruebas siguientes.

### Fecha incompleta o imposible

Se reprodujo en Chrome de escritorio con teclado real: borrar un segmento de fecha deja `input.value = ''` y `validity.badInput = true`. El formulario anterior, con `noValidate`, interpretaba esa cadena como filtro omitido y no presentaba el error esperado.

Corrección mínima en `PaginaEstadisticas.Consulta.consultar`: agregar `e.currentTarget.checkValidity()` a la validación existente y precisar el evento como `FormEvent<HTMLFormElement>`. Se conservan `noValidate` y el mensaje accesible propio para presentar el error del sistema de diseño. Un campo vacío intencional sigue siendo válido.

El nuevo `frontend/e2e/fechas-estadisticas.spec.ts` usa la aplicación real y API simulada para aislar el comportamiento nativo. No falsifica `validity` ni eventos: reproduce una fecha incompleta y un 31 de febrero mediante teclado, verifica que no haya solicitudes estadísticas, corrige la fecha y consulta, y finalmente comprueba que vaciarla intencionalmente omita el filtro.

Son cuatro casos: dos escenarios en dos anchos. Se fuerza modo de escritorio con teclado también en el ancho estrecho: no se presenta esta verificación como una reproducción del selector nativo móvil. La suite E2E de negocio conserva sus proyectos escritorio y teléfono reales.

También se agregó un caso Vitest para la rama de validez nativa. Allí se simula `checkValidity`, porque jsdom no implementa la edición de segmentos; la reproducción genuina corresponde al E2E.

### Verificación acotada de rendimiento

`observaHistorialSinteticoDeVeinteAniosSinConsultasPorEvento` genera en PostgreSQL descartable 3.000 eventos cerrados distribuidos entre 2070 y 2089, cinco tipos y diez bebidas por evento: 30.000 consumos. Es un volumen equivalente a veinte años a 150 eventos por año, según el contexto documentado del negocio.

Se excluye del tiempo la preparación del fixture. Se realizan cuatro peticiones MockMvc, contando sentencias SQL preparadas mediante estadísticas Hibernate. Todas verifican los resultados conocidos: 307.500 botellas, 102,5 por evento, 1.500 eventos válidos, 225.000 invitados, 153.750 botellas elegibles y 0,6833 por invitado.

Primera ejecución aprobada:

| Muestra | Sentencias SQL | Tiempo total | Tamaño JSON |
|---|---:|---:|---:|
| Primera | 6 | 1.221 ms | 22.219 bytes |
| Repetición 1 | 6 | 833 ms | 22.219 bytes |
| Repetición 2 | 6 | 853 ms | 22.219 bytes |
| Repetición 3 | 6 | 748 ms | 22.219 bytes |

Mediana de las tres repeticiones: 833 ms. No hay una aserción de tiempo dependiente del hardware; se exige un techo holgado de sentencias para detectar N+1 y se verifican los resultados en cada muestra.

En la ejecución completa se volvió a observar el mismo volumen con 6 sentencias por petición: 1.415 ms en la primera muestra y 905, 980 y 834 ms en las repeticiones, con respuestas de 22.291 bytes. Ambas ejecuciones verifican los mismos totales; no se comparó el JSON byte a byte entre instancias del fixture.

Es una observación local secuencial dentro de una transacción de prueba, con PostgreSQL real y serialización HTTP de MockMvc. No mide red/TLS, concurrencia, memoria máxima ni percentiles de producción. No justifica agregar índices, paginación ni dependencias. La retención del historial seleccionado en memoria sigue siendo una limitación a vigilar.

### Archivos de esta ronda

- Modificado: `backend/src/test/java/ar/edu/utn/vastio/bebida/api/EstadisticaConsumoIT.java`.
- Modificado: `frontend/src/features/bebidas/PaginaEstadisticas.tsx` (solo validez nativa y tipo del evento).
- Modificado: `frontend/src/features/bebidas/PaginaEstadisticas.test.tsx`.
- Nuevo: `frontend/e2e/fechas-estadisticas.spec.ts`.
- Actualizado: este documento.

`DevolucionSaldoNegativoIT` permanece idéntico a `main`, blob `09a023e9094b1c5ed44a7efbcf70a5dd735ee6dc`. No se deshabilitó ni excluyó de la ejecución completa. Su falla conocida por `-3` en un UUID debe corregirse mediante un cambio independiente.

Resultados finales de esta ronda:

| Verificación | Resultado |
|---|---|
| Backend dirigido B5-01 | 2 unitarias y 38 integraciones aprobadas, sin omisiones |
| Matriz final recompilada, con grupo cuyo único registro es cero | 2 unitarias y 1 integración dirigida aprobadas; es una revalidación del mismo caso, no un caso adicional al conteo de 38 |
| Backend completo, incluyendo devolución sin cambios | 49 unitarias aprobadas; 678 integraciones ejecutadas, 677 aprobadas y 1 fallida; cero errores y omisiones. **Suite global no aprobada** |
| Frontend completo, repetición final con un worker | 40 archivos y 347 pruebas aprobadas, sin modificar tests existentes ni sus timeouts |
| E2E integrado final | 12 aprobadas: estadísticas y B4-05 contra backend real (8) y fechas nativas con API simulada (4) |
| TypeScript, ESLint y build Vite/PWA | Aprobados |

El fallo global fue nuevamente `DevolucionSaldoNegativoIT.saldoNegativoRechazaCerosYPositivosSinConsumoNiEfectos`, línea 84, con el nombre `Z negativa 0e7a5c43-3306-4347-888c-9864766029dd`. El código HTTP y el código de negocio esperados pasaron; `-3` aparece en el UUID incluido en el mensaje. No se modificó ni se excluyó esa prueba.

La ejecución completa comenzó antes de agregar la última comprobación de grupo cuyo único registro es cero; esa matriz final se recompiló y aprobó de forma dirigida. Las fórmulas y el resto de las pruebas backend no cambian entre esas ejecuciones.

Incidencias de esta ronda que se conservan en los logs:

- Una ambigüedad de inferencia de tipos de AssertJ en el nuevo observador transaccional se resolvió asignando el resultado a una variable antes de comprobarlo. La siguiente ejecución dirigida aprobó 2 unitarias y 38 integraciones.
- La reproducción inicial con teclado confirmó el defecto en escritorio; en emulación móvil el selector nativo no permitía esa edición parcial. Los cuatro casos nuevos especifican por ello modo de teclado de escritorio, en dos anchos, y lo documentan explícitamente.
- La primera suite frontend completa ejecutó 347 casos: 346 aprobados y un timeout de 5 segundos en `PestanaPreparacion.test.tsx`, caso «escanea sin sumar cantidades ni duplicar renglones». Ese archivo coincide con `main` (blob `abe8d3276c5a6f140fd15bd98b51e24171a8d948`). No se modificó el test ni se aumentó su timeout. La repetición posterior con un solo worker y sin las otras verificaciones activas aprobó los 347 casos en 317,22 s. El timeout no volvió a aparecer en esa ejecución; no se afirma una causa raíz demostrada.
- El primer E2E integrado tuvo 4 casos nativos aprobados y 8 fallos de ingreso porque Vite apuntaba por omisión al puerto 8080. Se corrigió la configuración del proceso con `VASTIO_API_TARGET=http://localhost:18081`, sin cambios de configuración versionados, para repetir contra el backend descartable.

Para reproducir esta ronda, los comandos dirigidos de backend son:

```powershell
.\mvnw.cmd -B -Dtest=EstadisticaConsumoServiceTest -Dit.test=EstadisticaConsumoIT verify
.\mvnw.cmd -B verify
```

En frontend se usa `npm test -- --maxWorkers=1` para la repetición completa secuencial, sin modificar los timeouts. Para los E2E, con el entorno sintético y variables descritos arriba:

```powershell
npm run e2e -- e2e/estadisticas-consumo.spec.ts e2e/consulta-consumo.spec.ts e2e/fechas-estadisticas.spec.ts --workers=1
```

Logs de esta ronda: `vastio-b5-01-ajustes-*.log`, ignorados por Git. Los servidores locales 18081 y 15173 fueron detenidos después del E2E final; se eliminó el contenedor propio `vastio-b501-ajustes-e2e` y su volumen descartable. Las bases Testcontainers se retiraron al cerrar los procesos de pruebas.

### Estado de entrega tras los ajustes

Rama `codex/b5-01-estadisticas-consumo`, HEAD `c05c8efa9f079502481c19ac706394a49b05f7e5`, sin commits propios ni upstream. Cambios sin stage, sin push ni PR. Respecto de `main` se mantienen 7 archivos modificados y ahora 14 nuevos (se agregó únicamente el E2E de fechas al inventario inicial). `git diff --check` sin observaciones; manifiestos y lockfile sin cambios.

Esta ronda modifica cuatro archivos ya incluidos en B5-01 y agrega uno, detallados arriba. Ninguna fórmula estadística fue modificada. Pendientes: revisión del responsable, corrección independiente de la aserción aleatoria de devolución y posterior autorización para commits/publicación. No se presenta la suite global backend como aprobada.
