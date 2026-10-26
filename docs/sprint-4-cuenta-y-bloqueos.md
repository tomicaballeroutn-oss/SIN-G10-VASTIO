# Sprint 4 — Mi cuenta y bloqueo de unidades (tareas de Nehuen)

Dos historias, cada una en su rama y su PR.

| Orden | Historia | Rama | Base |
|---|---|---|---|
| 1 | 0-03 · Iniciar sesión / Mi cuenta (UI-03) | `nehuen/0-cuenta-y-preferencias` | `main` |
| 2 | A1-02 · Registrar bloqueo de unidades (UI-21) | `nehuen/A1-bloqueo-unidades` | `main` |

## 1 · 0-03 · Mi cuenta

**Qué hace.** Cada persona cambia su propia contraseña desde `/mi-cuenta`, elige el tema de la pantalla y cierra sesión. Si entró con la contraseña inicial que le dieron en el alta (`debe_cambiar_contrasena = true`), el marco de la app no la deja salir de Mi cuenta hasta que elija una propia.

**Decisiones.**

1. **Cambio obligatorio en el primer ingreso.** El alta de usuarios ya marcaba `debe_cambiar_contrasena` y avisaba «la va a cambiar al ingresar», pero ninguna pantalla lo usaba. Se decidió cumplir lo que el sistema promete (decisión de Nehuen, 25/10/2026).
2. **Se aplica en el frontend.** `Layout` redirige a `/mi-cuenta` mientras la sesión tenga la marca. El backend no bloquea los demás endpoints con la marca puesta: el JWT no lleva esa marca y la autorización de cada operación no cambia. Es un recordatorio de uso, no una barrera de seguridad.
3. **Sin cambios en la sesión.** Al cambiar la contraseña se llama a `renovarSesion()`, que vuelve a leer al usuario en la base y trae la marca en `false`. Las sesiones ya abiertas, en este y otros dispositivos, siguen valiendo hasta que venzan (no hay tabla de sesiones).
4. **Mismas reglas que el alta.** La nueva tiene de 8 a 72 caracteres (el límite de BCrypt) y tiene que ser distinta de la actual.
5. **El usuario sale del token.** El endpoint no recibe un id: nadie puede cambiar la contraseña de otra persona.
6. **Tema.** Sigue en `localStorage` (`vastio.tema`), como ya estaba decidido en `ds/tema.ts`.

**API.** `POST /api/v1/cuenta/contrasena` con `{contrasenaActual, contrasenaNueva}` → 204. Cualquier perfil autenticado.

| Caso | Respuesta |
|---|---|
| Falta un dato o la nueva no tiene 8 a 72 caracteres | 400 con `errores` por campo |
| La actual no es correcta | 422 `CONTRASENA_ACTUAL_INCORRECTA` |
| La nueva es igual a la actual | 422 `CONTRASENA_REPETIDA` |
| Usuario dado de baja | 422 `USUARIO_INACTIVO` |
| Sin token | 401 `SIN_SESION` |

**Código.** `CuentaController` (nuevo, aparte de `AutenticacionController`, que es público), `AutenticacionService.cambiarContrasena`, `Usuario.cambiarContrasena`; en el frontend `features/cuenta/` (`PaginaMiCuenta`, `FormularioContrasena`) y `api/cuenta.ts`.

**Pruebas.** `CuentaIT` (cada perfil cambia la suya, ingreso con la nueva y no con la vieja, contraseña actual incorrecta, nueva repetida o fuera de largo, campos faltantes, usuario dado de baja, 401). `PaginaMiCuenta.test.tsx` (redirección forzada, validaciones, errores del servidor, navegación liberada).

## 2 · A1-02 · Registrar bloqueo de unidades

**Qué hace.** `/bloqueos` saca de la venta salones × fechas × turnos por mantenimiento, feriado o evento propio. Muestra los bloqueos activos (salón, rango de fechas, turnos y motivo) y permite desbloquear con confirmación. En la agenda, las unidades bloqueadas ya se veían: esto agrega cómo se crean y se levantan.

**Quién.** Administración, Coordinación y Dirección (`Permisos.BLOQUEAR_UNIDADES`), como dice la matriz de `perfiles-y-permisos.md`. El resto lo ve en la agenda y recibe 403 si intenta la operación.

**Decisiones.**

1. **Un bloqueo es un rectángulo.** Varios salones × un rango de fechas × uno o los dos turnos, con un motivo (catálogo, ámbito BLOQUEO) y un detalle opcional. Se expande en una fila por unidad en `bloqueo_unidad`. Sin «Hasta» se bloquea un solo día.
2. **Sin migración.** Las tablas `bloqueo` y `bloqueo_unidad` ya existían desde V1.
3. **Tope de 180 días por bloqueo** (`BloqueoService.MAXIMO_DIAS`). Más que eso casi seguro es un error en el año; se pueden hacer dos.
4. **No se bloquea el pasado.** La fecha inicial no puede ser anterior a hoy (zona de Córdoba), igual que la pre-reserva.
5. **No se pisa nada.** Si alguna unidad del rango tiene un evento activo, se rechaza todo con 409 `UNIDAD_OCUPADA` y el mensaje nombra el evento («Club de Campo el 05/04/2033 (turno noche) ya tiene el evento EV-2033-00012 (…)»). Si alguna ya está bloqueada, 409 `YA_BLOQUEADA`. No hay bloqueo parcial: o entra todo o no entra nada. Un evento liberado o cancelado no cuenta.
6. **Exclusividad.** Es el mismo mecanismo de `TomaDeUnidad`: se crean las unidades que falten y se toma `SELECT … FOR UPDATE` de cada una **antes** de mirar eventos y bloqueos. Las toma siempre en el orden salón, fecha, turno, así que dos bloqueos que se pisan no se traban entre sí. Una pre-reserva que llega en el medio espera el mismo `FOR UPDATE` y, cuando entra, ve el bloqueo (409 `UNIDAD_BLOQUEADA`, que ya existía).
7. **Salón dado de baja.** No se bloquea (422 `SALON_DADO_DE_BAJA`). El motivo tiene que ser de ámbito BLOQUEO y estar activo (422 `MOTIVO_INVALIDO`).
8. **Desbloquear no borra.** El bloqueo queda inactivo con `usuario_desbloqueo_id` y `fecha_desbloqueo`; las filas de `bloqueo_unidad` se conservan como historia. Se toma `FOR UPDATE` sobre el bloqueo: si dos personas desbloquean a la vez, la segunda recibe 409 `BLOQUEO_YA_DESBLOQUEADO`.
9. **La lista** muestra los bloqueos activos que todavía tienen alguna fecha por delante, el más próximo primero.
10. **Un bloqueo no se edita.** Para cambiar algo se desbloquea y se registra de nuevo.

**API.** Todo bajo `/api/v1/bloqueos`, con `@PreAuthorize(Permisos.BLOQUEAR_UNIDADES)`.

| Operación | Respuesta |
|---|---|
| `GET /bloqueos` | 200, lista de bloqueos activos |
| `POST /bloqueos` con `{salonIds, desde, hasta?, turnoIds, motivoId, detalle?}` | 201 con el bloqueo; 400 validación; 404 salón, turno o motivo inexistente; 409 `UNIDAD_OCUPADA` / `YA_BLOQUEADA`; 422 `FECHA_PASADA`, `RANGO_INVALIDO`, `RANGO_MUY_LARGO`, `MOTIVO_INVALIDO`, `SALON_DADO_DE_BAJA` |
| `POST /bloqueos/{id}/desbloqueo` | 204; 404 `BLOQUEO_INEXISTENTE`; 409 `BLOQUEO_YA_DESBLOQUEADO` |

**Código.** Backend: `BloqueoService`, `BloqueoController` y los métodos nuevos de `Bloqueo` y `BloqueoRepository`. Frontend: `features/bloqueos/PaginaBloqueos`, `api/bloqueos.ts`, y la pantalla enchufada en `rutas.tsx` (el ítem «Bloqueos» del menú ya existía).

**Pruebas.** `BloqueoIT` (permisos por perfil, alta de un día y de un rango, conflictos con evento y con otro bloqueo, reglas, lista, desbloqueo, pre-reserva contra una unidad bloqueada y desbloqueada). `BloqueoConcurrenciaIT` (un bloqueo contra una pre-reserva y dos bloqueos que se pisan, simultáneos: entra uno solo y nadie se traba). `PaginaBloqueos.test.tsx` (lista, alta, validaciones, errores del servidor, desbloqueo con confirmación, perfil sin permiso).

## Para revisar el PR

- `.\mvnw verify` desde `backend/` (hace falta Docker para los `*IT`) y `npm run lint && npm test && npm run build` desde `frontend/`.
- El DER no cambia, pero A1-02 empieza a escribir en `bloqueo` y `bloqueo_unidad`.
- La agenda no se tocó: lee los bloqueos como siempre.
