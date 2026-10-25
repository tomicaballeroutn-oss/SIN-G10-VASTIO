# M-10 — Etapa 3: validación integral con Google Drive

Fecha: 23/10/2026 (Argentina). Rama: `codex/m-10-respaldo-local`.
HEAD/base: `c05c8efa9f079502481c19ac706394a49b05f7e5`.

## Resultado del ensayo real

**La integración M-10 con Google Drive queda validada para este ensayo sintético.**
El respaldo y la transferencia se ejecutaron mediante los scripts reales; se descargó
el paquete desde Drive y se restauró con PostgreSQL 17.11 y `pg_restore --exit-on-error`.
Esto no acredita un despliegue productivo ni una restauración del esquema completo de VASTIO.

El informe anterior de acceso fallido queda reemplazado por este ensayo. El diagnóstico
posterior identificó falta de certificados CA, no demostró un problema de permisos OAuth.
La imagen corregida incorpora `ca-certificates`; no se modificó el remoto ni el alcance `drive.file`.

## Recursos y aislamiento

- Docker Engine 29.8.2; imagen `vastio-m10-respaldo-test:local`, construida con
  `deploy/respaldo.Dockerfile`; rclone Debian `v1.60.1-DEV`.
- PostgreSQL: `17.11 (Debian 17.11-1.pgdg13+2)` en origen y destinos.
- Red exclusiva `m10drive_393f0abaa30e`; sin puertos publicados.
- Contenedores: `m10drive_393f0abaa30e_source`, `m10drive_393f0abaa30e_restore` y `m10drive_393f0abaa30e_backup`.
- Segunda restauración para secuencias: `m10drive_393f0abaa30e_seq_65ed83`,
  con `--network none`.
- PGDATA en tmpfs en todos los contenedores; no se crearon volúmenes persistentes.
- Base sintética `m10_synthetic`, entorno `m10drive_393f0abaa30e`. Autenticación trust limitada
  al ensayo descartable, sin credenciales reales ni exposición de puertos; no es una receta productiva.
- Configuración existente `%LOCALAPPDATA%\VASTIO\rclone\rclone.conf`, directorio
  montado en `/config/rclone` en solo lectura. No se copió, inspeccionó ni editó su contenido.
- Directorio host temporal `m10drive_393f0abaa30e`: legajo sintético, descarga y ejecutable rclone para WSL.

Se registraron los inventarios de contenedores, volúmenes y redes antes y después:
coincidieron exactamente. No se ejecutó Compose up ni se conectó a los contenedores
preexistentes `vastio-db` o `keycloak`, ni a ninguna base preexistente.

## Procedimiento y comandos relevantes

1. Preflight: `rclone --config /config/rclone/rclone.conf lsf drive:VASTIO-M10-PRUEBAS/ --max-depth 1`.
   Se encontró `prueba.txt` y se comprobó que la subcarpeta exclusiva todavía no existía.
2. Se crearon tres tablas relacionadas: `clientes`, `eventos`, `documentos`, dos filas
   por tabla, PK, FK, UNIQUE, NOT NULL, CHECK, índice explícito y tres secuencias
   (identity, bigserial y serial). Se prepararon tres archivos de legajo sintéticos.
3. En el contenedor de respaldo se ejecutó:

```sh
/bin/sh /opt/vastio/respaldo_local.sh --once
```

Variables exclusivas: `PGHOST=m10drive_393f0abaa30e_source`, `PGUSER=postgres`,
`PGDATABASE=m10_synthetic`, `RESPALDO_ENTORNO=m10drive_393f0abaa30e`,
`RESPALDO_TRANSFERIR=1`, `RCLONE_CONFIG=/config/rclone/rclone.conf`,
`RCLONE_DESTINO=drive:VASTIO-M10-PRUEBAS/m10drive_393f0abaa30e`.
Los scripts del checkout y el legajo se montaron en solo lectura.
El script principal llamó al auxiliar real `transferir_respaldo.sh` bajo su bloqueo;
no se invocó el auxiliar por fuera del protocolo de exclusión.

4. Código de salida del ciclo: **0**. Registro de aplicación:

```text
OK respaldo publicado=/respaldos/m10drive_393f0abaa30e/m10_synthetic/vastio-20261023-233902-belM4G.dump
OK transferencia paquete_preparado
OK transferencia paquete_verificado
OK respaldo ciclo_completo entorno=m10drive_393f0abaa30e base=m10_synthetic
```

5. Dump `vastio-20261023-233902-belM4G.dump`: cabecera custom `PGDMP`, no vacío,
   `pg_restore --list` exitoso, sin archivos `.partial` en este ciclo.
6. Se listó el paquete remoto: exactamente `datos.tar`, `SHA256SUMS`, `COMPLETO`.
   El dump y el legajo están dentro de `datos.tar`; no son objetos remotos separados.
7. Se descargó con `rclone copy <paquete-remoto> /recuperado --checksum` a un directorio
   nuevo y se ejecutó `cd /recuperado && sha256sum -c SHA256SUMS`: resultado correcto.
8. Se extrajo el tar verificando rutas relativas seguras y ausencia de enlaces,
   bloqueos, `.partial` y `.tmp`. Se copió únicamente el dump descargado al destino vacío:

```sh
pg_restore --exit-on-error -U postgres -d m10_synthetic /tmp/recuperado.dump
```

Código de salida: **0**. El destino no tenía tablas de usuario antes de restaurar.

## Integridad y marcador final

SHA-256 de `datos.tar` original, manifiesto y copia descargada coincidente:

```text
0bf830398149cbc39af5fb67805fb5c38fb05e5b62b189a83314a65567d851f7
```

El auxiliar ejecuta `rclone copy --checksum`, después `rclone check --download`,
y finalmente `rclone copyto .../COMPLETO --checksum --immutable`. En la versión ensayada
operaba bajo `set -e`; la corrección posterior comprueba explícitamente cada paso dentro
de `procesar_paquete` para continuar con otros paquetes sin saltarse validaciones.
Evidencia: `scripts/transferir_respaldo.sh`, etapas `rclone_copy`, `rclone_check`,
`rclone_completo`. La ejecución real terminó las tres operaciones y
el marcador descargado contiene `Paquete completo M-10`.
No se hizo observación temporal de cada escritura en Drive: el orden se verifica
por el flujo secuencial del script y las regresiones de fallo/interrupción con backend local.
En estas últimas se exige ausencia de `COMPLETO` antes del reintento exitoso.

- Esquema origen/restaurado idéntico mediante `pg_dump --schema-only --no-owner --no-privileges`,
  omitiendo únicamente los delimitadores aleatorios `restrict/unrestrict` de pg_dump.
  Incluye tablas, claves, índices, CHECK, secuencias y sus asociaciones.
  SHA-256 del esquema normalizado: `4de4c93b41cecf8d00814398be67ac46675bf3a13743166bb162d15002ee3f58`.
- Contenido ordenado de las tres tablas idéntico (6 filas en total), comparado como JSON.
  SHA-256: `62577d8bec1361b8dc3686fe1973d04c7d0f8734150bfa8f47115b4ed554e4a3`. El contenido de origen permaneció idéntico al finalizar.
- Secuencias restauradas: `last_value=2`, `is_called=true`, igual al origen en las tres.
- Se rechazaron escrituras inválidas con SQLSTATE: FK `23503`, CHECK `23514`, UNIQUE
  y PK `23505`, NOT NULL `23502`.
- Esos intentos inválidos consumen valores de secuencia. En el primer destino se repusieron
  explícitamente los valores sintéticos antes de probar inserciones. Para acreditar además
  el comportamiento sin ajustes se hizo una **segunda restauración nueva desde el mismo
  dump descargado**, sin red y sin `setval`: las primeras inserciones devolvieron **3, 3, 3**,
  con relaciones válidas y sin colisiones.
- Los tres archivos de legajo coincidieron por ruta y SHA-256, incluyendo un archivo binario:
  - `evento-1/contrato.txt`: `84b7f2dad5fcf09d250bc9fce3dcb0c52eaf51c1536ac5e430697c4a46eb47d2`.
  - `evento-2/acta.txt`: `e8e423a5364d0f9bf28a2ea2eea41f620d765fcde66db29ad02d80eca7dccfb6`.
  - `evento-2/anexo.bin`: `785b0751fc2c53dc14a4ce3d800e69ef9ce1009eb327ccf458afe09c242c26c9`.

## Pruebas automatizadas y comprobaciones

En Ubuntu WSL se ejecutaron las suites existentes, sin editar sus archivos:

```sh
python3 -B scripts/test_respaldo_local.py
RCLONE_TEST_BIN=<ejecutable-temporal-extraido-de-la-imagen> python3 -B scripts/test_transferencia_respaldo.py
```

La primera suite usa simuladores PostgreSQL y señales/flock Linux reales. La segunda
usa rclone real con backend local temporal y configuración sintética; no lee el remoto
Drive. Resultados: **13/13 aprobadas en 14,243 s** y **7/7 aprobadas en 72,204 s**. Total: **20/20 aprobadas**. No se modificaron las suites.

Compose combinado validado con `config --no-interpolate --quiet` usando únicamente
`.env.prod.example`; no se iniciaron sus servicios.

## Seguridad, conservación y límites

Se usó TLS normal con el almacén CA de la imagen, sin desactivar validaciones ni bundle
externo. Los comandos rclone no usaron trazas HTTP. Su salida cruda se capturó sin
imprimirla; el script de transferencia la suprime. Los logs de aplicación se revisaron
para detectar nombres de tokens, secretos y cabeceras de autorización: no aparecieron.
No se muestra ni guarda el contenido OAuth en el informe. Tamaño y fecha de modificación
del archivo de configuración permanecieron iguales; el montaje fue de solo lectura.

Paquete remoto conservado como evidencia, **sin autorización de borrado**:

```text
drive:VASTIO-M10-PRUEBAS/m10drive_393f0abaa30e/m10drive_393f0abaa30e/m10_synthetic/vastio-20261023-233902-belM4G
```

No se modificó ni eliminó `prueba.txt` ni otros objetos preexistentes. No se implementó
retención externa, cifrado, cambios de permisos ni despliegue productivo.
La coherencia transaccional entre PostgreSQL y archivos durante escrituras concurrentes
no queda demostrada: el ensayo mantuvo los datos sintéticos estables durante el respaldo.
La configuración productiva RW para renovación OAuth no fue validada por este ensayo RO.

## Limpieza y estado final

Se eliminaron los cuatro contenedores exclusivos y la red del ensayo tras comprobar
sus etiquetas de pertenencia. Los inventarios de contenedores, volúmenes y redes
preexistentes quedaron idénticos. Se eliminaron también el directorio temporal local
`m10drive_393f0abaa30e` (legajo, descarga, rclone y resultados intermedios) y el
orquestador temporal `m10_drive_check.py` después de registrar las evidencias aquí.
El paquete remoto permanece conservado. No se crearon commits, push ni PR.

En esta etapa solo se modificó `docs/m-10-drive-resultados.md` dentro del clon;
se conservaron los cambios pendientes de las etapas anteriores. `git diff --check`
sin errores. La rama y el commit base permanecen sin cambios.

## Corrección posterior a la auditoría, sin repetir acceso a Drive

La evidencia real anterior corresponde al ensayo sintético previo a esta corrección;
no se presenta como una ejecución en Drive de la nueva gestión de cola. El protocolo
copia/check/marcador, OAuth, TLS y el paquete remoto conservado no se modificaron.

Implementado posteriormente: aislamiento de errores por paquete, logs con identificador,
etapa y código, continuación de paquetes independientes y resultado final no cero si
alguno falla. Un fallo de preparación del paquete nuevo permite enviar pendientes previos.
El modo diario conserva 86400 segundos de espera y omite retención ante estos errores.
El diagnóstico y reintento están en [operación de transferencia](m-10-transferencia.md).

Las pruebas ampliadas usan backend local real de rclone y datos sintéticos, con fallos
selectivos inyectados para copy/check/marcador y tar. No usan la cuenta de pruebas ni
suben objetos a Drive. Resultado: **26/26 aprobadas**, 13 locales en 13,802 s y 13 de
transferencia en 228,094 s. Se verificaron colas mixtas, todos válidos, todos fallidos,
conservación, ausencia de marcador para fallidos, reintentos y continuidad ante fallos
de empaquetado. Sintaxis shell/Python, Compose y revisión de espacios y secretos correctas.
Producción sigue pendiente de cuenta institucional/custodios, retención, cifrado,
capacidad, alertas y coordinación transaccional entre base y legajo.
