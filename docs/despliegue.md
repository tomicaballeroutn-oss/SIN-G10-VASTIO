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
