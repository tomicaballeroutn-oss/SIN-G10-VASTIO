# M-10 — Etapa 1: preparación del respaldo local

Estado posterior: la Etapa 1.3 verifico dos restauraciones reales con PostgreSQL 17.11,
legajo por SHA-256 y stop/reinicio Docker. La [Etapa 2.1](m-10-transferencia.md) extiende
esta base con transferencia opcional. La [Etapa 3](m-10-drive-resultados.md) verificó el
ciclo real con Google Drive y datos sintéticos. Este documento describe el respaldo
local y su integración actual; no acredita producción.

Base: `main` en `c05c8efa9f079502481c19ac706394a49b05f7e5` (PR #40).
No se presupone que exista producción desplegada. El modo local funciona sin configurar
Drive/rclone. M-10 no cambia Java, frontend ni migraciones; sus ensayos usan únicamente
bases y respaldos sintéticos descartables.

## Cambios

Se extrae el ciclo existente de `docker-compose.prod.yml` a `scripts/respaldo_local.sh`.
Se conservan `postgres:17`, formato custom `pg_dump -Fc`, copia incremental `cp -ru`
del legajo, dependencia del backend saludable y espera de 86400 segundos. El backend
sigue dependiendo de la base saludable; no se cambia el arranque ni Flyway.

- `RESPALDO_ENTORNO` es obligatorio y exclusivo por instalación; `PGDATABASE` sigue
  llegando de `DB_NAME`. Ambos aceptan letras ASCII, números, guion y guion bajo.
  Un nombre de base con otros caracteres se rechaza expresamente; no se transforma.
- Destino: `/respaldos/<entorno>/<base>/`. No se trasladan ni eliminan copias previas.
  La configuración existente requerirá que el operador elija el identificador antes
  de un futuro despliegue. No se modificaron archivos `.env` reales.
- Un temporal exclusivo `vastio-<fecha UTC>-<aleatorio>.partial` recibe el dump.
  Se comprueba código de salida, tamaño no vacío e índice mediante `pg_restore --list`.
  Se copia el legajo y recién entonces se renombra a `.dump` en el mismo directorio.
  El renombrado evita exponer un dump en escritura como terminado en un sistema de
  archivos local que soporte renombrado atómico. No equivale a durabilidad ante corte
  eléctrico ni a una prueba de restauración.
- Un fallo del respaldo local termina el proceso con código no cero y `ERROR respaldo etapa=...`.
  Los fallos posteriores de preparación o transferencia se registran y omiten la retención;
  el modo diario mantiene la espera de 86400 segundos. `--once` y `--transfer-only`
  devuelven error aunque otros paquetes se hayan enviado correctamente.
  No se imprimen variables de conexión ni contraseñas. Los `.partial` no se borran:
  requieren revisión y limpieza posterior del operador. No deben copiarse como backups.
- Solo después de publicar se aplica `find -maxdepth 1 -type f -name 'vastio-*.dump'
  -mtime +14 -delete` en la carpeta propia. Conserva el criterio de antigüedad existente,
  no una cantidad exacta. Un fallo de retención también termina con error, pero el
  dump ya publicado permanece terminado. `ciclo_completo` solo se registra al finalizar
  también la retención, sin fallos de preparación o transferencia.
- El bloqueo usa `flock -n` (util-linux) sobre un descriptor del archivo estable
  `.respaldo.lock`. Solo se mantiene durante el ciclo; se cierra antes de la espera.
  El archivo no se elimina ni se trunca: su existencia no indica un bloqueo activo.
  El kernel libera el bloqueo al cerrarse el último descriptor, incluso tras KILL.
  Los hijos heredan el descriptor: si muere solo el padre y un hijo sigue escribiendo,
  otra ejecución se rechaza hasta que ese hijo termine. No se usa `flock -u`, que
  liberaría también el bloqueo compartido con el hijo. No se decide por antigüedad/PID.
- Los comandos largos se ejecutan como hijos y se esperan con `wait`, interrumpible.
  TERM/INT detienen el hijo activo mediante TERM, lo recogen y cierran el descriptor;
  el script sale con 143/130 respectivamente. Un dump interrumpido no se publica.
  La espera de 86400 segundos también es interrumpible y no hereda el descriptor.
  Un hijo que no termine conserva el bloqueo: se prioriza evitar dos escritores.
- Un `.en-curso` heredado de la implementación anterior no se borra ni se usa como lock.
  Antes de actualizar, detener la versión anterior y verificar que ya no queden sus
  procesos activos. Las dos versiones usan protocolos distintos y no deben coexistir.
- Se rechazan enlaces simbólicos en los directorios que delimitan el entorno y la base.
  El directorio de respaldo debe estar bajo control del operador; esto no es una defensa
  frente a un administrador que modifique rutas o archivos concurrentemente.

El `.dump` indica que estas operaciones terminaron; no certifica recuperación completa.
Un dump y la carpeta incremental de legajo no son un conjunto histórico atómico.

## Pruebas locales reproducibles sin base de datos

Desde la raíz del checkout, en Linux con Python 3.11+, shell POSIX y `flock` real:

```sh
python3 scripts/test_respaldo_local.py
```

Desde PowerShell, con Ubuntu WSL y Python 3 instalados (ajustar la ruta al checkout):

```powershell
wsl -d Ubuntu --exec python3 /mnt/c/Users/tomic/OneDrive/Documentos/ChatGPT/Seminario/vastio-m10/scripts/test_respaldo_local.py
```

La suite crea exclusivamente carpetas temporales `vastio-m10-sintetico-*`, las limpia
al terminar y antepone simuladores de `pg_dump`/`pg_restore` al PATH. El host es
`destino-inexistente.invalid` y no hereda variables PG de la máquina. No invoca Docker,
no lee `.env`, no abre conexiones y no ejecuta restauraciones. La espera también se
simula para no esperar un día: se comprueba que solicite 86400 segundos y repita el ciclo.
Las señales TERM, INT y KILL y el bloqueo `flock` son reales sobre procesos Linux
exclusivos de prueba. Git for Windows no permite acreditar ese comportamiento Linux;
la suite ahora exige Linux/WSL y falla explícitamente si falta `flock`.

Trece pruebas cubren publicación sin sobrescritura, fallos de dump/archivo vacío/índice,
fallo del legajo, retención y aislamiento, identificadores, periodicidad, TERM/INT durante
dump y espera, KILL en ambas fases, recuperación posterior, concurrencia, conservación
del inode de bloqueo y ausencia de bloqueo durante la espera. Tras KILL del padre se
comprueba además que el hijo superviviente siga excluyendo otro escritor, y que al
terminar se habilite el siguiente respaldo. Se comprueba el contenido de cada `.dump`
publicado. Usan contenido sintético; no demuestran una restauración PostgreSQL real.

Se puede validar la estructura de Compose sin arrancar servicios ni leer un `.env` real:

```sh
docker compose --env-file .env.prod.example -f docker-compose.prod.yml config --no-interpolate --quiet
git diff --check
```

Resultado de la corrección 1.2: 13 pruebas aprobadas en Ubuntu WSL con `flock` y señales
Linux reales. La imagen Debian de `postgres:17` incluye util-linux; el script comprueba
`command -v flock` y falla explícitamente si no está disponible. Las pruebas reales
posteriores con PostgreSQL 17.11 y contenedores descartables verificaron restauración
y stop/reinicio. La evidencia vigente con Drive está en [Etapa 3](m-10-drive-resultados.md).
Las suites automatizadas simulan PostgreSQL y no sustituyen esa evidencia integral.

## Operación futura y limitaciones

Los logs del servicio distinguen publicación, ciclo completo y etapa fallida. El código
de salida queda en el estado del contenedor; `restart: unless-stopped` puede reiniciarlo
y repetir antes del día siguiente. No se implementan alertas ni monitoreo en esta etapa.

Cada instalación debe usar un `RESPALDO_ENTORNO` distinto. Compartir ese identificador
y nombre de base en una misma raíz comparte también su retención. Los archivos
históricos de la raíz no se adoptan automáticamente ni se eliminan. La carpeta requiere
permisos adecuados; `umask 077` no reemplaza ACL de Windows ni cifra la información.

Implementado: respaldo local, transferencia opcional y aislamiento por entorno/base.
Comprobado: regresiones automatizadas, restauraciones PostgreSQL 17.11 y ciclo real
con Drive usando datos sintéticos. Pendientes para producción: coherencia temporal con
el legajo, cuenta institucional/custodios, cifrado, conservación externa, capacidad y alertas.
La configuración personal del ensayo no constituye una decisión productiva.
No ejecutar el Compose de despliegue como prueba local: iniciaría
backend/Flyway y podría reutilizar volúmenes existentes.
