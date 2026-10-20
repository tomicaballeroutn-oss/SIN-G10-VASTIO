# Despliegue del ambiente de prueba

Un solo servidor con Docker Compose: PostgreSQL 17, backend, frontend servido por Caddy (proxy a `/api` y HTTPS automático) y un respaldo diario de la base. Todo está en `docker-compose.prod.yml`; lo que cambia entre servidores va en `.env.prod`, que nunca se versiona.

HTTPS es obligatorio: sin él, la cámara del celular no lee códigos de barra y la PWA no se instala.

## 1. Servidor

**Opción A: Oracle Cloud Always Free (costo cero).** Región São Paulo, por latencia desde Córdoba.

1. Crear la cuenta. Pide tarjeta para validar, pero no cobra mientras se use lo gratuito.
2. *Compute → Instances → Create instance*:
   - Imagen **Ubuntu 24.04**.
   - Forma **VM.Standard.A1.Flex** (Ampere, arm64) con 2 OCPU y 12 GB. Si dice que no hay capacidad, reintentar más tarde o en otro dominio de disponibilidad.
   - Descargar la clave SSH.
3. Abrir los puertos en la red: *Virtual Cloud Network → Security List → Add Ingress Rules*, con origen `0.0.0.0/0`, **TCP 80**, **TCP 443** y **UDP 443**.
4. Las imágenes de Ubuntu de Oracle traen además un firewall propio que bloquea todo. Hay que abrirlo en el servidor:
   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p udp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```

**Opción B: un VPS chico pago** (entre US$ 6 y 8 por mes), con Ubuntu 24.04 y 2 GB de RAM o más. Solo hay que abrir 80/tcp, 443/tcp y 443/udp en su firewall. Tres meses de piloto entran en el presupuesto.

## 2. Dominio

Alcanza con un subdominio gratuito que apunte a la IP pública del servidor:

- **sslip.io**, sin registro: si la IP es `203.0.113.7`, el dominio es `203-0-113-7.sslip.io`.
- **DuckDNS** (duckdns.org), con registro: `vastio-g10.duckdns.org`. Es más estable y conviene si Let's Encrypt rechaza el certificado de sslip.io por límite de emisiones.

Caddy pide y renueva el certificado solo; no hay que hacer nada más.

## 3. Instalar y levantar

```bash
# En el servidor
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker

git clone https://github.com/tomicaballeroutn-oss/SIN-G10-VASTIO.git vastio
cd vastio
cp .env.prod.example .env.prod
```

Completar `.env.prod`:

| Variable | Valor |
|---|---|
| `DOMINIO` | el del paso 2 |
| `DB_PASSWORD` | `openssl rand -base64 24` |
| `VASTIO_JWT_SECRETO` | `openssl rand -base64 48` |
| `SPRING_PROFILES_ACTIVE` | `puesta-en-marcha` (solo la primera vez) |
| `VASTIO_ADMIN_CONTRASENA` | contraseña del primer usuario de Dirección (solo la primera vez) |

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f backend   # esperar «Usuario inicial de Dirección creado»
```

Entrar a `https://<DOMINIO>` con `direccion` y la contraseña elegida. Después, **dejar vacías `SPRING_PROFILES_ACTIVE` y `VASTIO_ADMIN_CONTRASENA`** en `.env.prod` y reiniciar el backend:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend
```

El resto de los usuarios se crea desde la aplicación (historia «Administrar usuarios»).

## 4. Actualizar a una versión nueva

```bash
cd vastio && git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Flyway aplica las migraciones nuevas al arrancar el backend.

## 5. Respaldos

El servicio `respaldo` hace un `pg_dump` por día en `./respaldos/` y conserva los últimos 14. También copia a `./respaldos/legajo/` los archivos nuevos del legajo (contratos digitalizados), que viven en el volumen `legajo`: la base guarda solo la ruta, así que **un respaldo de la base sin esa carpeta no alcanza para recuperar los contratos**. **Esos archivos están en el mismo servidor: si el servidor se pierde, se pierden con él.** Falta decidir a dónde se copian. Una opción sin costo es Google Drive con `rclone`: se configura una vez con `rclone config` y después un cron del servidor copia la carpeta todos los días:

```bash
# crontab -e
30 4 * * * rclone copy /home/ubuntu/vastio/respaldos drive:vastio-respaldos
```

Restaurar un respaldo (reemplaza los datos actuales):

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod stop backend
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T respaldo \
  pg_restore --clean --if-exists -d vastio /respaldos/vastio-AAAAMMDD-HHMM.dump
docker compose -f docker-compose.prod.yml --env-file .env.prod start backend
```

## 6. GitHub: proteger `main`

Lo hace quien administra el repositorio, en *Settings → Branches → Add branch ruleset* (o *Add rule*) para `main`:

- *Require a pull request before merging*, con **1 aprobación**.
- *Require status checks to pass*: **Backend** y **Frontend** (aparecen después de la primera corrida de CI).
- *Block force pushes*.

Si el repositorio es privado y la cuenta es gratuita, GitHub no aplica estas reglas. Hay dos salidas: hacer público el repositorio o activar GitHub Pro, que es gratis con el Student Pack.

## 7. Probarlo en tu máquina

El mismo stack corre en local con `DOMINIO=localhost`. Caddy usa su propia CA y el navegador avisa que el certificado no es de confianza. Así se probó antes de publicar:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
cd frontend
E2E_URL=https://localhost E2E_IGNORAR_CERTIFICADO=1 E2E_USUARIO=direccion E2E_CONTRASENA=... npm run e2e
```

No correrlo en la misma máquina donde ya hay algo escuchando en los puertos 80 o 443.

## Actualización V9: operación previa obligatoria

Antes de arrancar una versión que incluya V9 sobre una instalación existente, seguir el [resguardo, restauración verificada y aprobación manual](eliminacion-proveedores-despliegue.md). Flyway migra al iniciar: no arrancar el backend nuevo contra una base real antes de esa aprobación. V9 bloquea bases con datos de proveedores sin constancia válida para su estado actual.

## Recrear la base de desarrollo descartable

El seed anterior cargaba dos proveedores: una base local V8 con ese seed bloquea V9. **Recrear elimina todos los datos del volumen, no solo los proveedores**, incluidos usuarios, eventos, ingresos y movimientos. Solo corresponde si el responsable confirma que son datos sintéticos descartables. Si hay datos históricos, trabajo que conservar o dudas, detenerse y seguir el [resguardo completo, restauración y autorización de V9](eliminacion-proveedores-despliegue.md); ser una base «local» no la exime. No borrar la tabla ni falsear el recibo para evitar la protección.

Estos son pasos **manuales**, no un script para ejecutar en bloque. No se ejecutan desde CI ni durante el arranque. Antes de borrar, cerrar backend, IDE con arranque automático y cualquier otro escritor conectado a esa base. No usar este procedimiento con `docker-compose.prod.yml`, bases compartidas o remotas.

### 1. Identificar el destino (solo lectura)

En PowerShell, desde la raíz del worktree, comprobar el contexto Docker y copiar el nombre real del proyecto de desarrollo listado. Si no aparece, revisar con el responsable dónde está la base; no adivinar nombres ni crear otro proyecto para borrar el anterior. Usar el mismo archivo y opciones de entorno con los que se creó el servicio. El ejemplo utiliza el Compose de desarrollo y su `.env` habitual:

```powershell
docker context show
docker compose ls --all
$archivoCompose = (Resolve-Path './docker-compose.yml').Path
$proyecto = Read-Host 'Nombre EXACTO del proyecto de desarrollo identificado'
$dc = @('compose', '--project-name', $proyecto, '--file', $archivoCompose)
docker @dc config --services
docker @dc ps --all db
$ids = @(docker @dc ps --all --quiet db)
if ($ids.Count -ne 1) { throw 'Se requiere exactamente un contenedor db identificado' }
$idDb = $ids[0]
$infoDb = (docker inspect $idDb | ConvertFrom-Json)[0]
$infoDb | Select-Object Id, Name
$infoDb.Config.Labels | Select-Object 'com.docker.compose.project', 'com.docker.compose.service', 'com.docker.compose.project.config_files'
$infoDb.Mounts | Select-Object Type, Name, Source, Destination
docker port $idDb
$montajes = @($infoDb.Mounts | Where-Object Destination -eq '/var/lib/postgresql/data')
if ($montajes.Count -ne 1 -or $montajes[0].Type -ne 'volume') { throw 'No es el volumen Docker esperado: revisar manualmente, no borrar' }
$volDatos = $montajes[0].Name
$base = (docker exec $idDb printenv POSTGRES_DB).Trim()
$usuarioDb = (docker exec $idDb printenv POSTGRES_USER).Trim()
if (!$volDatos -or !$base -or !$usuarioDb) { throw 'Identificación incompleta: detenerse' }
docker volume inspect $volDatos
docker ps --all --filter "volume=$volDatos" --format '{{.ID}} {{.Names}} {{.Status}}'
docker exec $idDb psql -X -U $usuarioDb -d $base -c 'SELECT current_database(), current_user;'
docker exec $idDb psql -X -U $usuarioDb -d $base -c 'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;'
```

Registrar contenedor/ID, volumen, base, usuario, puerto y proyecto obtenidos. Verificar que las etiquetas corresponden al proyecto/archivo de desarrollo seleccionado y al servicio `db`, que el backend usa esa misma base/puerto (`application-dev.yml`, `.env` y posibles variables de entorno del proceso) y que el volumen solo pertenece a ese contenedor. No imprimir contraseñas. Si hay otros consumidores del volumen, un bind mount, un contexto remoto o cualquier discrepancia, detenerse; esta receta no aplica. Los valores por defecto del repositorio no prueban qué instalación se está usando.

### 2. Punto de decisión y recreación manual

**Detenerse aquí hasta confirmar el destino y aceptar expresamente la pérdida completa de esos datos descartables.** Un respaldo histórico validado sigue siendo obligatorio si hay información que conservar; esa base se migra con el procedimiento V9, no se recrea con esta receta.

Solo después de esa decisión, ejecutar cada comando y comprobar su resultado. `compose rm` pide confirmación; se elimina exclusivamente el servicio identificado, y después únicamente su volumen. No usar `down --volumes`, `volume prune` ni un nombre de volumen supuesto.

```powershell
docker @dc stop db
docker @dc rm db
docker ps --all --filter "volume=$volDatos" --format '{{.ID}} {{.Names}}'
# Debe quedar sin consumidores. Revisar de nuevo el nombre antes de la siguiente línea.
docker volume rm $volDatos
docker @dc up --detach db
docker @dc ps db
```

Si un comando falla, detenerse; no forzar la eliminación. Verificar que `db` esté saludable. Mantener el `.env` de desarrollo elegido; PostgreSQL crea la base y el usuario configurados al inicializar el volumen nuevo. Iniciar el backend actualizado desde `backend` con Java 25:

```powershell
cd backend
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

Flyway ejecuta V1–V9 sobre la base vacía; después el seed actual, si se habilitó con `VASTIO_DEMO_CONTRASENA`, carga la demo sin proveedores. No se necesita ni se instala un recibo para una base vacía.

### 3. Verificar el nuevo esquema

En la consola anterior, desde la raíz, obtener el ID nuevo (el anterior ya fue eliminado) y comprobar la historia de migraciones y la ausencia de las relaciones retiradas:

```powershell
$idDb = (docker @dc ps --quiet db).Trim()
docker exec $idDb psql -X -U $usuarioDb -d $base -c 'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;'
docker exec $idDb psql -X -U $usuarioDb -d $base -c "SELECT to_regclass('public.proveedor') IS NULL AS sin_tabla, NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND ((table_name = 'bebida' AND column_name = 'proveedor_habitual_id') OR (table_name = 'ingreso' AND column_name = 'proveedor_id'))) AS sin_relaciones;"
```

Deben aparecer las nueve versiones exitosas, la última `9 / eliminar proveedores / true`, y `sin_tabla = true`, `sin_relaciones = true`. Confirmar además arranque sin errores de Flyway/Hibernate y acceso a catálogo, inventario e ingresos. Si no se cumple, conservar el log y revisar la conexión; no editar migraciones históricas ni desactivar Flyway.
