# M-06 — Validación real en entorno aislado (29/10/2026)

## Entorno y corrección

Base inicial `c50b4dc`. Corrección en `codex/m06-diagnostico-oauth`: `cc6acff` evita la doble descompresión gzip de OAuth; `ccdf920` agrega causas/fases sanitizadas. No cambia publicación, identidad ni recuperación. El JSON de la cuenta de servicio se usa únicamente desde su archivo privado, sin mostrar/copiar claves, tokens ni contraseñas.

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

**Estado:** M-06 no terminada; exportación confirmada, idempotencia, actualización y retirada pendientes. Antes de reanudar se requiere una decisión explícita tras el resultado incierto, conforme a la condición del usuario. El PR #51 sigue abierto, Draft, sin fusionar, en c50b4dc; no se modifica, aprueba ni cierra. La revisión debe considerar las correcciones y el resultado real completo, sin acreditar aceptación con mocks.
