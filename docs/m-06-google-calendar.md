# M-06 — Publicación manual en Google Calendar

## 1. Objetivo y alcance

Exportar un evento desde su ficha hacia un calendario compartido. Publicación, actualización y retirada son manuales y exclusivas de Dirección y Coordinación. No hay publicación masiva, sincronización bidireccional, calendarios por usuario, invitados, adjuntos ni creación de calendarios.

Base de implementación: main `feb8ae3d3c5ae14d258f3d226caa1260a282919b` (27/10/2026). La validación real de Google requiere configuración externa y **no fue realizada por las pruebas automatizadas**. No se debe marcar M-06 cumplida solo por ejecutar los tests simulados.

## 2. Arquitectura

Integración dentro de agenda: `CalendarEventoController` → `ExportacionCalendarService` → interfaz `ClienteCalendar` → adaptador oficial `GoogleCalendarClient`. `ContenidoCalendar` construye una proyección explícita, sin serializar la ficha. `PublicacionCalendarRepository` usa JDBC para coordinación PostgreSQL y estado durable; `PublicacionCalendar` representa ese estado.

V15 captura cambios en una cola coalescente de PostgreSQL (`calendar_seguimiento`) dentro de la transacción de origen. `ProcesadorSeguimientoCalendar` ejecuta la proyección local `SeguimientoCalendar` después del commit, en `REQUIRES_NEW` con timeout de 10 segundos. Las señales AFTER_COMMIT aceleran el trabajo; un barrido local cada 5 segundos recupera revisiones pendientes después de una caída. No consulta Google. Fallos de proyección revierten únicamente esa proyección, quedan registrados con código sanitizado y backoff de hasta 300 segundos, y no revierten la operación comercial ya confirmada. Si tampoco puede registrarse el fallo, la revisión sigue sin confirmar y se registra el incidente en logs.

## 3. Estados de publicación

`PENDIENTE`, `EXPORTADO`, `RETIRADO`, `EN_PROCESO`, `ERROR`, `INCIERTO`. Son técnicos; no modifican `Evento.estado`.

La intención de retirada manual también se persiste (`retiro_solicitado`): un error no la convierte en una actualización aunque el evento siga activo.

La acción pendiente es independiente: `EXPORTAR`, `ACTUALIZAR`, `RETIRAR`, `NINGUNA`. Una operación fallida puede conservar el mismo trabajo pendiente. `ultimoExito` solo se actualiza al persistir una confirmación.

Después de una retirada manual confirmada no se vuelve a crear automáticamente la entrada. La recreación de un ID eliminado requiere revisión: Google puede conservar una lápida del evento. Nunca se cambia silenciosamente el ID para sortearla.

## 4. Reglas funcionales y horarios

Nuevas publicaciones: SENADO, CONTRATADO, CONFIRMADO. Una publicación existente se conserva en EN_CURSO, REALIZADO y CERRADO. PRE_RESERVA no se publica. CANCELADO y LIBERADA requieren retirar una identidad previamente reservada/publicada; sin identidad no se llama a Google.

Se publica el intervalo completo del turno del catálogo. Inicio: fecha operativa + hora de inicio del turno. Fin: fecha operativa + un día si `cruzaMedianoche`, a la hora de fin del turno. Zona IANA `America/Argentina/Cordoba`. No hay horarios fijos en el integrador.

La hora particular solo aparece en descripción y se omite si es null. El título es código · salón · turno. La descripción contiene tipo, salón, turno, hora particular opcional y código. No incluye nombre libre del evento, cliente, teléfono, correo, documento, seña, moneda, contratos, personal, observaciones, servicios ni adjuntos.

## 5. Permisos y endpoints

Todas estas rutas exigen DIRECCION o COORDINACION mediante `@PreAuthorize`, y el servicio vuelve a comprobar acceso total:

| Método | Ruta | Resultado |
|---|---|---|
| GET | `/api/v1/eventos/{id}/calendar` | Lectura local sin escrituras ni red Google; informa seguimiento pendiente |
| POST | `/api/v1/eventos/{id}/calendar` | Exportar/reconciliar identidad estable |
| PUT | `/api/v1/eventos/{id}/calendar` | Actualizar/reconciliar |
| DELETE | `/api/v1/eventos/{id}/calendar` | Retirar/reconciliar retirada |
| GET | `/api/v1/calendar/pendientes` | Pendientes persistidos, separados de lectura de avisos |
| POST | `/api/v1/eventos/{id}/calendar/verificacion` | Lee ETag y coincidencia de contenido remoto por solicitud explícita |
| POST | `/api/v1/eventos/{id}/calendar/reconciliacion` | Acepta el ETag revisado tras comprobar su vigencia; no escribe en Google |

El navegador envía intención e ID Vastio; en la aceptación de un conflicto también el ETag revisado. No recibe ni elige credenciales/destino. Los errores Google se traducen a `ProblemDetail`: conflicto 409, entrada ausente/eliminada 410, datos inválidos 422, cuota 429, red 504, respuesta inválida 502, configuración/autorización/disponibilidad 503. El 401 de Google nunca se devuelve como expiración del login de Vastio. POST y PUT conservan el mismo coordinador idempotente: los rótulos expresan intención de UI, pero ambos reconcilian la identidad estable antes de escribir. Separarlos duplicaría recuperación y permitiría carreras innecesarias.

## 6. Configuración

Por defecto `VASTIO_CALENDAR_HABILITADO=false`: el backend arranca sin archivo de credenciales y no conecta a Google. La cola captura cambios, pero no proyecta nuevos avisos. Los GET devuelven pendiente NINGUNA, capacidades falsas y listado vacío; el historial e identidades se conservan. Al iniciar desactivado se resuelven avisos Calendar anteriores y se restablece su clave de deduplicación; el barrido reintenta esa limpieza si falla. V14 conserva sus registros históricos y V15 no emite avisos masivos.

La configuración cambia mediante reinicio. Al activar se encolan eventos elegibles preexistentes y todas las publicaciones anteriores: se recalculan exportaciones, diferencias acumuladas y retiradas necesarias. Un aviso activo por problema/evento evita duplicaciones; una retirada confirmada no se republica automáticamente. El procesamiento es eventual y por lotes de 50; el estado informa `seguimientoPendiente`/`errorSeguimiento` para no presentar una publicación como vigente antes de revisar los cambios acumulados.

Variables para una instalación habilitada:

- `VASTIO_CALENDAR_HABILITADO=true`.
- `VASTIO_CALENDAR_ID`: identificador explícito del calendario compartido. `primary` se rechaza.
- `VASTIO_CALENDAR_INSTALACION`: identidad estable y única por entorno, hasta 120 caracteres. No cambiar tras publicar.
- `VASTIO_CALENDAR_CREDENCIALES`: ruta del JSON **fuera del repositorio**, legible solo por backend/operador.

Timeouts: `vastio.calendar.conexion-ms=5000`, `lectura-ms=10000`; configurables de 100 a 30000 ms. Se aplican tanto a Calendar como al transporte de obtención de tokens OAuth. El transporte OAuth desactiva redirecciones, reintentos y logging de peticiones; la estrategia de reintentos de la biblioteca de autenticación también está desactivada. Cada intento manual reconcilia antes de escribir y tiene un número acotado de solicitudes. Ante cuota/caída, esperar antes de solicitar otro intento; no mantener un bucle de reintentos desde el navegador. Son límites de conexión/lectura, no un deadline global que incluya DNS.

En Docker utilizar el override optativo:

```sh
docker compose -f docker-compose.prod.yml -f docker-compose.calendar.yml --env-file .env.prod up -d --build
```

El override requiere `VASTIO_CALENDAR_CREDENCIALES_HOST` apuntando a un archivo existente fuera del checkout y lo monta de solo lectura en `/run/secrets/vastio-calendar.json`. El compose base sigue sin activar Calendar. No copiar claves en el Dockerfile, en `src/main/resources`, en variables `VITE_*` ni en la imagen.

## 7. Credenciales y revocación

La biblioteca carga `ServiceAccountCredentials` únicamente al ejecutar una operación. Usa el scope `https://www.googleapis.com/auth/calendar.events`. No usa JWT de Vastio para Google ni firma JWT manualmente.

Restringir lectura del archivo, no imprimirlo ni adjuntarlo a incidencias. Rotar creando una clave autorizada nueva, cambiar el archivo montado y reiniciar backend; después revocar la anterior. Para detener acceso retirar la ACL, deshabilitar la cuenta o revocar la clave. Una instancia ya iniciada conserva el cliente en memoria hasta reinicio.

## 8. Procedimiento Google Cloud

1. Crear/seleccionar un proyecto controlado por el responsable; revisar cuotas efectivas.
2. Habilitar Google Calendar API.
3. Crear una cuenta de servicio con el mínimo acceso necesario; IAM del proyecto no otorga escritura en calendarios.
4. Crear el calendario **con una cuenta responsable**, no con la cuenta de servicio como propietaria.
5. Compartir ese calendario con el correo de la cuenta de servicio y permiso de escritura de eventos. No alcanza free/busy o reader. Revisar políticas de acceso externo en Workspace.
6. Obtener su calendarId y configurar un entorno aislado.
7. Ejecutar el ensayo de la sección 16 con datos sintéticos y autorización del responsable.

No se requiere administrar ACL ni delegación global de dominio desde Vastio. No se asume que cualquier calendario acepte la cuenta de servicio. Si las políticas lo impiden, OAuth 2.0 de servidor con una cuenta autorizada es una alternativa futura: consentimiento, callback con state, acceso offline, custodia/rotación/revocación del refresh token y revisión del modo de publicación/verificación. No se implementaron simultáneamente ambos flujos.

## 9. Calendario de prueba

Proyecto/identidad/calendario separados del productivo, datos sintéticos y audiencia restringida. La identidad de instalación también debe ser distinta. No utilizar un calendario personal/productivo para ensayos ni ejecutar Calendar real en CI.

## 10. Prevención de duplicados y concurrencia

Un registro por evento Vastio (un destino por evento en este MVP), con unicidad adicional de calendario/ID. El ID remoto es SHA-256 hexadecimal de instalación + separador + eventoId: 64 caracteres admitidos por base32hex, estable entre reintentos y separado por entorno.

Antes de escribir se persiste la identidad, la operación y la huella del intento. La huella del intento permite recuperar una escritura exitosa aun cuando el contenido local cambió antes del reintento. Se usa un advisory lock PostgreSQL de sesión por evento durante toda la operación remota. No es un lock Java ni una lease con vencimiento que permita ejecutar dos escritores simultáneos. Una colisión del hash numérico del lock solo serializa eventos adicionales.

El lock usa un pool propio, perezoso, de máximo dos conexiones, mínimo cero y espera de adquisición de un segundo. Ninguna llamada Google retiene conexiones del pool comercial. Las transacciones de preparación/confirmación son cortas. La entrada del caso de uso remoto usa propagación NEVER para rechazar su ejecución dentro de una transacción comercial. La conexión se desbloquea antes de volver al pool; si falla el desbloqueo se aborta. Al caer el proceso se libera la sesión; `EN_PROCESO` queda durable y el procesador local lo identifica como incierto si el lock ya está libre. Los GET no recuperan ni escriben.

## 11. Errores externos

Permisos/autorización requieren revisar ACL o credenciales. Cuotas/5xx/red admiten reintento manual acotado. 412 o un ETag distinto indican conflicto: no se pisan cambios remotos silenciosamente. Una identidad ajena no se adopta. Un evento ya confirmado pero ausente no se recrea automáticamente.

Para CONFLICTO_REMOTO, Dirección/Coordinación solicita «Revisar cambios externos», revisa la entrada en Google y acepta explícitamente el ETag vigente. El servidor vuelve a leerlo y rechaza una revisión que cambió mientras se revisaba. La aceptación queda auditada y solo guarda metadatos técnicos; nunca importa campos a Evento. La actualización o retirada requiere otra acción manual y sigue usando If-Match. Una edición posterior vuelve a producir conflicto. Identidades ajenas, contenido no interpretable y entradas eliminadas requieren intervención operativa; no hay recreación automática.

Antes de interpretar un 404 de evento se consulta el calendario mediante events.list. Si no hay acceso al destino no se crea nada. Aun así, no existe atomicidad entre comprobar acceso y la siguiente llamada; un fallo posterior mantiene error/resultado incierto y necesita revisión.

## 12. Recuperación de resultados inciertos

Repetir la acción en la ficha. El backend consulta el mismo ID remoto. Si coincide el contenido publicado con el intento vigente, confirma sin insertar nuevamente. Si coincide la versión anterior confirmada, actualiza con su ETag; si difiere se informa conflicto. Una retirada ya ausente puede confirmarse después de comprobar el destino.

Si Google escribió y falla el commit local, se conserva INCIERTO o EN_PROCESO; no se muestra éxito. Un reinicio no cambia la identidad. La auditoría conserva solicitud/reintento/error/confirmación. No se promete atomicidad distribuida.

## 13. Notificaciones

Se reutiliza `NotificacionService`, tipo `CALENDAR_PENDIENTE`. Dirección y Coordinación reciben aviso, incluido el autor del cambio. Disparadores: llegada a SENADO, contenido publicado desactualizado, cancelación/liberación con identidad y fallo que requiere atención.

Una clave persistida deduplica el mismo problema por evento. Cambiar el problema resuelve el aviso anterior y crea el nuevo. Éxito confirmado resuelve el aviso si no queda otra diferencia local. El nuevo campo `notificacion.resuelta` evita mostrarlo en lista/contador; se conserva para trazabilidad. Marcar leído no modifica la publicación.

## 14. Seguimiento persistente

`publicacion_calendar` conserva estado técnico; `operacion_calendar` es historial de solo inserción. V14 permanece intacta. V15 agrega `calendar_seguimiento`, captura inserción/cambios relevantes de evento y nombres/horarios de catálogos mediante triggers mínimos, y encola publicaciones existentes. Solo después del commit el procesador recalcula diferencias y avisos. La captura durable se confirma o revierte junto con el cambio comercial: no depende de que llegue una señal en memoria. Una indisponibilidad de la base o un fallo de captura impide confirmar esa transacción; no se promete durabilidad si la propia base no puede guardar el cambio. Ninguna excepción de la proyección Calendar participa en ese commit.

La huella depende del contenido efectivo, incluyendo nombres/horarios de catálogo, no de Evento.version. Los cambios comerciales y de catálogos recalculan localmente. Si el evento cambia mientras Google responde, se guarda la versión efectivamente publicada y permanece pendiente actualizar/retirar.

## 15. Pruebas automatizadas

`ContenidoCalendarTest`: privacidad, horario particular, medianoche/año, catálogo y estabilidad de identidad. `GoogleCalendarClientTest`/`GoogleCalendarHttpTest`: modelo oficial, roundtrip, cambios remotos y HTTP simulado. `TransporteTokenCalendarTest`: límites y ausencia de reintentos OAuth sin sockets. `CalendarIT`: PostgreSQL real, cliente Google sustituido, fallos de proyección inyectados en cinco flujos comerciales, recuperación, rollback de captura, GET sin escrituras, activación/desactivación, conflictos, seguridad, concurrencia y pool comercial liberado. `MigracionCalendarIT`: instalación, V13→V14 y V14→V15 sin alterar datos/historial. Frontend: Vitest/Testing Library y Playwright con API simulada en escritorio y teléfono.

Comandos: `backend/mvnw verify`; en frontend `npm test`, `npm run lint`, `npm run build`, `npx tsc -b`, `npx playwright test calendar.spec.ts`. Docker es necesario para Testcontainers. Usar un Node compatible con las dependencias fijadas en package-lock. No añadir credenciales a CI.

## 16. Validación real pendiente

Con autorización y calendario exclusivamente de prueba: exportar, repetir, cambiar hora particular, reprogramar salón/fecha/turno, actualizar, cancelar, retirar y repetir retirada. Verificar el cierre nocturno al día/mes/año siguiente. Revisar título/descripción sin datos privados. Simular edición/eliminación manual y retirada temporal de ACL; verificar conflictos y recuperación. Registrar fecha, responsable y evidencias sin claves. No se acredita este ensayo con mocks.

## 17. Limitaciones

- No hay polling Google ni reconciliación remota automática. Ediciones/eliminaciones externas se detectan en la siguiente operación remota explícita; el estado verde significa última confirmación, no monitoreo continuo.
- La lista del MVP no publica en lote. El pool dedicado admite dos operaciones remotas simultáneas por instancia y no consume el pool comercial durante la red.
- Identidades ajenas, IDs eliminados y cambio de destino/instalación requieren intervención; los conflictos ETag tienen revisión y aceptación explícitas, sin regeneración silenciosa de ID.
- Los eventos manuales previos en el calendario no se deduplican por título: usar calendario dedicado o conciliarlos antes del despliegue.
- La API de Google puede tener cuotas/políticas cambiantes. El uso estándar es sin costo adicional según la documentación consultada; verificar condiciones del proyecto al activar.

## 18. Criterios de aceptación

Acciones exclusivamente manuales y autorizadas; datos mínimos; turnos reales y zona Córdoba; estados visuales rojo/verde más texto; pendientes persistentes y avisos deduplicados/resueltos; doble clic y solicitudes concurrentes sin nuevas identidades; recuperación de timeouts/reinicio; sin red Google dentro de transacciones comerciales; tests reales PostgreSQL y simulación externa; secretos fuera de Git/imagen; validación real separada y documentada.

Fuentes oficiales: [Calendar scopes](https://developers.google.com/workspace/calendar/api/auth), [sharing](https://developers.google.com/workspace/calendar/api/concepts/sharing), [propiedad](https://developers.google.com/workspace/calendar/api/concepts/events-calendars), [insert/IDs](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert), [errores](https://developers.google.com/workspace/calendar/api/guides/errors), [ETags](https://developers.google.com/workspace/calendar/api/guides/version-resources), [cuotas](https://developers.google.com/workspace/calendar/api/guides/quota), [biblioteca Java](https://github.com/googleapis/google-api-java-client), [Google Auth Java](https://github.com/googleapis/google-cloud-java/tree/main/google-auth-library-java).

Dependencias seleccionadas tras consultar Maven Central: Calendar `v3-rev20260925-2.0.0`, Google Auth `1.55.0`. Su compatibilidad efectiva se valida compilando y probando con Java 25/Spring Boot 4; no se infiere de un ejemplo antiguo de quickstart.
