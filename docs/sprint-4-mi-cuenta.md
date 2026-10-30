# Sprint 4 — Mi cuenta (0-03, UI-03)

Cada persona administra su propia contraseña desde `/mi-cuenta`, elige el tema de la pantalla y cierra sesión.
Rama original: `nehuen/0-cuenta-y-preferencias`; base: `main`.

## Decisiones y alcance

1. **Cambio obligatorio en el primer ingreso.** El alta ya marcaba `debe_cambiar_contrasena` y avisaba «la va a cambiar al ingresar». Se cumple esa promesa (decisión de Nehuen, 25/10/2026) tanto en React como en el backend.
2. **Identidad de la sesión.** El usuario sale del subject del JWT validado. No hay id de otra cuenta en la ruta ni en el pedido de cambio. Todos los perfiles pueden cambiar su propia contraseña.
3. **Validaciones y transacción.** Se exige la contraseña actual, una nueva de 8 a 72 caracteres y hasta 72 bytes UTF-8 (capacidad existente de BCrypt), distinta según el codificador. No se guarda texto plano. La lectura del usuario toma bloqueo pesimista dentro de la transacción: dos cambios concurrentes no validan la misma contraseña anterior y se sobrescriben.
4. **Renovación y recuperación.** Tras guardar se llama a `renovarSesion()`, que lee el usuario persistido. Si falla o devuelve todavía la marca anterior, el formulario distingue «contraseña guardada» de «sesión actualizada», ofrece reintentar solo la renovación o cerrar/reingresar con la nueva. Un 401 usa el diálogo existente. Un ingreso o refresh posterior correcto retira la advertencia. No se agrega revocación de todas las sesiones.
5. **Flujo inicial.** Mientras la sesión requiere el cambio, Layout redirige a Mi cuenta y no monta menús comerciales ni Campana; no se instalan sus efectos o polling. Conserva formulario, recuperación, preferencias y cierre de sesión. Al renovar con marca=false restaura el marco normal sin desmontar el formulario.
6. **Tema por dispositivo.** Claro, oscuro y sistema conservan `vastio.tema` en localStorage, conforme a `ds/tema.ts`. El token de acceso sigue solamente en memoria; el refresco, en cookie HttpOnly.

## JWT, consultas y contrato de estado

El access token firmado incluye `debeCambiarContrasena` como booleano. La cadena de seguridad autoriza operaciones protegidas únicamente con `false`, además de los permisos por rol ya existentes.
La comprobación de esa marca no consulta PostgreSQL por solicitud. Login y refresh siguen leyendo al usuario y emiten la marca vigente.
La duración de acceso sigue configurada por `vastio.seguridad.minutos-acceso` (15 minutos por defecto); el refresco usa MINUTOS_EXPIRACION_SESION (60 minutos iniciales), renovado por actividad. Se mantienen firma HS256, issuer, tipo de token y validación temporal existentes.

| Token o situación | Política |
|---|---|
| Access token con booleano true | Solo cambio propio, además de auth/login, refresh y logout públicos; operaciones comerciales: 403. |
| Access token con booleano false | Se aplican los permisos normales por roles. |
| Token inicial anterior a un cambio correcto | Conserva la restricción hasta renovar: un cambio de contraseña no modifica un JWT ya firmado. |
| Refresh posterior al cambio | Lee marca=false y emite un access token que habilita la navegación y las operaciones permitidas. |
| Access token antiguo sin claim | 401, nunca se interpreta como false. El cliente intenta refresh; un refresco anterior válido sigue siendo aceptado. Si falla, se puede reingresar. |
| Claim de cambio de tipo incorrecto o subject ausente/no numérico/no positivo/fuera de Long | 401 controlado antes del controller. |
| Principal diferente de Jwt | No obtiene permisos por pertenecer a un rol ni por usar un perfil de pruebas. |

**Invariante del modelo vigente.** La marca se establece en el alta y se elimina al elegir la contraseña propia. No existe un endpoint ni servicio de restablecimiento que la reactive después; modificar nombre/perfiles/contacto y reactivar conservan su valor. El bootstrap y los datos de demostración mantienen sus exenciones previas.

**Límite explícito.** Un claim no detecta que alguien cambie false→true directamente por SQL o agregue un restablecimiento futuro. Esa operación está fuera del flujo actual y no debe desplegarse suponiendo que el claim revoca tokens existentes: antes se debe introducir invalidación/revalidación de tokens, una revisión de credenciales consultada de forma segura, o mantener una consulta de estado por solicitud. Sin ello habría acceso hasta el vencimiento del token previo (más la tolerancia temporal del decoder). Para un cambio operativo externo urgente se deben invalidar los tokens previos; cambiar únicamente la columna no es un procedimiento admitido.
No se agrega hoy caché ni infraestructura de revocación para una funcionalidad de restablecimiento que todavía no existe.

## Equivalencia de contraseña

Una igualdad textual permite rechazar una repetición sin otra verificación BCrypt. Para cadenas distintas se mantiene `PasswordEncoder.matches(nueva, hash)`.
No se reemplaza por una comparación textual general: BCrypt puede reconocer como actual una cadena con sufijo después de los primeros 72 bytes; la nueva podría ser la misma credencial, aunque las cadenas difieran.
Se prueban los casos ASCII y Unicode. BCrypt no normaliza NFC/NFD; esas representaciones distintas conservan la semántica del codificador.
No se supone un ahorro fijo de milisegundos. El atajo reduce de dos a una las verificaciones cuando ambas cadenas son idénticas; los cambios normales y los alias conservan la verificación adicional.

## API

`POST /api/v1/cuenta/contrasena` con `{contrasenaActual, contrasenaNueva}` → 204. No devuelve un access token nuevo: se renueva la sesión por la API existente.

| Caso | Respuesta |
|---|---|
| Falta un dato o la nueva no tiene 8 a 72 caracteres | 400 con errores por campo |
| La actual no es correcta | 422 CONTRASENA_ACTUAL_INCORRECTA |
| La nueva equivale a la actual | 422 CONTRASENA_REPETIDA |
| La nueva supera 72 bytes UTF-8 | 422 CONTRASENA_DEMASIADO_LARGA |
| Usuario dado de baja | 422 USUARIO_INACTIVO |
| Sin token | 401 SIN_SESION |
| JWT inválido, vencido o access token antiguo sin claim | 401 SESION_VENCIDA |

## Código y pruebas

Backend: CuentaController, AutenticacionService, Usuario.cambiarContrasena, TokenService, SeguridadConfig y UsuarioActual.
Frontend: features/cuenta, api/cuenta, Layout y los componentes existentes de sesión.

CuentaIT conserva cada perfil, ingreso con la nueva y rechazo de la vieja, actual incorrecta, repetida, longitudes, Unicode, campos faltantes, usuario inactivo, identidad propia y cambio obligatorio. Verifica que el token inicial siga restringido y que el refresh permita operar.
CuentaConcurrenciaIT comprueba la espera real y el rechazo del segundo cambio.
CuentaJwtIT cubre claims, migración de tokens, sujetos inválidos, principales de prueba y roles múltiples. Su medición en la sonda cuenta solo sentencias preparadas por Hibernate; no mide JdbcTemplate ni JDBC directo.
ContrasenaEquivalenciaTest cubre el atajo y las equivalencias del codificador.
PaginaMiCuenta.test.tsx y e2e/cuenta.spec.ts verifican el flujo, ausencia de menús/polling, errores de renovación, reingreso, navegación restaurada, tema y salida.

Para revisar: `./mvnw verify` desde backend (Docker para los IT, VASTIO_TEST_PYTHON para el resguardo externo); `npm run lint`, `npm test` y `npm run build` desde frontend. Las pruebas de navegador de cuenta usan API simulada y proyectos de escritorio/teléfono. El DER no cambia.
