# M-06 — Validación real en entorno aislado (29/10/2026)

## Entorno y corrección

Base inicial `c50b4dc`. La versión de backend validada es `a35dbea`, en la rama de corrección `codex/m06-diagnostico-oauth`: incorpora `cc6acff` para evitar la doble descompresión gzip y corrige la instrumentación inicial de `ccdf920` manteniendo causas/fases sanitizadas. No cambia publicación, identidad ni recuperación. El JSON de la cuenta de servicio se usa únicamente desde su archivo privado, sin mostrar/copiar claves, tokens ni contraseñas.

El worktree limpio `C:\Users\tomic\VASTIO-CALENDAR-TEST` pasó a una rama local independiente `codex/m06-validacion-real` en `ccdf920`. Se preservaron todas las variables del proceso anterior en memoria y sus argumentos de Spring/JVM. Se verificaron directorio, puerto, URL de base e instalación antes de detener exclusivamente el backend de pruebas. Se ejecutó `mvnw.cmd clean package -DskipTests` y se arrancó el jar con la configuración conservada. Salud UP en localhost:8080. Frontend 5173 y PostgreSQL 5433 conservaron sus procesos originales. El repositorio principal/main y su base no se modificaron.

## Publicación previa y acceso real: comprobados

Se consultó `vastio_calendar_test` mediante `vastio-calendar-db` dentro de una transacción READ ONLY. Evento `EV-2026-00001`, estado comercial SENADO. Publicación ERROR, pendiente EXPORTAR, error RED_GOOGLE, dos operaciones previas y ninguna huella de éxito confirmada. La instalación coincide con `vastio-calendar-test-local`; se conservaron calendar_id/google_id existentes, sin regenerar identidades. Tras el reinicio se verificaron igualdad de esos identificadores y del hash de los registros de auditoría existentes.

Se obtuvo el token OAuth con el transporte corregido y se consultó Google únicamente por GET. El calendario se identificó exactamente como `Vastio - Pruebas`; la identidad persistida devolvió AUSENTE y el filtro por propiedad `vastio-id` devolvió cero entradas activas.

Google devuelve el rol `writerWithoutPrivateAccess`. Este rol permite leer/escribir eventos no privados y oculta detalles de eventos privados; no requiere elevar permisos para probar el payload normal de M-06. Se verificó en la [documentación oficial de compartición](https://developers.google.com/workspace/calendar/api/concepts/sharing). La primera clasificación auxiliar solo contemplaba owner/writer; se corrigió para reconocer el rol oficial, sin modificar ACL ni autorización de producción.

La lectura usa un test optativo `ValidacionCalendarLecturaTest`, desactivado salvo variable explícita. Sus únicas operaciones remotas son events.list/events.get. Verifica nombre del destino y propiedad de la identidad; si hay entrada, compara título, descripción pública exacta, salón, intervalo completo, zona Córdoba y ausencia de invitados/adjuntos. Cuenta las entradas activas de esa identidad. No importa campos ni escribe en DB o Google.

## Pruebas ejecutadas

- Tanda focal: 26 tests, cero fallos/errores/omisiones: 24 regresiones simuladas, obtención OAuth real y lectura Calendar real.
- Verificación del rol oficial y nueva lectura: 1 test aprobado, AUSENTE y cero entradas activas.
- El proceso auxiliar inicialmente no pudo imprimir un carácter de la salida en la consola Windows; Maven había terminado BUILD SUCCESS y guardó su evidencia. Se ajustó solo la codificación de salida del auxiliar; no se ocultó una falla de pruebas.
- Compilación limpia del backend de validación: BUILD SUCCESS; no se reutilizaron clases anteriores. Los tests se ejecutaron previamente en el worktree de corrección y el empaquetado se hizo con skipTests.
- `git diff --check`: aprobado. No se ejecutaron suites completas ni CI de estos commits durante esta fase.

## Único intento autorizado y detención ante resultado incierto

La sesión de Dirección en Vastio pudo renovarse y se conservó después del reinicio. La ficha muestra el error histórico pendiente; no se borró ni reseteó para ocultarlo. No se pulsó exportar/reintentar/retirar ni se llamó directamente a escrituras Google.

El usuario autorizó explícitamente el ciclo completo mediante Vastio, conservando identidad y deteniendo cualquier resultado incierto. Se pulsó exportar una sola vez desde la sesión autenticada de Dirección. Vastio devolvió INCIERTO / RESULTADO_NO_PERSISTIDO. Se detuvo el ciclo: no se repitió la exportación ni se modificó hora particular, actualizó o retiró la entrada.

La lectura posterior confirmó identidad AUSENTE y cero entradas propias activas. DB conserva SENADO, la misma identidad y los dos registros anteriores, más REINTENTO e INCIERTO/RESULTADO_NO_PERSISTIDO (cuatro operaciones en total). No se borró historial ni se restableció el estado.

Un test optativo de solo lectura del cliente real reprodujo NullPointerException en CALENDAR_LISTAR. La instrumentación de ccdf920 suponía que HttpCredentialsAdapter instalaba un HttpExecuteInterceptor; la versión fijada implementa HttpRequestInitializer/HttpUnsuccessfulResponseHandler y autentica en initialize(). El interceptor era null, por lo que el fallo sucedía antes de enviar el GET y antes de INSERT. El catch genérico de RuntimeException lo convirtió conservadoramente en RESULTADO_NO_PERSISTIDO; ese rótulo no demostró un fallo de PostgreSQL.

Se corrigió la instrumentación para envolver únicamente auth.initialize(request), sin instalar ni depender de un interceptor. La clasificación sanitizada de fallos OAuth permanece. Se agregaron regresiones con el adaptador real y un token sintético contra transporte simulado, y otra para IOException OAuth. La nueva tanda de 28 tests pasó sin fallos/omisiones, incluyendo OAuth y lectura real; no efectuó escrituras Calendar.

La lectura optativa del cliente de producción, que antes fallaba con NullPointerException, pasó después del arreglo y confirmó AUSENTE sin crear nada. El commit `a35dbea` se incorporó por fast-forward en la rama de validación; se reconstruyó desde cero y se reinició únicamente el backend de pruebas con su entorno/argumentos preservados. Salud UP; permanecen INCIERTO / RESULTADO_NO_PERSISTIDO, cuatro operaciones y los identificadores e historial intactos. No se ejecutó recuperación ni otra escritura Google.

Regresión posterior: 26 unitarias simuladas y 38 integraciones CalendarIT con PostgreSQL descartable Testcontainers, todas aprobadas sin omisiones; BUILD SUCCESS. No se utilizó vastio_calendar_test para esas integraciones. La comprobación real de contenido, idempotencia, actualización y retirada sigue pendiente; no se acredita con las pruebas simuladas.

## Reanudación expresamente autorizada y ciclo real completado

El usuario autorizó una única recuperación y el ciclo completo después de conocer el resultado incierto, la corrección y la ausencia remota confirmada. Todas las escrituras se hicieron mediante Vastio autenticado; los auxiliares de Google únicamente efectuaron GET. No se regeneró la identidad ni se editaron filas para forzar un éxito.

| Etapa | Vastio / auditoría | Google real |
|---|---|---|
| Recuperación manual desde la ficha, Dirección | EXPORTADO / NINGUNA / sin error. REINTENTO + CREACION_CONFIRMADA; 6 operaciones acumuladas. | PRESENTE, propia, una entrada activa. |
| Repetición controlada de POST en la API autenticada de Vastio | EXPORTADO / NINGUNA. Misma identidad, hash de auditoría y 6 operaciones; no se generó otra operación remota. | Sigue una entrada activa, con contenido exacto. |
| Hora particular 12:30 guardada en el formulario | Estado comercial SENADO; pendiente ACTUALIZAR; siguen 6 operaciones. No se publicó automáticamente. | No se solicitó escritura remota; se verificó el pendiente local antes de actualizar. |
| Actualización manual desde la ficha | EXPORTADO / NINGUNA / sin error. SOLICITUD + ACTUALIZACION_CONFIRMADA; 8 operaciones. | Misma identidad y una entrada activa; descripción con hora particular 12:30. |
| Retirada manual desde la ficha | RETIRADO / NINGUNA / sin error, retiro_solicitado=false. SOLICITUD + RETIRADA_CONFIRMADA; 10 operaciones. | ELIMINADA y cero entradas propias activas. |

En creación, repetición y actualización se compararon contra la proyección pública exacta: título `EV-2026-00001 · Avril · Mediodía`, tipo Egresados, salón/ubicación Avril, descripción prevista, fecha 07/11/2026, inicio 12:00, fin 18:00 y zona `America/Argentina/Cordoba`. La hora particular solo cambia la descripción, no el intervalo del turno. Se comprobó ausencia de invitados y adjuntos. La igualdad exacta con la proyección permitida confirma que no se incorporaron nombre del cliente/evento libre, documento del firmante, importe de seña ni otros datos de la ficha.

La repetición usó un login normal de Dirección en la API de Vastio y una única petición POST, nunca una escritura directa a Google ni tokens fabricados. Contraseña y tokens se mantuvieron solo en memoria, sin imprimirlos, y se cerró únicamente esa sesión auxiliar. Las otras escrituras se realizaron desde la sesión de Dirección de la UI. No se modificaron credenciales, ACL, permisos globales ni configuración de seguridad.

La primera comparación del evento presente detectó ClassCastException en el auxiliar: GenericJson representa JSON null mediante un sentinel. Se corrigió la lectura opcional con Data.isNull y se agregó una regresión. No fue un fallo de la publicación ni se repitió una escritura por esa falla del auxiliar. Las comparaciones posteriores pasaron. El primer guardado del control nativo de hora no produjo cambio; se completó la entrada con teclado, se verificó 12:30 y se guardó. Solo la modificación efectiva agregó un registro al historial comercial (de 2 a 3).

## Estado final y entrega

- Backend de pruebas saludable en localhost:8080, código `a35dbea`; rama local `codex/m06-validacion-real`. Frontend y PostgreSQL originales de pruebas siguen en sus mismos procesos/puertos. No se reiniciaron servicios ajenos ni se borraron volúmenes.
- Evento ficticio permanece SENADO, con hora particular 12:30. La publicación queda RETIRADO, sin pendiente ni error y conservando su identidad. Los dos registros de auditoría anteriores se verificaron por hash y permanecen dentro de las diez operaciones acumuladas. No se canceló ni eliminó el evento comercial.
- Se guardaron evidencias sanitizadas de estados/lecturas y capturas de Exportado y Publicación retirada en el directorio local de validación, fuera del repositorio y sin claves/tokens. Los JSON de lectura remota contienen solo nombre del calendario, rol, presencia, coincidencia de contenido, intervalo y cantidad de entradas.
- Pruebas focales de la corrección: 26 unitarias simuladas y 38 CalendarIT aprobadas; además, pruebas optativas reales de OAuth, cliente de producción y lecturas por etapa aprobadas. La nueva regresión del null JSON también pasó. El resultado de CI del commit final se informa en el PR de correcciones; no se sustituye por resultados de commits anteriores.
- El PR #51 permanece abierto, Draft, sin fusionar, en c50b4dc. La corrección se entrega en rama independiente para revisión, sin modificar main ni la rama del #51, sin force-push, aprobación, cierre o merge automático.

**Alcance acreditado:** el ciclo real autorizado de recuperación/exportación, idempotencia, actualización, privacidad y retirada está COMPLETADO en el calendario aislado `Vastio - Pruebas`. No se declara aprobada toda M-06 ni autorizado su merge: siguen sujetos a revisión humana los cambios y la integración con otros PR. No se ejecutaron en Google real pruebas de permisos insuficientes (no se alteraron ACL), conflicto por edición externa, turno nocturno o activación/desactivación; cuentan con cobertura automatizada donde corresponde, pero no se presentan como aceptación externa ejecutada.
