# Diagnóstico RED_GOOGLE — Windows / commit c50b4dc

## Resultado demostrado

El transporte OAuth contiene un defecto de doble descompresión. `TransporteTokenCalendar` usa internamente un `HttpResponse`, cuyo `getContent()` entrega el stream ya descomprimido cuando la respuesta es gzip. El puente devolvía ese stream con `Content-Encoding: gzip` y la longitud de los bytes comprimidos. La capa HTTP exterior intentaba descomprimir de nuevo el JSON y lanzaba:

```text
java.util.zip.ZipException: Not in GZIP format
  java.util.zip.GZIPInputStream.readHeader
  com.google.api.client.http.HttpResponse.getContent
  com.google.auth.oauth2.ServiceAccountCredentials.parseResponseAs
  com.google.auth.oauth2.ServiceAccountCredentials.refreshAccessToken
```

Se reprodujo sin red, con una respuesta HTTP gzip simulada, tanto leyendo el cuerpo como mediante `ServiceAccountCredentials.refreshAccessToken()` con una clave efímera generada en memoria. Antes del arreglo: tres tests, uno aprobado y dos errores ZipException. La prueba anterior solo cubría una respuesta sin comprimir y dejó pasar este caso.

`HttpCredentialsAdapter` obtiene el access token antes de enviar la petición Calendar. Una IOException de OAuth atraviesa el cliente y `traducir()` la convierte en RED_GOOGLE, igual que una avería de red. Esa clasificación borra la causa de la respuesta de aplicación; RED_GOOGLE por sí solo no demuestra DNS, TLS ni timeout. El defecto explica cómo puede fallar la exportación aunque curl y Java HttpClient lleguen por HTTPS.

**Confirmación con la cuenta de prueba autorizada (29/10/2026):** se ejecutó únicamente `refreshAccessToken()`, sin Spring, PostgreSQL ni Calendar. Con el transporte original c50b4dc falló en OAUTH_TOKEN con `java.util.zip.ZipException`; la comprobación sanitizada confirmó el mensaje exacto `Not in GZIP format`. Con el transporte corregido, tras recompilar limpiamente, la misma cuenta obtuvo el token correctamente. Se confirmó que el defecto afecta la autenticación de esta cuenta; no se inspeccionó el proceso del backend ya iniciado. No se copiaron ni mostraron claves, tokens o contenido del JSON. El SDK leyó el archivo autorizado directamente.

La comparación confirma carga válida de la cuenta/clave para OAuth y conectividad DNS/TLS suficiente en ese ensayo; no acredita ACL ni permisos de Calendar. No se ajustaron timeouts ni truststores. La primera ejecución incremental posterior a restaurar el arreglo reutilizó clases originales por la fecha del archivo y volvió a fallar con ZipException; no se contabilizó como prueba del arreglo. La comparación definitiva usó `clean test`, que recompiló todos los fuentes.

**Resultados:** reproducción simulada previa: 1 aprobado / 2 errores ZipException; focales corregidas: 24 aprobadas (y dos pruebas optativas reales omitidas inicialmente); ensayo real con transporte original: 1 fallo esperado ZipException; ejecución limpia final con transporte corregido: 25 aprobadas, cero fallos/errores/omisiones (24 focales simuladas y 1 obtención real del token). `git diff --check` aprobado. No se ejecutaron suites completas ni CI en esta corrección. La comprobación remota de identidad NO se ejecutó: la autorización real se limitó a obtener el token.

## Corrección mínima

El puente sigue usando el stream de `HttpResponse.getContent()`. Para respuestas gzip ya descomprimidas no vuelve a anunciar Content-Encoding y descarta Content-Length comprimido (longitud desconocida: -1). Se mantienen conexión/lectura acotadas, cero reintentos, cero redirecciones y logging HTTP desactivado. Se prueban cuerpo comprimido exitoso, token OAuth real de la biblioteca contra transporte simulado, error HTTP comprimido y límites previos.

Se agrega diagnóstico con fases cerradas: CREDENCIALES, OAUTH_TOKEN y operaciones CALENDAR. Se identifica el fallo OAuth al ejecutar `HttpCredentialsAdapter.initialize(request)`. Este adaptador no instala un HttpExecuteInterceptor; no se debe envolver un interceptor inexistente. La instrumentación inicial de ccdf920 lo suponía y provocaba un NullPointerException, detectado/corregido durante la validación real posterior (ver `m-06-validacion-real.md`). Los logs contienen únicamente fase, categoría, nombres de clases de la cadena causal y estado HTTP. No contienen mensajes de excepciones, cuerpos, rutas, URLs, claves, tokens ni stack traces. No se cambia el código público RED_GOOGLE ni la semántica de resultado incierto.

| Evidencia | Interpretación |
|---|---|
| OAUTH_TOKEN / FORMATO_GZIP / ZipException | Defecto de doble descompresión reproducido. |
| OAUTH_TOKEN / OAUTH_HTTP / HTTP 400–401 | Google rechazó OAuth; revisar cuenta/clave/scope/reloj según el error seguro, no asumir problema de red. |
| DNS / UnknownHostException | Resolución del host; identificar fase antes de revisar DNS/proxy. |
| TLS / SSLHandshakeException u otra SSLException | Truststore/cadena TLS/proxy del transporte Java usado por la biblioteca. No desactivar validación TLS. |
| TIMEOUT / SocketTimeoutException | Límite de conexión/lectura; no prueba problema de permisos. |
| CONEXION / ConnectException | Conexión rechazada/no disponible en la fase indicada. |
| CALENDAR / AUTORIZACION_O_PERMISOS_CALENDAR / HTTP 401–403 | Rechazo de API Calendar; cuenta/ACL/scope o cuota se evalúan con el código sanitizado de la aplicación. |
| CREDENCIALES / IOException, NoSuchFileException, etc. | Archivo/configuración/carga; no imprimir JSON ni el mensaje completo. |

Un 401 de Calendar sin autenticar demuestra acceso a ese endpoint desde ese cliente, no que se haya obtenido un token. Un 404 de OAuth sin autenticar no ensaya el POST firmado que usa la cuenta de servicio ni su respuesta comprimida. curl, Java HttpClient y NetHttpTransport no comparten necesariamente proxy/truststore/transporte.

## Confirmación segura en Windows

Usar esta rama independiente `codex/m06-diagnostico-oauth`; no ejecutar la exportación para diagnosticar. El worktree de validación y su base permanecen intactos.

Pruebas focales sin Google:

```powershell
cd C:\Users\tomic\OneDrive\Documentos\ChatGPT\Seminario\vastio-m06-diagnostico-oauth\backend
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
.\mvnw.cmd '-Dtest=TransporteTokenCalendarTest,DiagnosticoCalendarTest,GoogleCalendarHttpTest,GoogleCalendarClientTest,ContenidoCalendarTest' test
```

Prueba optativa de **obtención del token únicamente**. Reutilizar la ruta local del JSON de prueba; nunca pegar su contenido. Las variables afectan solo esa consola. No levanta Spring, no usa PostgreSQL, no invoca Calendar y no imprime el token:

```powershell
$env:VASTIO_DIAGNOSTICO_OAUTH='true'
$env:VASTIO_CALENDAR_CREDENCIALES='<ruta-local-del-JSON-de-prueba>'
.\mvnw.cmd '-Dtest=DiagnosticoOAuthRealTest#obtieneTokenSinOperarCalendar' test
```

Si falla, conservar únicamente `Diagnóstico OAuth sanitizado` (fase/clases/categoría/http). El test evita adjuntar la excepción original porque podría contener datos de respuesta. El diagnóstico admite solo `https://oauth2.googleapis.com/token`; un endpoint diferente debe revisarse antes de enviar una aserción. Una ejecución exitosa confirma autenticación de esa cuenta con el transporte corregido, no los permisos del calendario.

## Resultado incierto: impedir un segundo INSERT durante el diagnóstico

No pulsar exportar/reintentar ni llamar a POST/PUT/DELETE de Vastio. No borrar la publicación, cambiar instalación, regenerar google_id ni restablecer el estado para empezar de nuevo. Aunque el fallo OAuth reproducido ocurre antes de la petición Calendar, no permite deducir qué ocurrió en intentos anteriores.

Primero leer la identidad existente en **vastio_calendar_test, localhost:5433**, dentro de una transacción de solo lectura:

```sql
BEGIN TRANSACTION READ ONLY;
SELECT e.codigo,p.estado,p.pendiente,p.google_id,p.calendar_id,p.instalacion,p.error,p.intento,p.exito
FROM evento e LEFT JOIN publicacion_calendar p USING(evento_id)
WHERE e.codigo='EV-2026-00001';
ROLLBACK;
```

Después consultar exactamente ese calendar_id/google_id, sin regenerarlos. Hay un test optativo de lectura que usa únicamente `events.list` y `events.get`, verifica acceso al destino antes de interpretar ausencia y valida propiedad de la identidad. No llama al servicio de publicación/recuperación ni escribe en DB/Google:

```powershell
$env:VASTIO_DIAGNOSTICO_OAUTH='true'
$env:VASTIO_DIAGNOSTICO_IDENTIDAD='true'
$env:VASTIO_CALENDAR_ID='<calendar_id-persistido-de-prueba>'
$env:VASTIO_DIAGNOSTICO_GOOGLE_ID='<google_id-persistido-de-64-caracteres>'
.\mvnw.cmd '-Dtest=DiagnosticoOAuthRealTest#consultaIdentidadGuardadaSinInsertarNiRecuperar' test
```

PRESENTE confirma una entrada propia en esa lectura; AUSENTE/ELIMINADA no autoriza automáticamente otro INSERT. Si falla OAuth, permisos o la consulta, el resultado sigue sin confirmar. Mantener la identidad e incertidumbre y decidir la recuperación existente solo tras revisar esa evidencia. No se modificó la lógica de publicación, recuperación ni sus reglas.

Al finalizar la consola de diagnóstico, quitar sus variables optativas con `Remove-Item Env:VASTIO_DIAGNOSTICO_OAUTH,Env:VASTIO_DIAGNOSTICO_IDENTIDAD,Env:VASTIO_DIAGNOSTICO_GOOGLE_ID -ErrorAction SilentlyContinue`. No cambiar permisos globales ni deshabilitar TLS. El backend cachea cliente/credenciales: aplicar el arreglo exige reconstruir y reiniciar la instancia de prueba antes de continuar la aceptación.

Referencia oficial: [HttpResponse.getContent()](https://docs.cloud.google.com/java/docs/reference/google-http-client/latest/com.google.api.client.http.HttpResponse#getContent()). La verificación decisiva del defecto es la reproducción local con la versión fijada por el proyecto.
