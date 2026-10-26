# B4-02 — Validación del lector de códigos

Revisión del 26/10/2026. No se realizaron pruebas con cámaras físicas durante esta revisión. Los resultados de dispositivos se completan únicamente con evidencia del equipo.

## Fuentes y alcance

- [Definición del Producto, sección 4, B4-02 / UI-30](https://docs.google.com/document/d/1ZU7B_aJO2UCm450wMsh8IxC8qaC7bXYJtvxsS1cfV4U/edit): cámara de celular/tablet Android e iOS; catálogo, ingreso y salida a barra; identificación sin contar unidades; ingreso manual siempre visible; código desconocido informado.
- [Tablero, PRODUCT BACKLOG y SPRINT ACTUAL](https://docs.google.com/spreadsheets/d/1TpSxwIanEqn-N-CXVGyU7tKKPrcnENybjTIe3FhqqqI/edit): B4-02 En prueba / Parcial, integrada por #24 y #27. Un compromiso de validación física figura Cumplido y una decisión del 23/10 dice que Tomi lo probó; no incluye dispositivo, versión ni evidencia de los escenarios. Se conserva esa información como antecedente, sin convertirla en resultado de esta revisión ni modificar el tablero.
- `docs/sprint-3.md`, `contexto-del-negocio.md`, `perfiles-y-permisos.md`, `der/diccionario-de-datos.md`, `maquina-de-estados.md`; diseño UI-30 y sistema de diseño.

Los códigos del catálogo son dígitos de 8 a 14 caracteres. El lector conserva EAN-13, EAN-8, UPC-A, ITF y CODE-128; no se agregan formatos ni verificación adicional de dígito de control al negocio. Las lecturas parciales o que no decodifican no se emiten como resultados. Las pantallas buscan en el catálogo ya cargado; no hay un GET de búsqueda por cada fotograma. El endpoint de consulta por código existente conserva sus permisos y 404.

## Trazabilidad de requisitos

| Requisito | Estado y evidencia |
|---|---|
| Abrir desde catálogo, ingreso y salida/retiro | Implementado; componentes y pruebas de pantallas. Preparación también reutiliza el lector. |
| Identificar sin contar ni guardar | Implementado y comprobado por pruebas de ingreso, barra y preparación: agrega renglón con cantidad 0, evita repetir bebida; POST solo al confirmar el formulario. |
| Código desconocido y elección manual | Implementado y probado en ingreso/barra; catálogo permite asociar códigos nuevos con validación local y backend. |
| Códigos inválidos, repetidos y permisos | Implementado; validación del catálogo y BebidaIT, IngresoIT, SalidaBarraIT y suites vinculadas. |
| Cámara trasera preferida, reproducción inline/sin audio, HTTPS | Implementado y probado con mocks; eficacia óptica y selección física pendientes. |
| Cleanup, cierre/reapertura y StrictMode | Corregido y comprobado mediante promesas diferidas, pistas simuladas y E2E con dispositivo simulado. |
| Android/iOS, orientación, foco, uso prolongado, pantalla negra física | Implementado sin validación física suficiente: pendiente de matriz siguiente. |
| UI-30 a pantalla completa | La implementación usa el Dialog responsive existente con visor; la descripción del prototipo dice pantalla completa. Se conserva el diseño existente, y el equipo debe evaluar su usabilidad en la prueba física. |

No se encontró una funcionalidad de negocio de B4-02 documentada y totalmente ausente. La validación física sigue siendo condición de cierre.

## Matriz de dispositivos

Registrar también responsable, fecha/hora, commit exacto y evidencia por escenario. Probar el build de producción con HTTPS y el servidor de desarrollo con StrictMode para comparar el defecto.

| Dispositivo | SO y versión | Navegador y versión | Cámara utilizada | URL y protocolo | Resultado | Observaciones |
|---|---|---|---|---|---|---|
| Android físico (modelo pendiente) | Pendiente | Chrome, pendiente | Trasera, confirmar | HTTPS, pendiente | NO EJECUTADO | Completar escenarios 1–15 |
| iPhone físico (modelo pendiente) | iOS, pendiente | Safari, pendiente | Trasera, confirmar | HTTPS, pendiente | NO EJECUTADO | Completar escenarios 1–15 |
| Escritorio con webcam (modelo pendiente) | Pendiente | Chrome/Edge, pendiente | Webcam, confirmar | HTTPS o localhost, pendiente | NO EJECUTADO | No sustituye Android/iOS |

## Preparación

Usar una base de prueba, una bebida activa con código de botella y caja y un código numérico válido inexistente. Para salida/retiro, usuario Barra autorizado, evento En curso y entrega/barra disponible; catálogo e ingreso con Compras/Administración/Dirección/Coordinación. Mantener cantidades inicialmente en cero. Anotar stock y cantidad de movimientos antes de escanear; no confirmar operaciones reales por accidente. La URL LAN con HTTP no es contexto seguro aunque funcione localhost en la PC. Usar certificado confiable para el celular.

Cada evidencia debe mostrar escenario, dispositivo/versión, commit y resultado; no incluir tokens, contraseñas ni imágenes de personas. Para cámara activa/inactiva registrar el indicador del navegador/SO. Si falla, registrar permiso, `paused`, `readyState` del video y de las pistas sin volcar códigos ni frames en logs de producción.

## Escenarios manuales

| # | Pasos | Resultado esperado | Evidencia necesaria |
|---|---|---|---|
| 1 | Borrar permisos del sitio, abrir lector y conceder acceso. | Solicita permiso, muestra cámara; opción manual visible. | Video de permiso y visor; cámara usada. |
| 2 | Cerrar y abrir con permiso concedido. | Imagen estable sin diálogo repetido innecesario. | Video de apertura y 30 s de imagen. |
| 3 | Revocar permiso, abrir y denegar; habilitar y reintentar. | Mensaje comprensible, manual disponible; reintento recupera captura. | Capturas del mensaje y video de recuperación. |
| 4 | Abrir y cerrar diez veces por X, Esc cuando haya teclado y elección manual. | Una captura por apertura; indicador de cámara se apaga tras cerrar. | Video y contador/indicador de pistas. |
| 5 | Leer botella y caja conocidas en catálogo, ingreso, salida y retiro adicional. | Identifica/asocia bebida correcta; no cuenta unidades ni guarda movimiento; se carga cantidad manualmente. | Código de fixture, renglón y stock/movimientos antes/después de solo escanear. |
| 6 | Leer código válido inexistente en ingreso y salida. | Informa que no está en catálogo; permite elegir a mano. | Mensaje y selección manual. |
| 7 | Mantener mismo código frente a cámara, retirarlo y volverlo a leer después de 2 s; alternar dos códigos de la misma bebida. | Suprime repetición inmediata; ninguna bebida se duplica ni cambia cantidad existente. | Video, renglones, cantidades y ausencia de POST/movimientos. |
| 8 | Abrir y cerrar inmediatamente mientras espera permiso o inicia; luego conceder. | No reaparece video ni cambia formulario; stream tardío se detiene. | Video e indicador cámara tras resolver permiso. |
| 9 | Navegar a otra ruta durante captura. | Libera cámara; no llegan lecturas a la pantalla anterior. | Video e indicador. |
| 10 | Regresar y reabrir tras 8/9. | Cámara nueva estable; limpieza anterior no la oscurece. | Video de regreso y 30 s de imagen. |
| 11 | Interrumpir cámara (revocar permiso/desconectar webcam/otra aplicación, según SO) y reintentar. | Informa fallo recuperable, manual disponible y sin stream huérfano. | Método de interrupción, mensaje y recuperación. |
| 12 | Girar vertical/horizontal con lector abierto y leer en ambas orientaciones. | Visor y controles utilizables; lectura correcta sin captura duplicada. | Video en ambas orientaciones. |
| 13 | Mantener abierto 5 min, alternar códigos y cerrar. | Imagen/lectura estables; no acumulación de pistas y cámara liberada al cerrar. | Video/resumen temporal y estado final de pistas. |
| 14 | Repetir 1/4/8/10 en desarrollo con StrictMode y en build de producción, observando desde el primer instante. | No queda negro después de milisegundos; mensaje explícito si captura/reproducción falla. | Video desde antes de apertura; commit, modo, navegador y tiempos. |
| 15 | Elegir a mano con y sin permiso; completar cantidad, revisar y confirmar una sola vez en base de prueba. | Conserva formulario; operación y cantidad exactamente las confirmadas; reglas de permisos/negocio se mantienen. | Formulario, respuesta API y stock/movimientos esperados. |

Además: cámara inexistente/ocupada, código ilegible/incompleto (sin renglón), código inválido al asociar (rechazo), caída de API al cargar/guardar (error sin pérdida incoherente), controles táctiles y teclado. Verificar foco dentro del modal y retorno al control de apertura; cualquier defecto general del Dialog se registra aparte.

## Diagnóstico y correcciones

El original conserva StrictMode en main.tsx y ya tenía muted/playsInline/autoPlay declarativos. No se encontraron la eliminación de StrictMode ni webkit-playsinline/logging reportados en las ramas remotas accesibles ni en el historial del lector. El trabajo no publicado de otro integrante queda fuera de lo accesible; no se sobrescribió ninguna rama.

La prueba de regresión ejecutada contra el original falla porque una lectura obsoleta llega a onRead tras el cleanup. Además, dos efectos de StrictMode recibían el mismo video. En @zxing/browser 0.2.1, stop de decodeFromStream limpia el video recibido: una resolución vieja podía limpiar el elemento de una captura nueva. Es una carrera demostrada con simulaciones y revisión del código de dependencia; no demuestra por sí sola la causa del reporte físico.

Se conserva ZXing y sus formatos, con un adaptador pequeño que posee getUserMedia, configura el video antes de play, espera su promesa y usa scan para la decodificación. Cada efecto tiene video y AbortController propios: evita compartir srcObject, ignora lecturas/errores obsoletos y libera pistas al cancelar o fallar, incluso cuando el permiso llega tarde. Se detecta interrupción de pista/pausa/final del video, con mensaje y reintento. NotReadableError (cámara ocupada/no legible) ahora permite reintentar, sin afirmar que no existe cámara. No hay logging nuevo de códigos, frames ni datos sensibles; StrictMode permanece intacto.

Archivos de producción: frontend/src/ds/components/lector.tsx y camaraLector.ts. Pruebas nuevas: LectorCiclo.test.tsx, LectorCamara.test.tsx y e2e/lector.spec.ts. Mocks adaptados a getUserMedia/play/scan en PaginaIngresoBebida.test.tsx, PaginaBarra.test.tsx y PestanaPreparacion.test.tsx; mantienen las aserciones del negocio. No se modifica el backend productivo, migraciones, dependencias ni formularios de negocio. La única modificación de backend es la fixture de prueba detallada en el apartado de CI.

Referencias técnicas: [React StrictMode](https://react.dev/reference/react/StrictMode), [autoplay MDN](https://developer.mozilla.org/en-US/docs/Web/Media/Guides/Autoplay), [video Safari](https://developer.apple.com/documentation/webkit/delivering-video-content-for-safari), [ZXing BrowserCodeReader](https://github.com/zxing-js/browser/blob/master/src/readers/BrowserCodeReader.ts). Se inspeccionó también la versión instalada del lockfile; la documentación externa orienta, no acredita compatibilidad física.

## Criterios de cierre

Para integrar la corrección: suite frontend/backend y controles estáticos/build aprobados, E2E relevante sin fallos de producto, diff acotado, CI aprobado y revisión humana. Para marcar B4-02 cumplida: además ejecutar matriz Android/Chrome e iPhone/Safari con evidencia de escenarios 1–15, sin pantalla negra ni cámaras huérfanas, con identificación correcta y cantidades manuales; registrar versión/commit y resolver discrepancia documental de estados. Las pruebas simuladas no sustituyen esas comprobaciones. No se modifica automáticamente el tablero ni se hace merge.

## Accesibilidad y observaciones fuera de alcance

Se verificó por inspección que Dialog ofrece rol, título asociado, foco inicial, botón Cerrar etiquetado y Escape para el diálogo superior. El Dialog compartido no implementa una trampa de foco ni restauración explícita al botón que abrió el modal; es una limitación general del sistema de diseño, también presente antes de B4-02. Se documenta para seguimiento separado y se conserva el componente compartido. Las pruebas móviles simuladas no acreditan tamaño táctil, orientación física ni comportamiento de Safari.

## Validaciones automatizadas de esta revisión

Entorno: Node 24.22.1 de runtime local (PATH solo de la sesión), JDK 25.0.3, Maven 3.9.16 y PostgreSQL 17.11 con Testcontainers. No se cambian dependencias ni configuración global.

- `npm test`: 388/388 pruebas, 44 archivos, sin omitidas. Incluye once regresiones nuevas de cámara/ciclo de vida y las suites de catálogo, ingreso, barra y preparación.
- `mvn -B verify`: BUILD SUCCESS; 49 unitarias y 759 integraciones, sin fallos ni errores. Una integración de resguardo externo se omitió en esa ejecución por faltar VASTIO_TEST_PYTHON.
- `VASTIO_TEST_PYTHON=<runtime Python> mvn -B -Dit.test=MigracionProveedoresIT failsafe:integration-test failsafe:verify`: 7/7 aprobadas, sin omitidas, incluido el caso anterior con dump y restauración real. El conjunto de reportes finales suma 49 unitarias y 759 integraciones aprobadas, sin omitidas; no se afirma que la primera ejecución completa tuvo cero omitidas.
- Flyway V1–V13 y suites de migraciones: sin cambios de esquema ni alteraciones de migraciones existentes. Se comprueban en PostgreSQL real; no se sustituye por H2.
- E2E del lector: 2/2 aprobados, Edge escritorio y teléfono emulado, contra Vite de desarrollo con StrictMode; getUserMedia y play simulados, ZXing real sobre frames vacíos. Comprueba apertura, propiedades móviles, cierre, liberación y reapertura. No prueba decodificación de una imagen de un código ni una cámara física.

Intentos previos, conservados para trazabilidad: la regresión contra el original falló por callback obsoleto; instalación offline encontró un paquete fuera de caché y se completó npm ci normal; Node global 24.14 avisó incompatibilidad de engines, por eso se usó runtime compatible; Vitest aislado encontró ENOENT de temporales y Mockito no pudo abrir el pipe de attach en sandbox. Se repitieron fuera del sandbox. La primera suite completa tuvo 386 aprobadas y un timeout de 5 s en DialogoCancelar (ajeno al lector); una ejecución intermedia tuvo tres fallos de pruebas nuevas por codificación UTF-8 y una expectativa editada mientras ya corría la suite. La ejecución final indicada arriba incluye sus correcciones y no modifica timeouts ni debilita aserciones existentes.

Los primeros E2E del lector no alcanzaron el servidor iniciado dentro del sandbox (ERR_CONNECTION_TIMED_OUT); al ejecutar Vite y Edge en el mismo entorno pasaron. Los E2E de stack inicialmente omitieron seis casos por faltar las variables de demo aislada; esos resultados no se cuentan como aprobaciones del circuito completo.

Los logs locales se conservan fuera del repositorio en el directorio padre del worktree con prefijo `b4-02-`: regresion-antes, frontend-verificado, camara-final, backend-final, resguardo, lint-final, build-final, e2e-verificado y e2e-stack. Los reportes XML de backend y los traces Playwright quedan en target y test-results (ignorados por Git).

### Resultado final local

- `npm run lint`: aprobado, sin advertencias de ESLint.
- `npx tsc -b` y `npm run build` (incluye tsc -b, Vite y PWA): aprobados. El plugin PWA informa tiempos de callbacks; no es un fallo de compilación.
- E2E con stack real: 3/3 aprobados en Edge escritorio sobre una base PostgreSQL descartable exclusiva y backend/frontend de esta rama. Se ejecutaron objetivo-sprint-3, objetivo-sprint-3-operacion y preparacion-evento con un worker y credenciales demo efímeras. Incluyen catálogo → ingreso → existencias/recuento, salida → retiro adicional → devolución → cierre/consumo/remanente y preparación con respuesta perdida/idempotencia. Usan selección manual, sin cámara física. La configuración temporal que selecciona Edge y un worker se elimina antes de publicar; no se modifica playwright.config.ts.
- E2E del lector sobre la versión final: 2/2, escritorio y teléfono emulado; cámara simulada. Total de escenarios E2E aprobados de ambos grupos: 5.
- `git diff --check`: aprobado.

Base: main remoto a0262bcd5b1c7ca7b5f6fc33f3c2496f06553bc4, actualizado con fetch antes de investigar. Worktree limpio al inicio, independiente, rama codex/b4-02-validacion-lector. Se revisan diff y archivos nuevos antes del commit; push normal y PR contra main, sin merge, aprobación ajena ni modificaciones del tablero. El PR y CI contienen la trazabilidad remota del commit publicado.

Conclusión local: APTO PARA MERGE de la corrección, sujeto a revisión humana y CI. B4-02 NO está lista para marcarse Cumplida en esta revisión: faltan pruebas físicas documentadas en Android/Chrome e iPhone/Safari, incluida la ausencia del defecto de pantalla negra. No se asegura haber solucionado el reporte en hardware real.

### Incidencia de CI y corrección reutilizada

La ejecución [CI #91](https://github.com/tomicaballeroutn-oss/SIN-G10-VASTIO/actions/runs/40372305609), sobre 3574aa4, aprobó Frontend y falló Backend: 759 integraciones, 1 fallo, cero errores/omitidas. DevolucionSaldoNegativoIT.saldoNegativoRechazaCerosYPositivosSinConsumoNiEfectos esperaba que el mensaje no incluyera `-3`, pero el UUID aleatorio del nombre era `cd5a12d8-3bc3-4999-944d-552f3769fa91`. El mensaje nombraba la bebida y no revelaba el saldo; la colisión producía un falso positivo.

Para desbloquear esta validación se reutiliza mediante cherry-pick -x el commit 051ae7094e96aebb914fa4b5982a48ee45d9f8a4, ya existente en el PR #46, en vez de modificar aquella rama o reconstruir su corrección. Únicamente elimina guiones del UUID sintético; conserva la aserción de privacidad, cantidades negativas, datos únicos y todas las comprobaciones transaccionales. No se incorporan los otros cambios de A7-01 ni se modifica producción backend. El nuevo commit preserva autoría y procedencia. Este es un defecto de prueba preexistente ajeno a B4-02 que impidió completar CI.

La regresión local posterior de DevolucionSaldoNegativoIT pasó 11/11, sin fallos, errores ni omitidas (`mvn -B -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=DevolucionSaldoNegativoIT verify`; solo se seleccionaron esas integraciones, sin repetir los unitarios ya aprobados). Log: b4-02-fixture-regresion.log. La publicación incluye este ajuste de fixture y exige una nueva ejecución completa de CI; el fallo de #91 permanece visible en el historial.
