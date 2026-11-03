# M-10 — Transferencia con rclone y operación

Las pruebas automatizadas usan backend local y configuración sintética, sin credenciales
reales. El ciclo real con Drive fue validado con datos sintéticos; ver [Etapa 3](m-10-drive-resultados.md).
La configuración y cuenta de ese ensayo son solo de pruebas, no un destino productivo aprobado.
La transferencia se habilita de forma explicita; el Compose de respaldo local sigue
funcionando sin configuracion de rclone. No cambian Java, Flyway ni el backend.

## Arquitectura y garantias

`deploy/respaldo.Dockerfile` extiende PostgreSQL 17 con `rclone` y `ca-certificates`
para validar TLS normalmente.
`docker-compose.prod.yml` construye esa imagen y monta ambos scripts en solo lectura.
El override `docker-compose.respaldo-externo.yml` habilita `RESPALDO_TRANSFERIR=1`
y monta un directorio de configuracion externo, que debe existir previamente.

El script principal conserva su `flock`, manejo de senales y espera de 86400 segundos.
Solo tras publicar un dump local completo prepara un paquete y llama al auxiliar
`scripts/transferir_respaldo.sh`. El auxiliar es interno: **no ejecutarlo directamente**,
porque el lock de la operacion lo mantiene `respaldo_local.sh`.

Cada paquete queda en:

```text
respaldos/<entorno>/<base>/transferencias/<nombre-del-dump>.listo/
  datos.tar       # ese dump y el legajo congelado para reintentos
  SHA256SUMS      # SHA-256 de datos.tar
  COMPLETO       # texto fijo; se publica al final en el destino
  .enviado       # confirmacion LOCAL; nunca se transfiere
```

Se construye primero en una carpeta `.partial` y luego se renombra a `.listo`.
El tar solo incluye el dump seleccionado y `legajo/`; excluye `.partial`, `.tmp`,
`.respaldo.lock` y `.en-curso`. No se recorren otros entornos ni se adoptan
automaticamente los dumps historicos anteriores a esta etapa.

El destino agrega siempre `<entorno>/<base>/<nombre-del-dump>/` a `RCLONE_DESTINO`.
Para cada paquete pendiente se valida SHA-256 local, se ejecuta `rclone copy` con
`--checksum`, y despues `rclone check --download` compara el contenido
transferido. Solo al terminar ambas operaciones se copia el marcador `COMPLETO`.
**Un directorio remoto sin ese marcador no es una copia terminada.** Durante un corte
pueden quedar archivos o temporales propios de rclone en destino; no se consideran
recuperables hasta que el reintento termine y aparezca el marcador.

No se usa `sync`, `move`, borrado remoto ni borrado local de paquetes. El nombre estable
y la comparacion por checksum evitan nuevas copias innecesarias al reintentar. Un
objeto truncado por un corte se repara en la misma ruta. No se usa `--immutable` para
el contenido: rclone 1.60 puede dejar el nombre final incompleto y esa opcion impide
recuperarlo. El marcador usa `--immutable`; para recuperar se exige tambien verificar
SHA-256, porque un marcador no protege contra cambios posteriores del almacenamiento.
El ack local guarda una huella del destino para no omitir transferencias al cambiar
la ruta configurada. Si cambia la cuenta detras del mismo nombre de remoto, el operador
debera revisar esas confirmaciones antes de reutilizarla.

La salida cruda de rclone se descarta para impedir que errores del proveedor revelen
tokens/rutas privadas. Se registran identificador seguro del paquete, etapa y código
de salida. No usar trazas `set -x`.
Los remotos se declaran en el archivo externo: no se admiten URI, conexiones inline
ni secretos en `RCLONE_DESTINO`.

## Fallos, reintentos e interrupciones

Cada paquete pendiente se procesa independientemente: un fallo de integridad, copia,
comprobación o marcador se registra y el recorrido continúa. Cada paso se comprueba
explícitamente, conservando copia → check → COMPLETO → confirmación local. No se publica
un nuevo marcador para el paquete fallido; se conserva para investigación o reintento.
El resultado final conserva el primer código no cero aunque los siguientes terminen bien.
Los errores globales de configuración y las señales siguen deteniendo la ejecución.

Un fallo al preparar el paquete nuevo también permite enviar los anteriores listos.
Su `.partial` y dump permanecen; no se reconstruye automáticamente con un legajo posterior.
Un fallo de preparación o transferencia conserva dumps y paquetes locales y omite la retención
de ese ciclo. Con `--once` o `--transfer-only` se devuelve el código fallido. En modo diario se registra el
error y se conserva la espera de 86400 segundos; el proximo ciclo intenta todos los
paquetes pendientes. El trabajo de transferencia agrega tiempo al ciclo, igual que
las otras operaciones previas a la espera.

Para reintentar sin crear otro dump, usar el servicio con el mismo entorno/base:

```sh
docker compose --env-file .env.prod -f docker-compose.prod.yml \
  -f docker-compose.respaldo-externo.yml run --rm --no-deps respaldo --transfer-only
```

Es una instruccion para el futuro operador; no ejecutarla sobre una instalacion real
sin identificar sus recursos. Si hay otro ciclo activo, falla por `flock` sin tocarlo.
TERM/INT se propagan por la cadena de procesos; KILL no publica un marcador por si
solo. Los hijos supervivientes conservan el lock hasta terminar. Un corte mientras se
prepara el paquete puede dejar una carpeta `.partial`, que nunca se envia. No hay
limpieza automatica de esos temporales. Un dump local sin paquete despues de ese corte
requiere revision del operador; no se empaqueta retrospectivamente con otro legajo.

## Identificar pendientes y fallos sin alterar respaldos

1. Identificar instalación, entorno, base y destino configurados antes de operar.
2. En `respaldos/<entorno>/<base>/transferencias`, las carpetas `.partial` son preparaciones
   incompletas y nunca se envían. Un dump sin paquete listo requiere revisión del operador.
3. Una carpeta `.listo` sin `.enviado` está pendiente. La confirmación guarda la huella del
   destino: al cambiar la ruta configurada también se considera pendiente. Su existencia no
   prueba disponibilidad remota actual. No editar ni borrar confirmaciones para forzar envíos.
4. Buscar `ERROR transferencia paquete=<id> etapa=<etapa> codigo=<n>; pendiente_conservado`.
   `verificar_local` identifica fallos de manifiesto/hash; `rclone_copy`, `rclone_check` y
   `rclone_completo` localizan el paso fallido. `ERROR respaldo etapa=preparar_transferencia`
   identifica el fallo al construir el paquete nuevo. El auxiliar emite además un resumen
   con código no cero. No usar trazas ni volcar la configuración OAuth para diagnosticar.
5. Reintentar con el `--transfer-only` documentado arriba, manteniendo entorno, base y destino.
   El bloqueo impide dos escritores. Un fallo transitorio puede recuperarse; un paquete
   corrupto requiere investigación del operador, sin recalcular su manifiesto para dar por
   válidos bytes desconocidos. No se borra ni repara automáticamente y no bloquea otros paquetes.
6. En destino, exigir `COMPLETO` y comprobar SHA-256 antes de restaurar en una instancia nueva
   y aislada. El marcador por sí solo no garantiza la integridad actual.

## Configuración productiva futura (pendiente de aprobación)

1. Elegir cuenta/custodio y carpeta privada de destino, con permisos acordados.
2. En una etapa autorizada, crear un remoto `drive` con `rclone config` **fuera del
   repositorio**. No poner tokens en `.env`, comandos, documentos o imagenes Docker.
3. Definir `RCLONE_CONFIG_DIR` como ruta absoluta de ese directorio y `RCLONE_DESTINO`
   como `drive:vastio-respaldos` (ejemplo; no reutilizar automáticamente el destino de pruebas).
   El archivo se espera en `/config/rclone/rclone.conf`.
4. El directorio se monta RW porque rclone puede renovar tokens; restringir ACL/permisos
   al custodio. El override no crea carpetas host automaticamente.
5. Combinar ambos Compose y reconstruir el servicio al desplegar. El resto del stack
   no requiere rclone ni recibe su configuracion. No se ha desplegado en esta etapa.

No hay cifrado incorporado ni politica de retencion externa. Los paquetes y sus copias
pueden contener informacion confidencial. Deben dimensionarse almacenamiento, acceso
y conservacion antes del uso real. El nombre de entorno debe ser unico por instalacion.

## Pruebas locales

Linux/WSL, Python 3.11+, `flock`, `tar`, `sha256sum` y rclone real:

```sh
python3 -B scripts/test_respaldo_local.py
python3 -B scripts/test_transferencia_respaldo.py
```

Si rclone no esta en PATH, `RCLONE_TEST_BIN` permite indicar un ejecutable ya instalado.
Las pruebas crean su propio `rclone.conf` con backend `local`, destinos temporales,
datos sinteticos y grupos de procesos exclusivos. No leen configuracion de usuario.
PostgreSQL se simula en estas suites; rclone, hashes, locks y senales son reales.
El limite de ancho de banda usado en los casos de interrupcion sirve solo para hacer
observable una transferencia activa; no sustituye rclone.

Se cubren exito y recuperacion del tar, exclusiones, fallo y conservacion local,
reintento sin duplicados, separacion, corrupcion local, periodicidad tras error,
TERM/KILL durante rclone y reintento posterior.

Verificación posterior a la auditoría: **26/26 pruebas aprobadas** (13 de respaldo local
en 13,802 s y 13 de transferencia en 228,094 s). Se agregaron seis pruebas para colas
mixtas, todos válidos, todos fallidos, fallos selectivos de copy/check/marcador,
reintentos y fallos de empaquetado con continuidad y espera diaria. Los fallos selectivos
se inyectan en un wrapper; las transferencias exitosas usan rclone real con backend local.
Se conservan los paquetes fallidos, no se publica su marcador remoto ni su confirmación,
y el resultado global sigue siendo no cero aunque se envíen los demás paquetes.
No se accedió a Drive al verificar estas correcciones.

Resultados históricos de la Etapa 2.1, con backend local y anteriores al ensayo con Drive:

- 13 regresiones del respaldo local aprobadas; 7 pruebas de transferencia con rclone
  real aprobadas. La repeticion final de transferencia duro 68,728 segundos.
- Imagen construida con PostgreSQL 17 y rclone Debian `1.60.1+dfsg-4`
  (`rclone v1.60.1-DEV` en su salida de version). Sin credenciales en el build.
- Integracion Docker exclusiva `m10rfinal_c60f40c9ea`: PostgreSQL 17.11, tres tablas
  relacionadas, constraints/indices, datos sinteticos y legajo. Fallo de destino,
  reintento sin generar otro dump ni reescribir objetos iguales, recuperacion del tar
  desde el destino y restauracion con `pg_restore --exit-on-error` en instancia nueva.
  Esquema y contenido iguales; inserciones posteriores dieron IDs `3|3|3`; legajo y
  paquetes coincidieron por SHA-256.
- Se detuvo rclone despues de observar bytes en destino: `docker stop --time 5`
  termino en 0,39 s, salida 130, sin publicar `COMPLETO`. `--transfer-only` reparo
  el objeto incompleto; quedaron dos paquetes completos, sin otro dump generado por
  el reintento y con hashes validos.
- El primer ensayo integral detecto que `--immutable` sobre el contenido impedia
  reparar un objeto truncado (salida 6). Se retiro esa opcion solo del contenido y se
  reforzo la prueba para esperar bytes reales antes de interrumpir. La integracion
  final completa aprobo; el marcador conserva `--immutable`.
- Todos los contenedores, archivos temporales y datos de las pruebas integrales fueron
  eliminados. Los inventarios de contenedores y volumenes preexistentes quedaron iguales.
  En esa etapa local no se conectó Google Drive ni se modificaron bases existentes.
  La evidencia posterior real con Drive está en [Etapa 3](m-10-drive-resultados.md).

## Limitaciones conservadas

El legajo se congela al preparar el tar, pero no es una instantanea transaccional con
el dump PostgreSQL. Debe coordinarse escritura de archivos en una recuperacion real.
Los paquetes locales se conservan incluso despues del envio; su limpieza y la politica
de retencion remota quedan pendientes. Si falla el destino por mucho tiempo, crece el
uso de disco. La verificacion `--download` lee toda la copia y consume ancho de banda.
Una confirmacion local no es un monitor continuo de disponibilidad del remoto.
