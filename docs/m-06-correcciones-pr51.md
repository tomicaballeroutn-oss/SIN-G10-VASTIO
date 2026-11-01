# M-06 — Correcciones de la revisión del PR #51

## 1. Estado inicial verificado (29/10/2026)

- Revisión [Albert, CHANGES_REQUESTED](https://github.com/tomicaballeroutn-oss/SIN-G10-VASTIO/pull/51#pullrequestreview-5690180622).
- PR #51 abierto, Draft, HEAD `e8aef42c49c96b74d48e0f1cc3d69fb462cbf870`.
- Main `f94fd73b9867a181e0dca106acd13b071b426327`, con PR #50 fusionado.
- PR #49 abierto, HEAD `d136cc6c0df4153bf235d77ad18dbfce0b41291a`, seguridad/cuenta, pendiente de revisión.
- PR #52 abierto, HEAD `0f5eb45da4051f47ffe4eed8c5d913d074f126d4`, colores de notificaciones.
- Worktree independiente `vastio-pr51-correcciones`, rama `codex/pr51-correcciones-revision`, creada sobre el HEAD original. Merge normal de main: `08d2c3d`; se preservan los commits originales. No se modifica la rama original.

## 2. Hallazgos demostrados

Los listeners originales de `SeguimientoCalendar` eran síncronos, transaccionales y llamaban `EntityManager.flush()`: una excepción local de seguimiento podía marcar rollback en la transacción comercial. Los GET recalculaban publicaciones y avisos. La configuración desactivada no impedía avisos/pending accionables. La retirada confirmada conservaba `retiro_solicitado`. Los errores remotos diferentes se devolvían como 503. La sesión del advisory lock ocupaba el pool comercial durante la red. Los límites HTTP de Calendar no alcanzaban al transporte OAuth. Un conflicto ETag carecía de aceptación operativa explícita.

## 3. Solución transaccional y recuperación

La migración V15 agrega una cola coalescente con revisión/procesada. Triggers mínimos capturan inserciones y cambios en estado, unidad, tipo u hora particular de Evento, y nombres/horarios de los catálogos usados por el contenido. La captura es atómica con el origen: un rollback comercial revierte su trabajo en cola. No se ejecuta proyección, notificación Calendar ni red dentro de esa captura.

AFTER_COMMIT acelera el procesamiento en una transacción REQUIRES_NEW limitada a diez segundos. Un barrido cada cinco segundos procesa lotes de cincuenta sin llamadas Google y recupera las señales perdidas por caída del proceso. Confirma la revisión solo tras completar la proyección. Fallos dejan la revisión pendiente, guardan código sanitizado y backoff hasta cinco minutos. Si la base no permite guardar el error, la revisión continúa sin confirmar y se registra el incidente sin mensajes privados. Orden de bloqueo: Evento, cola, publicación.

La base sigue siendo necesaria para confirmar cualquier cambio comercial. Un fallo de la propia captura SQL no se oculta: hacerlo permitiría perder trabajo durable. La garantía de aislamiento se refiere a excepciones de proyección Calendar y a fallos externos, no a confirmar ventas sin una base disponible.

## 4. Integración desactivada

Por defecto false, sin credenciales ni red. Se preservan identidades, última publicación, auditoría y cambios acumulados en cola. No se proyectan nuevos avisos, los anteriores se resuelven al arrancar/barrer y la API devuelve listado vacío, pendiente NINGUNA y capacidades falsas. La UI no ofrece acciones remotas ni muestra un falso éxito vigente. No se borran registros para ocultar pendientes.

La activación requiere reinicio. Se encolan elegibles preexistentes y publicaciones previas; se recalculan exportaciones, actualizaciones y retiradas. La clave persistida evita duplicar avisos activos. Una retirada confirmada sigue retirada. La lectura informa si la proyección local está pendiente o falló.

## 5. Observaciones adicionales

| Observación | Resolución |
|---|---|
| retiro_solicitado tras retirada | Se limpia al confirmar y V15 corrige filas RETIRADO anteriores conservando identidad e historial. |
| CONFLICTO_REMOTO | Verificación remota manual, aceptación auditada de ETag vigente y otra acción manual para escribir; If-Match continúa obligatorio. No se importan campos de Google. |
| 503 genérico | Códigos HTTP diferenciados 409/410/422/429/502/503/504 y mensajes accionables sanitizados. Google 401 no vence el login Vastio. |
| Pool retenido durante red | Pool de locks separado, perezoso, máximo dos sesiones, espera un segundo. Pool comercial liberado durante Google. |
| Token OAuth | Transporte con límites de conexión/lectura configurados, sin redirecciones, logging ni reintentos de autenticación. No es deadline global de DNS. |
| GET escribe | GET estrictamente de lectura, sin FOR UPDATE, proyección ni recuperación de abandonos. El procesador local recupera EN_PROCESO abandonado. |
| Error de credenciales | Log únicamente de categoría de excepción, sin ruta/contenido/mensaje del proveedor; respuesta sanitizada. Rotación exige reiniciar cliente cacheado. |
| POST/PUT equivalentes | Documentado y conservado: intenciones de UI distintas sobre el mismo coordinador idempotente y su recuperación. Separarlo duplicaría lógica crítica. |
| PR #52 | Incompatibilidad TypeScript demostrada y parche de integración descrito abajo. No se importan cambios de un PR pendiente. |

## 6. Compatibilidad

**#50:** main se incorporó mediante merge normal sin conflictos. Los bloqueos de unidades y sus pruebas forman parte de la validación completa. No se cambia la exclusividad comercial.

**#49:** no se integra ni modifica su seguridad pendiente. `UsuarioActual(long, Set<String>)` y `accesoTotal()` se conservan; Calendar utiliza esas interfaces y las restricciones del controlador. Su política de cambio obligatorio de contraseña debe aplicarse también a Calendar cuando se integre. `git merge-tree` sobre el HEAD de corrección actualizado con main y el HEAD de #49 no detectó conflictos textuales; el árbol combinado no se ejecutó y no se da por validado sin sus pruebas de seguridad/rutas/layout.

**#52:** `git merge-tree` tampoco detectó conflictos textuales, pero el mapa exhaustivo `COLOR: Record<TipoNotificacion,string>` no contiene CALENDAR_PENDIENTE. Se compiló el mapa exacto del HEAD de #52 contra el contrato real M-06: TypeScript TS2741, falta CALENDAR_PENDIENTE. El chequeo temporal se eliminó después. El icono M-06 `calendar` y el tipo del backend/API ya están presentes. Al integrar #52 se debe añadir al mapa:

```ts
CALENDAR_PENDIENTE: 'calendar-pendiente',
```

y una regla `.avisos__aviso--calendar-pendiente` con los tokens existentes, manteniendo texto e icono y probando avisos leídos/no leídos. Este ajuste pertenece al merge con #52; no se elimina la exhaustividad ni se usa un cast para silenciar el error. No se modifica la rama ajena.

## 7. Migraciones y persistencia

Main termina en V13; el PR original agrega V14. Se mantienen ambas sin alteraciones y se agrega V15. La base actual tiene 45 tablas. Se verifican instalación desde cero, V13→V14 y V14→V15, validación Flyway repetible, datos de Evento e historial sin cambios y captura durable. La auditoría sigue siendo de solo inserción.

## 8. Seguridad y privacidad

Solo Dirección/Coordinación pueden consultar u operar Calendar, incluidas verificación/aceptación. Las llamadas Google siempre requieren una petición manual autorizada. Los trabajadores locales no consultan Google. Se mantienen identidad por instalación/evento, recuperación de resultados inciertos, comprobación del destino e If-Match. La aceptación de conflicto nunca importa datos comerciales ni regenera IDs.

El contenido sigue siendo código, salón, tipo, turno y hora particular opcional; intervalo completo y zona America/Argentina/Cordoba, incluyendo medianoche. No se publican cliente, contactos, seña, contratos ni observaciones comerciales. Ninguna prueba usa credenciales reales.

## 9. Pruebas

Resultados finales de suites completas y CI se consignan en el PR de correcciones. La tanda focal inicial ejecutó 7 unitarias, 60 integraciones PostgreSQL/Testcontainers y 17 pruebas de UI, todas sin fallos ni omisiones. Incluye fallos inyectados en pre-reserva, seña, cancelación, reprogramación y catálogo, persistencia comercial y recuperación; transiciones de configuración; deduplicación; rollback de captura; lecturas sin mutación; conflictos/ETag; identidades y resultados inciertos; concurrencia; permisos; límites OAuth y migraciones.

Estos resultados son simulación de Google: no acreditan aceptación externa. Las suites completas deben finalizar antes de declarar la entrega lista para revisión.

## 10. Aceptación Google real — PENDIENTE

No se dispone de credenciales/autorización para ejecutar Google real y no se configura ningún calendario del cliente. Procedimiento del responsable:

1. Crear o elegir un proyecto exclusivo de prueba y habilitar Google Calendar API.
2. Crear cuenta de servicio de prueba. Guardar su clave fuera del repositorio/imagen, restringida al backend; no adjuntarla a evidencias.
3. Desde una cuenta humana de prueba, crear calendario aislado y compartirlo con la cuenta de servicio con permiso de modificar eventos. IAM del proyecto por sí solo no da acceso al calendario. Verificar restricciones Workspace; no usar `primary` ni crear el calendario con la cuenta de servicio.
4. Configurar ID explícito, instalación única de prueba, ruta de credenciales y habilitado=true. Reiniciar. Registrar fecha, responsable, versión desplegada y destino sin secretos.
5. Crear un evento ficticio elegible y exportar como Dirección/Coordinación. Guardar evidencia sanitizada e identidad remota; repetir y verificar que no duplica.
6. Comparar código/título, descripción, salón/ubicación, fecha operativa, zona Córdoba y duración completa del turno. Usar 31/12 nocturno y comprobar fin 01/01.
7. Cargar en Vastio datos privados sintéticos (cliente, documento/contacto, seña, observación interna) y comprobar que ninguno aparece en Google ni en solicitudes visibles del navegador.
8. Modificar hora particular y reprogramar salón/fecha/turno; comprobar pendiente y actualizar manualmente. La identidad remota debe conservarse.
9. Editar externamente un campo, intentar actualizar y comprobar conflicto sin sobrescritura. Revisar, aceptar ETag, volver a editar externamente y comprobar rechazo del ETag viejo. Revisar de nuevo y ejecutar actualización explícita. Confirmar que Evento no importó campos externos.
10. Cancelar en Vastio, comprobar pendiente de retirada y ausencia de borrado automático; retirar, repetir retirada y comprobar historial y retiro_solicitado=false.
11. Quitar escritura o cambiar temporalmente a reader y probar una acción. Verificar código sanitizado, sesión Vastio vigente y datos comerciales conservados. Restaurar permiso y reconciliar manualmente. Probar un perfil no autorizado y comprobar 403 sin llamadas Google.
12. Desactivar y reiniciar; cambiar un evento y comprobar ausencia de avisos/acciones/red. Activar y reiniciar: pendiente recuperado y aviso sin duplicados. Retirar las entradas ficticias restantes mediante el flujo autorizado.
13. Limpiar calendario/proyecto de prueba según su política, retirar ACL, revocar/eliminar clave, eliminar archivo del host y desactivar integración. Reiniciar para descartar el cliente cacheado. Registrar evidencias y cualquier fallo sin tokens, claves ni datos privados.

Fuentes oficiales consultadas: [crear eventos](https://developers.google.com/workspace/calendar/api/guides/create-events), [scopes](https://developers.google.com/workspace/calendar/api/auth), [compartir](https://developers.google.com/workspace/calendar/api/concepts/sharing), [ETags](https://developers.google.com/workspace/calendar/api/guides/version-resources), [credenciales Java](https://docs.cloud.google.com/java/docs/reference/google-auth-library/latest/com.google.auth.oauth2.ServiceAccountCredentials).

## 11. Entrega

Rama independiente `codex/pr51-correcciones-revision`, commits originales conservados, sin force-push. PR de correcciones Draft dirigido a `codex/m-06-google-calendar`. El PR original #51 sigue separado; no se aprueba, marca listo ni fusiona automáticamente.

## 12. Veredicto

El veredicto final depende de las suites completas y CI. Aun con todas verdes, la aceptación Google real sigue pendiente y se requiere revisión humana de Albert y coordinación del merge con #52. No constituye autorización de merge.
