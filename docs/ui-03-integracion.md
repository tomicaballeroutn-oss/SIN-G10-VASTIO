# UI-03 — Preparación de integración y revisión

## Base y alcance

Esta continuación parte de #49 d136cc6, que ya incorporó #53 y #55. Main vigente al iniciar: f94fd73 (A1-02 integrado). Referencias de compatibilidad: #51 6185c08 y #59 88bc0db. No se incorporan esas ramas a UI-03 ni se modifican sus PR.

Las correcciones funcionales ya integradas están documentadas en [Mi cuenta](sprint-4-mi-cuenta.md). Esta continuación agrega soporte de pruebas para @WithMockUser y regresiones; no cambia la seguridad de producción.

## Matriz de observaciones de Albert

| Observación | Implementación y evidencia |
|---|---|
| Consulta por petición | SeguridadConfig autoriza por claim booleano firmado. CuentaJwtIT mide cero sentencias SQL en tres solicitudes a la sonda; no afirma cero consultas en endpoints de negocio ni saturación demostrada del pool. |
| Subject y pruebas Spring | Decoder y UsuarioActual validan identidad; subjects ausentes/no numéricos/no positivos/desbordados reciben 401. Principales no JWT siguen recibiendo 403. Personas.jwtDeUsuarioSimulado() permite reutilizar @WithMockUser explícitamente, emitiendo un JWT real con identidad y marca del fixture y autoridades simuladas. Solo existe en src/test. |
| BCrypt | Igualdad textual rechaza la repetición sin segundo matches. Para textos distintos se mantiene la comparación con el encoder: la equivalencia BCrypt por truncamiento a 72 bytes impide eliminarla con seguridad. Casos ASCII, Unicode, NFC/NFD y concurrencia están cubiertos; no se promete una reducción fija de milisegundos. |
| Navegación y notificaciones | Layout no monta Nav ni Campana en primer ingreso; evita el rebote por interacción y sus intervalos. Recuperación, cierre y tema permanecen disponibles. Las pruebas comprueban cero polling, restauración después de refresh y reintento tras fallo. |
| Documento mixto | sprint-4-mi-cuenta.md contiene exclusivamente 0-03. A1-02 integrado en main conserva sus archivos propios; esta rama no los modifica. |

## Uso de @WithMockUser

Crear el fixture persistido cuyo nombre coincide con username de la anotación y usar `.with(personas.jwtDeUsuarioSimulado())` en la solicitud MockMvc. Ejemplo:

```java
@WithMockUser(username="prueba.direccion", roles="DIRECCION")
// @BeforeEach: personas.de(RolCodigo.DIRECCION)
mvc.perform(get("/api/v1/_sonda/direccion").with(personas.jwtDeUsuarioSimulado()));
```

No es un filtro alternativo ni una excepción por perfil. El encabezado firmado atraviesa el decoder real. El adaptador conserva los roles simulados, no agrega los roles del fixture y obtiene la obligación de cambio desde la entidad. Las regresiones verifican 200 con Dirección, 403 al simular Vendedora y restricción inicial con posibilidad de cambiar contraseña. El uso de @WithMockUser sin el adaptador continúa denegado. Los tests de firma, expiración y claims siguen usando tokens reales; se agrega adulteración del payload sin nueva firma con rechazo 401.

## Política JWT y límites

Un access sin claim o con tipo incorrecto recibe 401 y debe renovarse; un refresh anterior válido consulta el estado vigente. Un access true permanece restringido aun después del cambio hasta renovar. Cambiar una contraseña no altera el JWT firmado ni revoca globalmente sesiones.

En el modelo vigente no existe flujo que pase la obligación de false a true después del primer cambio. Un futuro reset o SQL externo requerirá invalidación/revalidación antes de habilitar esa operación; cambiar solo la columna no es un mecanismo admitido para revocar tokens false emitidos. Se conserva esta limitación explícita para revisión humana.

## Verificación e integración recomendada

- Ejecutar `mvn -B verify` con Docker y VASTIO_TEST_PYTHON: incluye unitarios, integraciones de JWT/autenticación/cuenta, bloqueo concurrente y resguardo en PostgreSQL descartable.
- Frontend: `npm ci`, `npm run lint`, `npm run build` y `npm test -- --maxWorkers=1`.
- Playwright: `E2E_URL=<vite local> E2E_BROWSER_CHANNEL=msedge npm run e2e -- cuenta.spec.ts --workers=1`, escritorio/teléfono con API simulada. No sustituye aceptación contra un despliegue real.
- Compatibilidad: generar árboles combinados descartables mediante `git merge-tree --write-tree` con main, #51 y #59; compilar backend con `mvn -B -DskipTests package` y TypeScript con `tsc -b`. Esto comprueba composición y compilación, no equivale a ejecutar las suites completas combinadas. El lockfile frontend coincide en esas referencias.
- No hacer merge de #51/#59 para preparar UI-03. Si sus HEAD cambian, repetir compatibilidad.

Después de revisar este borrador, el equipo puede integrar sus regresiones en la rama de #49, actualizar esa rama con main mediante el procedimiento del equipo, ejecutar nuevamente CI sobre el HEAD resultante y solicitar nueva revisión de Albert. La revisión CHANGES_REQUESTED permanece vigente hasta su decisión; los checks correctos no equivalen a aprobación. No se autoriza merge automático, cierre, modificación de tablero ni cambio de ramas ajenas.