<!-- Registro histórico de las Fases 1–2. La continuación autorizada se documenta en eliminacion-proveedores-despliegue.md y eliminacion-proveedores-resultados.md. -->

# Eliminación de proveedores — Fases 1 y 2

Fecha: 20/10/2026. Decisión de producto comunicada por el equipo: reunión del 19/10/2026, eliminación completa; reemplaza conservar proveedores deshabilitados.

**Estado: detenido antes de la Fase 3 por decisión pendiente de conservación de datos.** No se eliminó código, no se creó una migración ejecutable y no se accedió a bases persistentes. Este informe describe el cambio propuesto, no una implementación terminada.

## Base e aislamiento

- Repositorio: `tomicaballeroutn-oss/SIN-G10-VASTIO`.
- Se actualizó `origin/main` mediante fetch y se creó un worktree nuevo desde `02c35649a156c2eea073106a9b9f8a2d28c53860`.
- Rama local: `codex/eliminar-proveedores`, inicialmente siguiendo `origin/main`.
- Worktree: `C:/Users/tomic/OneDrive/Documentos/ChatGPT/Seminario/vastio-eliminar-proveedores`.
- El HEAD contiene el merge de PR #33; B5-04 está integrado vía #31. No se modificó main, ni archivos de otros checkouts, ni stash. Git actualizó únicamente referencias/metadatos necesarios para fetch y creación del worktree.
- No hubo push, PR, commit, acceso a Google Drive, arranque de aplicaciones ni ejecución SQL. El checkout principal está fuera del acceso de lectura del sandbox; no se afirma haber validado su estado local.

## Inventario de dependencias

Los paths de esta sección son relativos al repositorio. El anexo `eliminacion-proveedores-referencias.txt` contiene todas las coincidencias textuales en los archivos versionados del HEAD auditado, con línea. Se buscaron `proveedor`, `supplier` y `cuit`, sin distinguir mayúsculas. Se inspeccionó además el DER binario, que no aparece en búsquedas de texto.

### Backend

Base de los paths Java: `backend/src/main/java/ar/edu/utn/vastio/`.

| Componente | Acción propuesta | Dependencia / motivo |
| --- | --- | --- |
| `bebida/api/ProveedorController.java` | Eliminar | GET y POST `/api/v1/proveedores`; PUT `/{id}` y `/{id}/bebidas`; POST `/{id}/baja` y `/{id}/reactivacion`. Retirar OpenAPI asociado. |
| `bebida/api/ProveedorDto.java` | Eliminar | Requests, responses y resumen de bebidas exclusivos del ABM. |
| `bebida/aplicacion/ProveedorService.java` | Eliminar | Alta, edición, baja, reactivación, asignación de bebidas; validación de razón social activa y CUIT. |
| `bebida/dominio/Proveedor.java` | Eliminar | Entidad, estado activo y verificador de CUIT exclusivos. |
| `bebida/infraestructura/ProveedorRepository.java` | Eliminar | Persistencia y consultas exclusivas. |
| `bebida/dominio/Bebida.java` | Adaptar | Quitar relación habitual, getter y asignación. Conservar identidad, códigos, unidad, estado y cantidades. |
| `bebida/aplicacion/BebidaService.java` | Adaptar | Quitar repositorio inyectado, `DatosBebida.proveedorId`, asignaciones y validación de proveedor. |
| `bebida/api/BebidaDto.java` | Adaptar | Quitar `ProveedorResumen`, `BebidaResponse.proveedorHabitual` y `BebidaRequest.proveedorId`. |
| `bebida/infraestructura/BebidaRepository.java` | Adaptar | Quitar fetch de proveedor y `findByProveedorHabitualId`; conservar fetch de códigos y consultas de saldo/bloqueo. |
| `bebida/dominio/Ingreso.java` | Adaptar | Quitar relación, getters y parámetro de `deMercaderia`. Conservar cabecera e IDs históricos. |
| `bebida/aplicacion/IngresoService.java` | Adaptar | Quitar repositorio, `DatosIngreso.proveedorId`, validación y resolución de proveedor. Conservar validación de fecha, bebidas y cantidades, y transacción de movimientos. |
| `bebida/api/IngresoDto.java` | Adaptar | Quitar propiedad request/response y `ProveedorIdDeserializer`, exclusivo de ese ID. Mantener validaciones de renglones y remito. |
| `bebida/api/IngresoController.java` | Adaptar | Actualizar descripción del contrato. |
| `bebida/infraestructura/IngresoRepository.java` | Adaptar | Quitar `LEFT JOIN FETCH i.proveedor`; conservar destino y orden de recientes. |
| `bebida/api/EntregaDto.java` | Adaptar comentario | Mención descriptiva «sin stock mínimo ni proveedor»; no contiene relación funcional con proveedor. |
| `demo/DatosDeDemostracion.java` | Adaptar | Quitar carga de proveedores, constantes, índice en artículo demo e inyección. Mantener catálogo, inventario y eventos demo. |
| `comun/seguridad/Permisos.java` | Conservar permisos; adaptar comentario | `ProveedorController` usa `CATALOGO_BEBIDA`, compartido con bebidas, ubicaciones, ingresos y ajustes. No se identificó permiso o rol exclusivo que deba eliminarse. |

No se identificaron rutas de seguridad adicionales ni validadores compartidos de CUIT que deban retirarse. La validación exclusiva reside en la entidad/servicio/DTO anteriores.

### Frontend

Base: `frontend/src/`.

| Archivo | Acción propuesta |
| --- | --- |
| `api/proveedores.ts` | Eliminar cliente, tipos y formato de CUIT. |
| `features/bebidas/SeccionProveedores.tsx` | Eliminar ABM y diálogos completos. |
| `api/bebidas.ts` | Quitar `proveedorHabitual` y `proveedorId`. |
| `api/ingresos.ts` | Quitar import, `proveedor` y `proveedorId`. |
| `features/bebidas/PaginaCatalogo.tsx` | Quitar pestaña, import, carga en Promise.all, estado/props y selector habitual. Hoy una falla de proveedores también afecta la carga del catálogo. |
| `features/bebidas/SeccionCargaInicial.tsx` | Quitar paso siguiente a proveedores y callback; adaptar finalización manteniendo guardado, corrección y selección de ubicación. |
| `features/bebidas/PaginaIngresoBebida.tsx` | Quitar carga/listado, selector, estado, request y proveedor de revisión/recientes; preservar remito, fecha y renglones. |
| `features/bebidas/catalogo.css` | Actualizar comentario de pestañas; revisar estilos usados por sección eliminada sin quitar estilos compartidos. |

La navegación está dentro de `/catalogo?pestana=proveedores`; no se encontró una página independiente en el router. El catálogo ya selecciona la primera pestaña si el query no coincide. Preservar y probar esa salida para enlaces antiguos, sin reconstruir una pantalla de proveedores.

### PostgreSQL y Flyway V1–V8

Fuente: `backend/src/main/resources/db/migration/`.

| Objeto | Dependencia | Tratamiento propuesto |
| --- | --- | --- |
| `proveedor` | PK `pk_proveedor`, UNIQUE de CUIT, identidad `proveedor_id` | Eliminar después del resguardo y de retirar las dos FK. Se pierden ID, razón social, CUIT, teléfono, email y activo. |
| `bebida.proveedor_habitual_id` | FK `fk_bebida_proveedor_habitual_id` → proveedor | Eliminar FK y columna; se pierde la asignación habitual vigente. |
| `ingreso.proveedor_id` | FK `fk_ingreso_proveedor_id` → proveedor | Eliminar FK y columna; se pierde qué proveedor corresponde a cada ingreso histórico. |
| `movimiento_stock.ingreso_id` | FK → ingreso | Conservar íntegra; no requiere tocar movimientos. |

Las relaciones y el maestro se crean en V1 (líneas 344–352, 365/371, 417/425). No existe tabla intermedia «bebidas que provee»: es la relación habitual de bebida. PostgreSQL crea índices para PK/UNIQUE; V1–V8 no declaran índices adicionales de estas dos FK. Al eliminar la tabla desaparecen sus índices y la secuencia de identidad dependiente; no hay que alterar secuencias de ingreso o bebida.

- V2: datos iniciales sin inserción de proveedores; los proveedores demo se crean desde Java.
- V3: triggers de solo inserción, incluido `movimiento_stock`; no modifica proveedores.
- V4: servicios contratados; sin dependencia de proveedores.
- V5: catálogo, códigos y cantidades; deja vigente el vínculo habitual de V1.
- V6: ubicaciones; sin dependencia directa de proveedores.
- V7: consumo; sin dependencia directa de proveedores.
- V8: preparación y relación de movimientos; sin dependencia directa de proveedores.

No se encontraron otras FK, vistas, funciones, triggers o SQL versionado dependientes de proveedor. Esto cubre el esquema del repositorio; una instalación puede tener vistas, reportes, integraciones o cambios manuales no versionados. Debe inspeccionarse el catálogo real antes de migrar. No usar `CASCADE`: una dependencia inesperada debe impedir la migración.

### Pruebas y dependencias indirectas

Base backend: `backend/src/test/java/ar/edu/utn/vastio/`.

- `bebida/api/ProveedorIT.java`: retirar pruebas de ABM y sustituir cobertura de ruta por verificación de ausencia del mapping/OpenAPI y de endpoints para usuario autenticado autorizado. Verificar todos los métodos y subrutas, sin aceptar un 401/403 como evidencia de eliminación.
- `bebida/api/IngresoDtoTest.java`: retirar pruebas exclusivas del deserializador; conservar o trasladar cobertura válida de contrato de ingreso.
- Adaptar `bebida/api/IngresoIT.java`, `bebida/aplicacion/IngresoServiceTest.java`, `bebida/api/BebidaSinTransaccionIT.java`: entidades, mocks y aserciones de proveedores; conservar regresiones de ingreso y funcionamiento sin Open Session in View.
- Adaptar fixtures/constructores de `bebida/aplicacion/RecuentoConcurrenciaIT.java` y `PreparacionConcurrenciaIT.java`; no eliminar sus verificaciones de concurrencia.
- Adaptar `PerfilDevIT.java`: expectativas de proveedores demo; conservar catálogo e inventario.
- `MigracionesIT.java`: hoy espera 40 tablas, incluida proveedor; serían 39 si V9 solo elimina proveedor. Agregar pruebas explícitas de ausencia de columnas/FK/tabla y de preservación de ingresos/movimientos.

Frontend: adaptar `features/bebidas/CargaInicialYProveedores.test.tsx` (separar/conservar inventario), `PaginaCatalogo.test.tsx`, `PaginaIngresoBebida.test.tsx`, `RecuentoSinSaldo.test.tsx` y `features/eventos/PestanaPreparacion.test.tsx`. Retirar únicamente escenarios exclusivos del ABM/selector y actualizar fixtures; agregar ausencia de pestaña, campos y llamadas HTTP de proveedores.

No hubo coincidencias directas en `frontend/e2e`. Los E2E `objetivo-sprint-3.spec.ts`, `objetivo-sprint-3-operacion.spec.ts`, `preparacion-evento.spec.ts` y `consulta-consumo.spec.ts` dependen indirectamente de catálogo, ingresos, datos demo y bebidas. Deben ejecutarse sobre servicios y PostgreSQL efímeros exclusivos del nuevo worktree; no utilizar su URL por defecto sin controlar el destino.

### Documentación y diseño

- Adaptar `docs/sprint-3.md`: historia, recorrido, decisiones 5/13/14/19 y UI-28; registrar la decisión del 19/10/2026 y que reemplaza el criterio anterior, sin borrar el historial del proyecto.
- Adaptar `docs/perfiles-y-permisos.md`: fila «Catálogo de bebidas y proveedores» pasa a bebidas; conservar perfiles.
- Adaptar `docs/der/diccionario-de-datos.md`: entidad proveedor y columnas de bebida/ingreso salen del modelo vigente; conservar entrada histórica con referencia a V9/decisión.
- `docs/der/DER_3_bebida.png`: inspección visual confirma entidad PROVEEDOR y ambos vínculos. Actualizar el modelo vigente o identificar claramente como DER histórico y proporcionar un DER vigente; no dejarlo como documentación actual.
- `design/pantallas/UI28-CargaInicial.dc.html`: retirar paso de proveedores y adaptar flujo. Otros prototipos sin coincidencias directas no requieren cambios automáticos.
- `docs/b5-04-implementacion.md`: conservar registro histórico de alcance y enlazar la nueva decisión para evitar interpretarlo como regla vigente.
- Ningún documento externo de Google Drive fue consultado o editado.

## Evaluación de datos y decisión pendiente

La pérdida es **posible y estructuralmente demostrable**, pero no se verificó cuántos registros existen en instalaciones persistentes. No se debe interpretar ausencia de datos en migraciones como ausencia de datos reales: API y demo los generan.

Eliminar el maestro y las columnas pierde datos de contacto/fiscales, estado de proveedores, vínculo habitual actual y asociación proveedor–ingreso. El proveedor de un ingreso no está congelado como texto: la respuesta lo obtiene del maestro mutable; un resguardo captura su valor actual, no reconstruye nombres o CUIT anteriores a modificaciones que el sistema no registró.

Permanecen ingresos (ID, remito, fechas, ubicación, usuario y marca de inventario inicial), movimientos y sus cantidades, bebidas, stock, preparaciones, entregas, devoluciones, consumo y cierre. El remito por sí solo no permite recuperar inequívocamente al proveedor. Conservar movimientos no equivale a conservar toda la información histórica del ingreso.

**Propuesta recomendada para autorizar:** conservar fuera de la base operativa un respaldo verificable del estado V8 y un extracto de maestro y relaciones, y eliminar completamente proveedores del esquema y aplicación mediante V9. No se propone mantener proveedores deshabilitados ni nuevas pantallas de archivo. Antes de ejecutar sobre una instalación hay que definir custodio, ubicación, acceso y plazo de conservación del archivo, además de verificarlo. Este informe no autoriza operaciones sobre bases reales.

### Resguardo verificable previo (propuesto, no ejecutado)

1. Detener escrituras durante la captura y hasta terminar la actualización coordinada; registrar instalación, fecha UTC, versión PostgreSQL, commit y `flyway_schema_history` (V8 validada). No incluir credenciales en manifiestos.
2. Crear respaldo completo en formato custom con `pg_dump`, sin filtrar tablas para permitir restauración coherente. Derivar del mismo snapshot/restauración los extractos ordenados: todas las columnas de proveedor; `bebida_id, proveedor_habitual_id` para todas las bebidas (incluidos NULL); `ingreso_id, proveedor_id` para todos los ingresos (incluidos NULL). Esto incluye proveedores inactivos y no referenciados.
3. Guardar SHA-256 del dump y de cada extracto; manifiesto con recuentos totales, referencias no nulas, filas por proveedor y cero referencias huérfanas. Proteger el archivo por contener CUIT/contactos. Copiarlo a la ubicación acordada y verificar nuevamente hashes allí.
4. Restaurar el dump en PostgreSQL desechable aislado; comparar extractos completos ordenados y hashes/recuentos con el manifiesto. Una suma de filas o `pg_restore --list` por sí sola no demuestra restaurabilidad.
5. Ensayar V9 sobre esa copia y comprobar que las columnas restantes de ingreso/bebida y todas las filas de movimientos, stock, preparaciones, entregas y consumos son idénticas antes/después. Verificar cero objetos operativos de proveedores y repetir regresiones. Archivar evidencia de restauración y comparación.
6. Solo con respaldo verificado y autorización operativa aparte programar el despliegue. Este trabajo no ejecutará cambios destructivos en bases persistentes. Para rollback de esquema se necesita restaurar el respaldo y la versión anterior de aplicación; no reescribir ni borrar la historia Flyway. Una restauración completa posterior al uso de V9 podría perder nuevas operaciones: exige plan específico de reconciliación.

Consultas de diagnóstico de solo lectura, para una copia autorizada del esquema V8:

```sql
SELECT count(*) AS proveedores FROM proveedor;
SELECT count(*) AS bebidas_con_proveedor FROM bebida WHERE proveedor_habitual_id IS NOT NULL;
SELECT count(*) AS ingresos_con_proveedor FROM ingreso WHERE proveedor_id IS NOT NULL;
SELECT c.conrelid::regclass AS origen, c.conname, pg_get_constraintdef(c.oid)
FROM pg_constraint c
WHERE c.contype = 'f' AND c.confrelid = 'proveedor'::regclass;
```

Además de esas FK, inspeccionar `pg_depend`, vistas/materializadas, triggers, funciones y consumidores externos en cada instalación. No se promete detectar sistemas externos consultando únicamente el catálogo PostgreSQL.

### Propuesta de V9 (no instalada ni ejecutada)

Nombre previsto: `V9__eliminar_proveedores.sql`. Se creará únicamente después de resolver la conservación. Cuerpo mínimo propuesto, bajo la transacción de Flyway para PostgreSQL:

```sql
ALTER TABLE bebida DROP CONSTRAINT fk_bebida_proveedor_habitual_id;
ALTER TABLE ingreso DROP CONSTRAINT fk_ingreso_proveedor_id;
ALTER TABLE bebida DROP COLUMN proveedor_habitual_id;
ALTER TABLE ingreso DROP COLUMN proveedor_id;
DROP TABLE proveedor;
```

Sin `CASCADE`, sin reescribir V1–V8 y sin `IF EXISTS` que oculte divergencias. Esta propuesta asume el esquema versionado V8 exacto; todavía no está validada por ejecución. El resguardo debe preceder a V9 y no puede depender de un comentario SQL. No colocar el script en `db/migration` hasta resolver la decisión, porque Flyway puede ejecutarlo automáticamente al arrancar.

- Base existente en V8: validar checksums, respaldar/verificar y ejecutar V9 junto con backend/frontend nuevos. Se eliminan datos de proveedores y vínculos del esquema operativo; no se borran ingresos ni movimientos. La versión anterior del backend deja de ser compatible con V9.
- Instalación vacía: ejecutar V1–V8 intactas y luego V9; la tabla se crea históricamente y se elimina antes de inicializar entidades/datos demo. Actualizar demo es imprescindible para que no intente recrear proveedores vía JPA.
- No se necesita compatibilidad de endpoints de proveedores: deben desaparecer. Sí se conservan IDs y contratos restantes de inventario/catálogo/operación. Desplegar frontend y backend coordinadamente e invalidar cachés/PWA antiguas para evitar clientes con contratos anteriores.

## Validación y trabajo pendiente

Ejecutado en esta etapa: fetch de main, comprobación de HEAD e historial PR #33/B5-04, búsqueda completa de referencias versionadas, lectura del esquema V1–V8 y de código/pruebas afectadas, inspección visual del DER. Se adjuntan inventario con líneas y SHA-256 de V1–V8 para controlar su inmutabilidad. No hubo ejecución de pruebas de comportamiento, compilación, SQL, migraciones ni E2E: la implementación está deliberadamente pendiente.

Plan requerido al autorizar Fase 3:

| Verificación | Alcance esperado |
| --- | --- |
| Backend | Java 25, compilación y `mvnw.cmd verify`; Surefire unitarias y Failsafe integraciones con PostgreSQL 17 efímero/Testcontainers. |
| Migración vacía | V1–V9, validate Flyway/JPA, objetos de proveedores ausentes. |
| Migración V8 poblada | Fixtures de proveedores activos/inactivos/no referenciados; bebida con/sin habitual; ingresos con/sin proveedor, remitos y movimientos. Comparar todas las filas preservadas. |
| Ingreso e inventario inicial | Guardar sin proveedor; validaciones, recientes, saldos, correcciones de inventario y trazabilidad de usuario/fecha/remito. |
| Catálogo | Alta/edición/baja/reactivación, búsqueda/códigos y carga independiente de proveedores. |
| B5-04 | Preparación manual, idempotencia, concurrencia y saldo. |
| B4-03/B4-04/B4-05 | Devolución, saldo negativo de #33, cierre sin doble descuento y consulta histórica. |
| Frontend | `npm test`, `npm run lint`, `npm run build` (incluye `tsc -b`); E2E de Sprint 3, operación, preparación y consulta. |
| Ausencia | No mapping/OpenAPI, pantalla, selector ni llamadas API de proveedores; enlace antiguo del catálogo resuelve a pestaña válida. |
| Integridad | V1–V8 byte a byte sin cambios, `git diff --check`, revisión final del diff y de documentación/DER. |

Riesgos pendientes: cantidad real de datos desconocida, política de custodia no definida, dependencias externas no verificadas, resguardo aún no producido/restaurado, V9 aún no ensayada, despliegue incompatible con clientes/backend anteriores y ausencia de evidencia funcional hasta Fases 3–4.

## Archivos y estado final de esta entrega

Solo se agregaron este informe y dos anexos de auditoría: `docs/eliminacion-proveedores-referencias.txt` y `docs/eliminacion-proveedores-migraciones-sha256.txt`. Ningún archivo de aplicación fue eliminado o modificado. Diff funcional contra `origin/main`: vacío. Los archivos nuevos quedan sin commit; no hubo push ni creación de PR. El diff documental se entrega también fuera del worktree para facilitar revisión.

**Autorización requerida:** confirmar si se aprueba conservar el maestro y las dos relaciones en un archivo externo verificable y luego eliminarlos del esquema operativo. Si se exige otra conservación, definirla antes de implementar V9. Esta pausa responde a la restricción expresa de la solicitud ante pérdida irreversible de información histórica.
