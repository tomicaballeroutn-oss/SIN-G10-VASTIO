# Eliminación de proveedores: resguardo y despliegue V9

La decisión del 19/10/2026 elimina la funcionalidad completa y reemplaza conservar proveedores deshabilitados. El 20/10/2026 se autorizó implementar el cambio y probarlo en PostgreSQL descartable. **No está autorizado ejecutar V9 en bases reales o productivas.** La publicación de la rama y del PR fue autorizada posteriormente; esa autorización no habilita el despliegue ni la migración de una instalación real.

## Qué cambia

Se retiran `/api/v1/proveedores` y su ABM, entidad, DTO, servicio, repositorio y cliente frontend. Catálogo e ingresos dejan de recibir/devolver `proveedorId`, `proveedorHabitual` y `proveedor`. El inventario inicial se guarda por ubicación, sin segundo paso. Se mantienen remito, fechas, usuario, identificadores de ingresos, bebidas, códigos, cantidades, movimientos, preparación, devoluciones, cierre y consulta. Los permisos compartidos de catálogo/ingreso siguen vigentes.

V9 elimina `proveedor`, `bebida.proveedor_habitual_id` e `ingreso.proveedor_id`. V1–V8 quedan intactas. Una instalación nueva ejecuta toda la cadena y después inicializa el catálogo/demo sin proveedores. Un esquema V8 con datos afectados exige resguardo validado. Una base sin proveedores ni referencias puede migrar directamente porque no hay información de proveedores que archivar.

## Protección de V9

La migración bloquea `proveedor`, `bebida` e `ingreso` en modo ACCESS EXCLUSIVE, calcula SHA-256 sobre JSONB ordenado de **todos** los proveedores y ambas relaciones (incluidos NULL), y verifica el recibo `public.resguardo_proveedores_v9`. Exige coincidencia de huella, nombre y OID de base, hashes SHA-256 de dump/manifiesto y fecha de verificación. Una modificación posterior del maestro o de las relaciones invalida el recibo. El recibo se consume al eliminar las estructuras. No hay flag global de configuración para saltar esta protección.

El script crea el recibo únicamente después de comprobar hashes y restaurar el dump completo en un contenedor nuevo. Al autorizar vuelve a restaurarlo y comprueba la huella de la fuente bajo bloqueo SHARE. V9 comprueba otra vez bajo bloqueo exclusivo, en su misma transacción. Si falla una dependencia no prevista, el DDL se revierte: no utiliza CASCADE.

El recibo es una constancia operativa, **no una firma criptográfica ni defensa contra un DBA malicioso**: un administrador puede falsificar tablas o modificar migraciones. El rol de aplicación no debe poder escribir el recibo; solo el operador de migraciones. Los archivos externos, sus copias y la custodia requieren control humano. V9 no puede leer ni comprobar su disponibilidad externa desde PostgreSQL. La huella cubre la información eliminada, no todos los cambios posibles del resto de la base; detener toda escritura sigue siendo obligatorio para un respaldo integral de despliegue coherente.

Usar para `authorize` el mismo rol de migración que ejecutará V9: necesita leer el recibo y ser propietario (o tener los permisos de propietario mediante el mecanismo de roles de la instalación) para alterar/eliminar las tablas y consumir la constancia. Separar ese rol del usuario de operación diaria. Spring permite configurar las credenciales de Flyway con `SPRING_FLYWAY_USER` y `SPRING_FLYWAY_PASSWORD` desde el gestor de secretos; no registrarlas en comandos ni archivos versionados. Revisar propietarios/permisos antes del despliegue; un rol sin acceso al recibo falla de forma segura.

## Operación manual obligatoria antes de desplegar (pendiente)

No ejecutar estos pasos contra una base real sin autorización operativa específica. Primero acordar custodio, almacenamiento externo a la base, permisos, cifrado del volumen/archivo y plazo de conservación. El dump completo contiene también información ajena a proveedores: tratarlo como respaldo confidencial, sin agregarlo a Git ni adjuntarlo al PR. `respaldos/` y `backend/target/` están ignorados.

Requisitos: Python 3.11+, Docker con imagen `postgres:17`, clientes PostgreSQL 17 `pg_dump`/`psql` si la fuente no es un contenedor. El script no instala herramientas ni cambia Docker globalmente. Las restauraciones de verificación usan contenedores con nombre aleatorio, sin red, puertos publicados ni volúmenes persistentes, y se eliminan al terminar. Dentro de cada contenedor, la espera y restauración usan TCP en `127.0.0.1` para esperar al servidor definitivo: el servidor temporal de inicialización solo acepta sockets y se reinicia. No se expone ningún puerto ni se cambia la conexión a la fuente. Se requiere espacio para dump, extracto y restauración completa; el extracto JSON se mantiene en memoria.

1. Detener tráfico y todos los procesos que escriban, incluidos jobs y clientes antiguos. Mantenerlos detenidos hasta completar el despliegue o cancelarlo. Verificar que la fuente sea la instalación correcta y Flyway esté en V8, con checksums V1–V8 válidos. Inspeccionar dependencias PostgreSQL y consumidores externos no versionados; no confiar solo en el análisis del repositorio.
2. Configurar conexión libpq con `PGHOST`, `PGPORT`, `PGPASSFILE` y, donde corresponda, `PGSSLMODE=verify-full`/certificados. No poner contraseña ni URI en comandos, informes o manifiestos. `--database` acepta solo un nombre de base. Para una fuente en contenedor se puede usar `--source-container NOMBRE`; los comandos se ejecutan allí como el usuario indicado.
3. Crear el respaldo en un directorio **nuevo** sobre almacenamiento controlado:

   ```text
   python scripts/resguardo_proveedores.py create RUTA_ARCHIVO --database BASE --user OPERADOR --pg-bin RUTA_BIN_POSTGRESQL17
   ```

   Produce `v8.dump` (formato custom completo), `datos-afectados.json` (todos los campos del maestro y pares ID/relación) y `manifest.json` (recuentos, identidad de base, huella, hashes y fecha UTC). Restaura el dump en PostgreSQL 17 descartable y compara el estado afectado con la fuente. Si falla, no hay recibo ni migración: conservar evidencia y repetir en otro directorio cuando se resuelva la causa. `create` y `verify` no escriben en la fuente.
4. Aplicar ACL exclusivas para custodios en Windows; el chmod del script no sustituye ACL NTFS. En otros sistemas verificar permisos del directorio. Copiar el archivo a la ubicación definitiva protegida; comprobar hashes allí con `verify`, que además vuelve a restaurar y compara el extracto completo:

   ```text
   python scripts/resguardo_proveedores.py verify RUTA_ARCHIVO --database BASE --user OPERADOR
   ```

5. Revisión humana: verificar ubicación, custodio, recuentos, restauración completa y correspondencia con la instalación. Registrar aprobación y evidencia en el procedimiento de despliegue. Con escrituras aún detenidas, instalar el recibo mediante:

   ```text
   python scripts/resguardo_proveedores.py authorize RUTA_ARCHIVO --database BASE --user OPERADOR --pg-bin RUTA_BIN_POSTGRESQL17 --writes-stopped
   ```

   Este comando **no ejecuta V9**; crea/actualiza únicamente la constancia transitoria en la fuente. Si fuente, archivo o extracto cambiaron, falla. La restauración se repite, no confía en un booleano guardado en el manifiesto. `--writes-stopped` es declaración del operador, no un sistema de mantenimiento automático.
6. **Punto de detención antes de la operación irreversible:** revisar el despliegue y obtener autorización específica para esa base. En esta tarea no se cruza este punto sobre una base real. El siguiente arranque con el backend nuevo y Flyway habilitado ejecutaría V9 automáticamente: no iniciarlo antes de la aprobación. No iniciar una versión de aplicación con V9 sobre un esquema poblado sin este procedimiento.
7. En un despliegue autorizado por separado, usar un rol dedicado, actualizar backend/frontend coordinadamente, ejecutar migración y regresiones, invalidar clientes/PWA anteriores y reabrir tráfico. Archivar manifiesto, hashes, aprobación y logs fuera de la base. Si se abandona el despliegue, retirar el recibo con el operador autorizado antes de volver a habilitar escrituras.

El verificador externo valida la restauración íntegra y compara el extracto afectado. No reconstruye valores fiscales/contactos anteriores a ediciones del maestro que V8 nunca registró. Se conserva el estado existente al respaldar, incluidos inactivos y proveedores sin referencias.

## Restauración

Para comprobar un archivo no es necesario restaurar sobre la fuente: usar `verify`, que solo acepta un destino descartable creado por el propio script. No existe parámetro de URL de restauración que permita apuntarlo accidentalmente a producción.

Para recuperación real, detener escrituras y obtener autorización específica; crear una base PostgreSQL vacía separada, restaurar con clientes PostgreSQL 17 (`pg_restore --exit-on-error --no-owner --no-privileges -d BASE_RECUPERACION v8.dump`), y reasignar propietarios/permisos necesarios según la instalación. Comparar el extracto ordenado y huella del archivo, los recuentos de tablas y Flyway V8; arrancar la versión anterior compatible con V8 y verificar acceso/operación antes de cambiar conexiones. No arrancar el backend nuevo contra la base restaurada: volvería a intentar V9. Nunca restaurar directamente encima de una base productiva.

La restauración completa devuelve la base al instante del dump. Si ya hubo operaciones después de V9, un rollback completo podría perderlas: requiere reconciliación específica, no ejecutar el procedimiento automáticamente. Para consultar únicamente datos archivados, restaurar una copia aislada y consultar maestro/relaciones allí; no recrear gestión operativa de proveedores.

## Pruebas reproducibles

- Backend: Java 25 y Docker; `mvn -B verify` dentro de `backend` ejecuta unitarias e integraciones. Definir `VASTIO_TEST_PYTHON` con la ruta de Python 3.11+ para incluir el ensayo real de dump/restauración/recibo de `MigracionProveedoresIT`. Si no se define, ese único ensayo externo se omite explícitamente; las otras pruebas SQL siguen ejecutándose.
- CI configura Python 3.12 con `actions/setup-python` v7 fijado a su SHA, verifica el ejecutable y pasa su salida `python-path` como `VASTIO_TEST_PYTHON` a `./mvnw -B verify`. Así se incluye el ensayo real en cada PR. No requiere credenciales ni conexión a una base existente: Java crea la fuente en Testcontainers y Python crea sus propias restauraciones descartables. V9 y su protección no cambian.
- `MigracionProveedoresIT`: migración vacía y V8, bloqueo sin recibo, recibo obsoleto, datos preservados, dependencia externa sin CASCADE y mecanismo real de resguardo. Cada base se crea en un contenedor de pruebas, nunca toma el datasource de desarrollo.
- Frontend: `npm ci`, `npm test`, `npm run lint`, `npm run build` (incluye TypeScript). Usar una versión de Node compatible con el lockfile.
- E2E: levantar backend/frontend con puertos y base descartable exclusivos; ejecutar los escenarios de Sprint 3, operación, preparación y consulta. No utilizar el datasource real ni un contenedor existente para estas pruebas.

Ver [resultados y limitaciones de esta ejecución](eliminacion-proveedores-resultados.md).
