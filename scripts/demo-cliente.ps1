# Demo para el cliente: base de datos aparte, cargada desde cero con el escenario completo.
#
#   .\scripts\demo-cliente.ps1
#
# Qué hace:
#   1. Recrea el contenedor vastio-demo (PostgreSQL 17 en el puerto 5434). La base de desarrollo (vastio-db, 5432) no se toca.
#   2. Abre una ventana con el backend (perfil dev, http://localhost:8080) apuntando a esa base, con
#      VASTIO_DEMO_COMPLETO=true: al arrancar carga los datos de demostración y el escenario completo.
#   3. Abre otra ventana con el frontend (http://localhost:5173).
#
# Requisitos: Docker Desktop abierto, VASTIO_DEMO_CONTRASENA en el .env (es la contraseña de todos los usuarios de
# ejemplo) y nada escuchando en el 8080 (cerrá el backend de desarrollo si lo tenés levantado).
# Correlo el mismo día de la reunión: las fechas son relativas a hoy y el evento del mediodía pasa a En curso a las 12.

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot

docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker no está corriendo: abrí Docker Desktop y volvé a intentar.' }
if (-not (Select-String -Path (Join-Path $raiz '.env') -Pattern '^VASTIO_DEMO_CONTRASENA=.+' -Quiet)) {
    throw 'Falta VASTIO_DEMO_CONTRASENA en el .env (la contraseña de los usuarios de ejemplo).'
}
if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    throw 'El puerto 8080 está ocupado: cerrá el backend que esté corriendo.'
}

Write-Host 'Recreando la base de demo (vastio-demo, puerto 5434)...'
docker container inspect vastio-demo *> $null
if ($LASTEXITCODE -eq 0) { docker rm -f vastio-demo | Out-Null }
docker run -d --name vastio-demo -e POSTGRES_DB=vastio -e POSTGRES_USER=vastio -e POSTGRES_PASSWORD=vastio -p 5434:5432 postgres:17 | Out-Null
do { Start-Sleep -Seconds 1; docker exec vastio-demo pg_isready -U vastio -d vastio *> $null } until ($LASTEXITCODE -eq 0)

Write-Host 'Abriendo el backend (carga los datos al arrancar, tarda un minuto)...'
Start-Process powershell -WorkingDirectory (Join-Path $raiz 'backend') -ArgumentList '-NoExit', '-Command',
    "`$env:DB_PORT='5434'; `$env:VASTIO_DEMO_COMPLETO='true'; .\mvnw clean spring-boot:run"

Write-Host 'Abriendo el frontend...'
Start-Process powershell -WorkingDirectory (Join-Path $raiz 'frontend') -ArgumentList '-NoExit', '-Command', 'npm run dev'

Write-Host ''
Write-Host 'Listo. Cuando el backend diga "Escenario completo de demostración cargado", entrá a http://localhost:5173'
Write-Host 'Usuarios (contraseña: la de VASTIO_DEMO_CONTRASENA): melina.sifon (Coordinación), gabriela.paz (Administración),'
Write-Host 'lucia.ferreyra y sofia.mendez (Vendedoras), ana.sosa y carla.nunez (Planners), nicolas.herrera (Compras),'
Write-Host 'rocio.blanco (Barra), julian.torres (Cocina). Dirección: el usuario inicial del .env.'
