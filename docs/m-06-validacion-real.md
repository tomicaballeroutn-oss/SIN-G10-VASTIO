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

## Operaciones de escritura: pendientes de autorización explícita

La sesión de Dirección en Vastio pudo renovarse y se conservó después del reinicio. La ficha muestra el error histórico pendiente; no se borró ni reseteó para ocultarlo. No se pulsó exportar/reintentar/retirar ni se llamó directamente a escrituras Google.

Se solicitó autorización para una exportación mediante Vastio, comprobación de contenido e idempotencia y, si se confirma el éxito, cambiar la hora particular ficticia a 12:30, actualizar y retirar la publicación. El intervalo exportado seguirá siendo el turno completo: 07/11/2026, Avril, Mediodía, 12:00–18:00, America/Argentina/Cordoba. Cualquier resultado incierto detiene el ensayo: no hay reintentos automáticos.

Hasta recibir la autorización, exportación, actualización y retirada reales permanecen PENDIENTES. M-06 no se declara terminada. El PR #51 sigue abierto, Draft, sin fusionar, en c50b4dc; no se modifica, aprueba ni cierra. La revisión debe considerar los commits OAuth y los resultados finales del ensayo, cuando se autorice y complete.
