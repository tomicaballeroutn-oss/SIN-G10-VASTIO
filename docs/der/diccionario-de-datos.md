# Vastio — Modelo de datos (DER v2) y diccionario de datos

Motor: PostgreSQL. Convenciones: nombres en `snake_case` y en español; claves primarias sustitutas (`bigint` generado para tablas transaccionales, `smallint` para catálogos); importes en `numeric(14,2)` expresados en pesos argentinos (el único es el de la seña: el sistema no maneja precios ni costos); fechas y horas en `timestamptz`; cantidades de bebida en `numeric(10,2)` expresadas siempre en **botellas** (unidad base). Los estados y tipos fijos se modelan como `varchar` con restricción `CHECK`, no como tablas, porque el comportamiento del sistema depende de ellos y no son configurables.

## 1. Cambios respecto del DER v1

| Cambio | Motivo |
|---|---|
| Se agregó `evento.estado` | Sin el estado en el evento no se puede garantizar la exclusividad: el índice único parcial necesita saber qué eventos están activos. |
| Se eliminó la tabla `ESTADO` | Los estados son parte del comportamiento (la máquina de estados vive en el código); se reemplazan por un `CHECK`. El historial queda en `cambio_estado_evento`. |
| Se eliminó la tabla `PRERESERVA` | La pre-reserva es un estado del Evento, no una entidad. La tabla duplicaba información. |
| Se eliminó `unidadcomerciable.bloqueada` y se agregaron `bloqueo` y `bloqueo_unidad` | El bloqueo necesita motivo, rango, responsable y desbloqueo (UI-21), y no es un estado del Evento. |
| Se reemplazó `usuario.rol_id` por `usuario_rol` | Un usuario puede tener más de un perfil. |
| Se eliminó la tabla `SESION` | Con JWT la sesión no se guarda en la base; la expiración la resuelve el propio token. Solo haría falta una tabla si se decide revocar tokens. |
| Se eliminó la tabla `TIPOMOVIMIENTO` | Los tipos de movimiento son fijos y el cálculo del consumo depende de ellos: pasan a `CHECK`. |
| Se agregaron seña, firmante, firma de contrato, cancelación, hora de inicio y observaciones en `evento` | Datos que ya piden UI-08, UI-11, UI-12, UI-17 y la vista de cocina. |
| Se agregaron `contacto_evento`, `documento_evento`, `modificacion_evento`, `reprogramacion` | Contactos múltiples, contrato digitalizado del legajo, registro de cambios de «Modificar evento» y conservación de la fecha original. |
| Se agregaron `notificacion` y `notificacion_destinatario` | La épica A8 no tenía soporte en el modelo. |
| Se agregaron `parametro` y `motivo` | Horizonte de cocina, expiración de sesión y listas de motivos configurables. |
| Se agregaron `proveedor`, `codigo_barra`, `ingreso`, `consumo_evento` | Carga inicial de proveedores, varios códigos por artículo, cabecera del ingreso con remito y costo congelado al cierre. |
| `bebida`: precio de referencia, stock mínimo, baja lógica, unidades por bulto | El `factorConversion` depende del artículo (un cajón de cerveza no trae lo mismo que uno de vino), por eso pasa a `bebida`. |
| `ubicacion`: tipo y salón | Depósito de transición configurable y barra de cada salón. (V6 quitó los sectores y la ubicación padre.) |
| `movimiento_stock`: fecha y hora, motivo, movimiento corregido, ingreso | Auditoría (RNF-SEG-03) y asiento de ajuste que referencia al original (UI-38). |
| `asistencia_segmento`: cantidad prevista además de la real; PK simple | UI-19 pide la prevista por segmento. La PK triple del v1 era redundante. |
| `turno`: horario | Sin horario el sistema no puede pasar el evento a En curso ni a Realizado. |
| `orden_preparacion`: estado, origen y destino | Tiene su propia máquina de estados (§6.1). |
| `salon.color_hex` → `salon.codigo`; `turno.codigo` | El color de cada salón ya está en los tokens del sistema de diseño, con variante clara y oscura; un solo hex en la base lo duplicaba y rompía el tema oscuro. El código estable (avril, club, santa-barbara; mediodia, noche) es el que usa el frontend. |
| Sin precios ni costos (V5, Sprint 3) | El sistema maneja cantidades; cada área calcula los costos con sus remitos. Se quitaron `bebida.precio_referencia` y el precio y el costo de `consumo_evento`. La seña es la única excepción. |
| Ubicaciones: sin sectores; abastecimiento de la barra y máximo de barras por salón (V6, Sprint 3) | Las divisiones internas del depósito no se modelan. Cada barra se abastece del depósito madre o de una transición, con retiro directo del depósito madre como contingencia. Una barra por salón; Avril, hasta dos. |
| Solo bebida con alcohol; «Cajón» pasa a «Caja» (V5) | Alcance de esta etapa. La caja es solo la forma de mostrar y cargar: todo se guarda en botellas. |
| Correcciones menores | `cliente.nombre_cli` estaba marcado como FK; typos `CATEGORIASERIVICIO` y `tipoMovimiemto_id`; `unidadComerciable` → `unidad_comercializable`. |

## 2. Reglas de integridad transversales

- **Exclusividad de la unidad.** `CREATE UNIQUE INDEX ux_evento_unidad_activa ON evento(unidad_id) WHERE estado NOT IN ('LIBERADA','CANCELADO');` La restricción la garantiza la base, incluso con dos vendedoras confirmando a la vez.
- **Evento contra bloqueo.** Antes de crear un evento, reprogramarlo o bloquear, el backend toma `SELECT … FOR UPDATE` sobre la fila de `unidad_comercializable`, y dentro de esa transacción verifica que no haya evento activo ni bloqueo activo.
- **Fecha operativa.** `unidad_comercializable.fecha` es la fecha de inicio de la jornada. Los movimientos de la madrugada no se imputan por fecha sino por `evento_id`, así que un retiro a las 02:00 cae en el evento correcto.
- **Reprogramación.** No cambia el estado: actualiza `evento.unidad_id` y deja una fila en `reprogramacion` con la unidad anterior. Esto reemplaza al estado «Reprogramado» de la máquina de estados v1.
- **Tablas de solo inserción.** `cambio_estado_evento`, `modificacion_evento`, `reprogramacion` y `movimiento_stock` no admiten UPDATE ni DELETE: un trigger (`fn_solo_insercion`, V3) los rechaza sin importar quién los intente, porque la aplicación es dueña de las tablas y revocarle permisos no alcanza. Las correcciones de stock se registran como asiento de ajuste con `movimiento_corregido_id`.
- **Saldo teórico negativo permitido.** `stock_ubicacion.cantidad` no tiene `CHECK ≥ 0`: un retiro mayor al saldo se acepta y genera alerta a compras y administración (operación a ciegas).
- **Consumo congelado.** Al cerrar el evento se escribe `consumo_evento`; no se recalcula después.
- **Consumo por asistente.** Se divide lo consumido por la suma de `asistencia_segmento.cantidad_real` si el tipo de evento usa segmentos; si no, por `evento.cantidad_invitados`.
- **Obligatoriedad por estado** (validada en backend y con `CHECK`): desde SENADO, `importe_sena`, `fecha_sena` y `firmante_dni`; desde CONTRATADO, `fecha_firma_contrato` y un `documento_evento` de tipo CONTRATO; para CONFIRMADO, `planner_id`, `invitados_definitivos = true` y servicios cargados en las categorías con `requerida_para_confirmar`; en CANCELADO, `motivo_cancelacion_id`.
- **Importe de la seña** (`importe_sena`, el único dato económico): se filtra por perfil en el backend según RNF-SEG-04; el modelo no lo separa en otra tabla.

## 3. Diccionario de datos

Columnas: **Clave** (PK, FK, PK/FK, UQ) · **Nulo** (SÍ admite nulo).

### 3.1 Seguridad

#### USUARIO

Persona que opera el sistema. La baja es lógica: se conserva el historial de lo que registró.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `usuario_id` | bigint | PK | NO | Identificador. Generado (identity). |
| `nombre_completo` | varchar(120) |  | NO | Nombre y apellido. |
| `nombre_usuario` | varchar(50) | UQ | NO | Usuario de inicio de sesión. Único. |
| `hash_contrasena` | varchar(100) |  | NO | Hash BCrypt. Nunca la contraseña en claro (RNF-SEG-01). |
| `email` | varchar(120) |  | SÍ | Correo de contacto. |
| `telefono` | varchar(30) |  | SÍ | Teléfono de contacto. |
| `debe_cambiar_contrasena` | boolean |  | NO | true tras alta o restablecimiento. Default true. |
| `activo` | boolean |  | NO | false = dado de baja. Default true. |
| `fecha_alta` | timestamptz |  | NO | Momento del alta. Default now(). |
| `fecha_baja` | timestamptz |  | SÍ | Momento de la baja lógica. |

#### ROL

Perfil de usuario. Los códigos son fijos porque la autorización del backend los referencia.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `rol_id` | smallint | PK | NO | Identificador. |
| `codigo` | varchar(20) | UQ | NO | DIRECCION · COORDINACION · ADMINISTRACION · VENDEDORA · PLANNER · COMPRAS · BARRA · COCINA. |
| `nombre` | varchar(50) |  | NO | Nombre visible del perfil. |

#### USUARIO_ROL

Asignación de perfiles. Un usuario puede tener más de un perfil (p. ej. vendedora que cubre como planner).

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `usuario_id` | bigint | PK/FK | NO | → usuario. |
| `rol_id` | smallint | PK/FK | NO | → rol. |
| `fecha_asignacion` | timestamptz |  | NO | Default now(). |

### 3.2 Configuración

#### PARAMETRO

Parámetros escalares configurables sin intervención técnica.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `clave` | varchar(60) | PK | NO | Ej.: HORIZONTE_COCINA_DIAS, MINUTOS_EXPIRACION_SESION, MINUTOS_AVISO_EXPIRACION. |
| `valor` | varchar(255) |  | NO | Valor en texto; el backend lo interpreta según la clave. |
| `descripcion` | varchar(255) |  | SÍ | Para qué sirve el parámetro. |

#### MOTIVO

Lista configurable de motivos para cancelaciones, reprogramaciones, bloqueos y ajustes de stock.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `motivo_id` | smallint | PK | NO | Identificador. |
| `ambito` | varchar(15) |  | NO | CANCELACION · REPROGRAMACION · BLOQUEO · AJUSTE. |
| `nombre` | varchar(80) |  | NO | Texto del motivo. Único dentro del ámbito. |
| `activo` | boolean |  | NO | Default true. |

#### SALON

Salón del complejo.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `salon_id` | smallint | PK | NO | Identificador. |
| `nombre` | varchar(40) | UQ | NO | Avril · Club de Campo · Santa Bárbara. |
| `capacidad` | integer |  | SÍ | Capacidad máxima de invitados. > 0. |
| `codigo` | varchar(20) | UQ | NO | avril · club · santa-barbara. El color de la agenda (RNF-DIS-01) sale de los tokens del sistema de diseño según este código, con variante para tema claro y oscuro. |
| `activo` | boolean |  | NO | Default true. |
| `maximo_barras` | smallint |  | NO | Barras activas que puede tener: 1; Avril, 2 (V6). ≥ 1. Sin pantalla. |

#### TURNO

Turno comercializable. Define el horario que usa el sistema para pasar el evento a En curso y a Realizado.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `turno_id` | smallint | PK | NO | Identificador. |
| `codigo` | varchar(20) | UQ | NO | mediodia · noche. Código estable que usa el frontend. |
| `nombre` | varchar(20) | UQ | NO | Mediodía · Noche. |
| `hora_inicio` | time |  | NO | Ej.: 20:00. |
| `hora_fin` | time |  | NO | Ej.: 06:00. |
| `cruza_medianoche` | boolean |  | NO | true si hora_fin corresponde al día siguiente (turno noche). |

#### TIPO_EVENTO

Tipo de evento.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `tipo_evento_id` | smallint | PK | NO | Identificador. |
| `nombre` | varchar(40) | UQ | NO | Casamiento · Quince · Corporativo · Egresados · Cumpleaños. |
| `usa_segmentos` | boolean |  | NO | true si exige asistencia por segmento (egresados). |
| `activo` | boolean |  | NO | Default true. |

#### TIPO_SEGMENTO_ASISTENCIA

Segmento de asistencia.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `tipo_segmento_id` | smallint | PK | NO | Identificador. |
| `nombre` | varchar(40) | UQ | NO | Comensales cena · Entradas anticipadas · Venta en puerta. |
| `activo` | boolean |  | NO | Default true. |

#### CATEGORIA_SERVICIO

Categoría configurable de servicio contratado (decisión de Meli: sin desplegables fijos).

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `categoria_id` | smallint | PK | NO | Identificador. |
| `nombre` | varchar(40) | UQ | NO | Recepción · Plato principal · Postre · After · Bodega · Tipo de barra · Técnica · Mobiliario · Menús especiales · Extras. |
| `orden` | smallint |  | NO | Orden de presentación en la ficha. |
| `visible_en_cocina` | boolean |  | NO | true si se muestra en la vista de cocina. |
| `requerida_para_confirmar` | boolean |  | NO | true para Plato principal y Tipo de barra (condición de Confirmar evento). |
| `avisa_a_compras` | boolean |  | NO | true para Bodega y Tipo de barra (V4): un cambio en un evento confirmado le llega a Compras. Default false. |
| `activo` | boolean |  | NO | Default true. |

### 3.3 Módulo A — Agenda de eventos

#### UNIDAD_COMERCIALIZABLE

Salón × fecha × turno. Se crea a demanda la primera vez que se la ocupa o bloquea. Es además el ancla de bloqueo pesimista (SELECT … FOR UPDATE) que evita que un evento y un bloqueo tomen la misma unidad en paralelo.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `unidad_id` | bigint | PK | NO | Identificador. |
| `salon_id` | smallint | FK | NO | → salon. |
| `fecha` | date |  | NO | Fecha de inicio de la jornada. Un retiro a las 02:00 pertenece a la jornada del día anterior; por eso los movimientos se imputan por evento_id, nunca por fecha. |
| `turno_id` | smallint | FK | NO | → turno. |

*Restricciones:* UNIQUE (salon_id, fecha, turno_id).

#### BLOQUEO

Bloqueo de unidades por motivos no comerciales. No es un estado del Evento.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `bloqueo_id` | bigint | PK | NO | Identificador. |
| `motivo_id` | smallint | FK | NO | → motivo (ámbito BLOQUEO): mantenimiento, feriado, evento propio. |
| `detalle` | varchar(255) |  | SÍ | Texto libre. |
| `usuario_id` | bigint | FK | NO | → usuario. Quién bloqueó. |
| `fecha_registro` | timestamptz |  | NO | Default now(). |
| `activo` | boolean |  | NO | false al desbloquear. |
| `usuario_desbloqueo_id` | bigint | FK | SÍ | → usuario. Quién desbloqueó. |
| `fecha_desbloqueo` | timestamptz |  | SÍ | Momento del desbloqueo. |

#### BLOQUEO_UNIDAD

Unidades alcanzadas por un bloqueo. El rango de fechas y turnos de la pantalla se expande en una fila por unidad.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `bloqueo_id` | bigint | PK/FK | NO | → bloqueo. |
| `unidad_id` | bigint | PK/FK | NO | → unidad_comercializable. |

#### CLIENTE

Persona u organización que contrata el evento.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `cliente_id` | bigint | PK | NO | Identificador. |
| `nombre` | varchar(120) |  | NO | Nombre o razón social. |
| `documento` | varchar(13) |  | SÍ | DNI o CUIT, sin guiones. |
| `telefono` | varchar(30) |  | SÍ | Teléfono principal. |
| `email` | varchar(120) |  | SÍ | Correo principal. |

*Índices (V3):* `ix_cliente_documento` (documento) e `ix_cliente_nombre` (lower(nombre)), para buscarlo al pre-reservar.

#### EVENTO

Entidad central que vincula ambos módulos.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `evento_id` | bigint | PK | NO | Identificador. |
| `codigo` | varchar(14) | UQ | NO | Código legible para imputar movimientos. Formato EV-AAAA-NNNNN. |
| `unidad_id` | bigint | FK | NO | → unidad_comercializable. Cambia al reprogramar. |
| `cliente_id` | bigint | FK | NO | → cliente. |
| `tipo_evento_id` | smallint | FK | NO | → tipo_evento. |
| `vendedora_id` | bigint | FK | NO | → usuario. Vendedora titular. |
| `planner_id` | bigint | FK | SÍ | → usuario. Obligatoria para pasar a Confirmado. |
| `estado` | varchar(15) |  | NO | PRE_RESERVA · SENADO · CONTRATADO · CONFIRMADO · EN_CURSO · REALIZADO · CERRADO · LIBERADA · CANCELADO. |
| `nombre` | varchar(120) |  | NO | Denominación del evento. Ej.: «15 de Sofía». |
| `cantidad_invitados` | integer |  | SÍ | Invitados previstos/definitivos. ≥ 0. |
| `invitados_definitivos` | boolean |  | NO | true cuando la cantidad quedó cerrada (condición de Confirmar). |
| `hora_inicio` | time |  | SÍ | Hora de inicio (vista de cocina). Default: hora del turno. |
| `observaciones_internas` | text |  | SÍ | Notas no visibles para cocina (p. ej. «cliente complicado»). |
| `importe_sena` | numeric(14,2) |  | SÍ | Importe de la seña en ARS. Obligatorio desde SENADO. Dato económico (RNF-SEG-04). |
| `fecha_sena` | date |  | SÍ | Fecha de pago de la seña. |
| `firmante_nombre` | varchar(120) |  | SÍ | Quien firma el contrato. |
| `firmante_dni` | varchar(10) |  | SÍ | DNI del firmante. Obligatorio desde SENADO. |
| `firmante_contacto` | varchar(120) |  | SÍ | Teléfono o correo del firmante. |
| `fecha_firma_contrato` | date |  | SÍ | Obligatoria desde CONTRATADO. |
| `motivo_cancelacion_id` | smallint | FK | SÍ | → motivo (ámbito CANCELACION). Obligatorio si estado = CANCELADO. |
| `detalle_cancelacion` | varchar(255) |  | SÍ | Texto libre de la cancelación. |
| `fecha_creacion` | timestamptz |  | NO | Default now(). |
| `version` | integer |  | NO | Bloqueo optimista (@Version). Detecta ediciones simultáneas. |

*Restricciones:* Exclusividad: CREATE UNIQUE INDEX ux_evento_unidad_activa ON evento(unidad_id) WHERE estado NOT IN ('LIBERADA','CANCELADO'). CHECK sobre estado con los nueve valores.

#### CONTACTO_EVENTO

Contactos del evento además del cliente (padre, madre, organizador).

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `contacto_id` | bigint | PK | NO | Identificador. |
| `evento_id` | bigint | FK | NO | → evento. |
| `nombre` | varchar(120) |  | NO | Nombre del contacto. |
| `vinculo` | varchar(40) |  | SÍ | Ej.: madre, padre, organizador. |
| `telefono` | varchar(30) |  | SÍ |  |
| `email` | varchar(120) |  | SÍ |  |

#### SERVICIO_CONTRATADO

Servicio acordado con el cliente, con descripción libre dentro de una categoría configurable.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `servicio_id` | bigint | PK | NO | Identificador. |
| `evento_id` | bigint | FK | NO | → evento. |
| `categoria_id` | smallint | FK | NO | → categoria_servicio. |
| `descripcion` | text |  | NO | Detalle libre. Ej.: «Malbec Luigi Bosca y Chardonnay Alamos». |
| `usuario_id` | bigint | FK | NO | → usuario. Último en modificarlo. |
| `fecha_modificacion` | timestamptz |  | NO | Default now(). |

*Restricciones (V4):* `ux_servicio_contratado_evento_categoria` UNIQUE (evento_id, categoria_id): un texto por categoría y evento.

#### ASISTENCIA_SEGMENTO

Asistencia prevista y real por segmento (egresados).

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `asistencia_id` | bigint | PK | NO | Identificador. |
| `evento_id` | bigint | FK | NO | → evento. |
| `tipo_segmento_id` | smallint | FK | NO | → tipo_segmento_asistencia. |
| `cantidad_prevista` | integer |  | SÍ | ≥ 0. |
| `cantidad_real` | integer |  | SÍ | ≥ 0. Se carga al cierre. |

*Restricciones:* UNIQUE (evento_id, tipo_segmento_id).

#### DOCUMENTO_EVENTO

Archivos del legajo. El archivo vive en el almacenamiento; la base guarda la referencia.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `documento_id` | bigint | PK | NO | Identificador. |
| `evento_id` | bigint | FK | NO | → evento. |
| `tipo` | varchar(15) |  | NO | CONTRATO · PRESUPUESTO · OTRO. |
| `nombre_archivo` | varchar(150) |  | NO | Nombre original. |
| `ruta` | varchar(255) |  | NO | Ubicación en el almacenamiento de archivos. |
| `mime_type` | varchar(60) |  | NO | application/pdf · image/jpeg · image/png. |
| `tamano_bytes` | integer |  | NO | > 0. |
| `usuario_id` | bigint | FK | NO | → usuario. Quién lo subió. |
| `fecha_carga` | timestamptz |  | NO | Default now(). |

#### CAMBIO_ESTADO_EVENTO

Historial inalterable de transiciones (RNF-SEG-03). Solo inserción.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `cambio_id` | bigint | PK | NO | Identificador. |
| `evento_id` | bigint | FK | NO | → evento. |
| `estado_anterior` | varchar(15) |  | SÍ | Null en la creación. |
| `estado_nuevo` | varchar(15) |  | NO | Mismos valores que evento.estado. |
| `usuario_id` | bigint | FK | SÍ | → usuario. Null = transición automática del sistema. |
| `fecha_hora` | timestamptz |  | NO | Default now(). |
| `observacion` | varchar(255) |  | SÍ |  |

#### MODIFICACION_EVENTO

Registro de cambios de datos del evento (Modificar evento). Solo inserción.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `modificacion_id` | bigint | PK | NO | Identificador. |
| `evento_id` | bigint | FK | NO | → evento. |
| `campo` | varchar(60) |  | NO | Nombre del dato modificado. Ej.: cantidad_invitados. |
| `valor_anterior` | text |  | SÍ |  |
| `valor_nuevo` | text |  | SÍ |  |
| `usuario_id` | bigint | FK | NO | → usuario. |
| `fecha_hora` | timestamptz |  | NO | Default now(). |

#### REPROGRAMACION

Cambio de unidad de un evento. Conserva la programación original. No cambia el estado.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `reprogramacion_id` | bigint | PK | NO | Identificador. |
| `evento_id` | bigint | FK | NO | → evento. |
| `unidad_anterior_id` | bigint | FK | NO | → unidad_comercializable. |
| `unidad_nueva_id` | bigint | FK | NO | → unidad_comercializable. Distinta de la anterior. |
| `motivo_id` | smallint | FK | NO | → motivo (ámbito REPROGRAMACION). |
| `detalle` | varchar(255) |  | SÍ |  |
| `usuario_id` | bigint | FK | NO | → usuario. |
| `fecha_hora` | timestamptz |  | NO | Default now(). |

#### NOTIFICACION

Aviso generado por un cambio de evento.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `notificacion_id` | bigint | PK | NO | Identificador. |
| `evento_id` | bigint | FK | SÍ | → evento. El aviso lleva a su ficha. |
| `tipo` | varchar(30) |  | NO | EVENTO_NUEVO · SENA · CONTRATO · CONFIRMACION · MODIFICACION · REPROGRAMACION · CANCELACION · PLANNER_ASIGNADA · ALERTA_STOCK. |
| `mensaje` | varchar(255) |  | NO | Texto visible. |
| `usuario_origen_id` | bigint | FK | SÍ | → usuario. Null si la generó el sistema. |
| `fecha_hora` | timestamptz |  | NO | Default now(). |

#### NOTIFICACION_DESTINATARIO

Destinatarios de cada aviso. El ruteo por perfil y dato modificado se resuelve al crear el aviso.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `notificacion_id` | bigint | PK/FK | NO | → notificacion. |
| `usuario_id` | bigint | PK/FK | NO | → usuario. |
| `leida` | boolean |  | NO | Default false. |
| `fecha_lectura` | timestamptz |  | SÍ |  |

#### NUMERADOR_EVENTO

Último número de evento de cada año, para el código EV-AAAA-NNNNN (V3). Se incrementa con `INSERT … ON CONFLICT … RETURNING`: la fila del año queda bloqueada hasta el final de la transacción, así dos pre-reservas simultáneas no comparten número y, si una se deshace, su número no se pierde.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `anio` | smallint | PK | NO | Año de creación del evento. |
| `ultimo` | integer |  | NO | Último número entregado. Entre 1 y 99999. |

### 3.4 Módulo B — Control de existencias de bebida

#### TIPO_BEBIDA

Clasificación de bebidas.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `tipo_bebida_id` | smallint | PK | NO | Identificador. |
| `nombre` | varchar(40) | UQ | NO | Vino · Espumante · Destilado · Aperitivo · Cerveza. Solo bebida con alcohol (V5). |

#### UNIDAD_MANIPULACION

Forma en que se mueve la mercadería.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `unidad_manipulacion_id` | smallint | PK | NO | Identificador. |
| `nombre` | varchar(20) | UQ | NO | Caja · Pack · Botella. Solo para mostrar y cargar: las cantidades se guardan en botellas. |

#### PROVEEDOR

Proveedor de bebida (carga inicial de datos maestros). Las bebidas que provee son las que lo tienen como `bebida.proveedor_habitual_id`. Razón social sin repetir entre los activos y CUIT con dígito verificador válido, validados en el backend.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `proveedor_id` | bigint | PK | NO | Identificador. |
| `razon_social` | varchar(120) |  | NO |  |
| `cuit` | varchar(11) | UQ | SÍ | Sin guiones. |
| `telefono` | varchar(30) |  | SÍ |  |
| `email` | varchar(120) |  | SÍ |  |
| `activo` | boolean |  | NO | Default true. |

#### BEBIDA

Artículo del catálogo. Toda cantidad del sistema se expresa en su unidad base (botella).

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `bebida_id` | bigint | PK | NO | Identificador. |
| `nombre` | varchar(100) |  | NO | Ej.: Fernet Branca. |
| `presentacion` | varchar(40) |  | NO | Ej.: 750 ml. |
| `tipo_bebida_id` | smallint | FK | NO | → tipo_bebida. |
| `unidad_manipulacion_id` | smallint | FK | NO | → unidad_manipulacion. Unidad en que se mueve (caja). |
| `unidades_por_bulto` | smallint |  | NO | Botellas por unidad de manipulación. > 0; 1 si la unidad es Botella. Reemplaza al factorConversion. |
| `stock_minimo` | numeric(10,2) |  | SÍ | Umbral de stock bajo del depósito madre, en botellas (UI-27). |
| `proveedor_habitual_id` | bigint | FK | SÍ | → proveedor. |
| `activo` | boolean |  | NO | false = baja lógica. Default true (V5). |
| `fecha_baja` | timestamptz |  | SÍ |  |

*Restricciones (V5):* `ux_bebida_nombre_presentacion_activa` UNIQUE (lower(nombre), lower(presentacion)) WHERE activo.

#### CODIGO_BARRA

Códigos de barras de un artículo. Un artículo puede tener varios (botella y caja). El código identifica el artículo; la cantidad se tipea.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `codigo` | varchar(20) | PK | NO | Código leído (EAN-13, DUN-14, etc.). |
| `bebida_id` | bigint | FK | NO | → bebida. |
| `unidades` | smallint |  | NO | Botellas que representa una lectura: 1 para botella, 6 o 12 para caja. |

*Restricciones (V5):* `ck_codigo_barra_formato` CHECK (codigo ~ '^[0-9]{8,14}$'); índice `ix_codigo_barra_bebida` (bebida_id).

#### UBICACION

Depósito madre, depósito de transición o barra. Configurable: el circuito funciona con o sin transición. Las divisiones internas del depósito no se modelan (V6).

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `ubicacion_id` | smallint | PK | NO | Identificador. |
| `nombre` | varchar(60) | UQ | NO | Ej.: Depósito principal · Transición Club de Campo · Barra Avril. Único sin distinguir mayúsculas (V6). |
| `tipo` | varchar(12) |  | NO | DEPOSITO (depósito madre) · TRANSICION · BARRA. No cambia después del alta. |
| `salon_id` | smallint | FK | SÍ | → salon. Obligatoria si tipo = BARRA; opcional en TRANSICION; nula en DEPOSITO. |
| `ubicacion_abastecimiento_id` | smallint | FK | SÍ | → ubicacion. Solo barras, y obligatoria: el depósito madre o un depósito de transición (V6). |
| `permite_retiro_directo` | boolean |  | NO | Solo barras abastecidas por una transición: habilita el retiro directo del depósito madre como contingencia. Default false (V6). |
| `activo` | boolean |  | NO | Default true. |

*Restricciones (V6):* `ux_ubicacion_deposito_madre` (un solo DEPOSITO activo); `ux_ubicacion_nombre` UNIQUE (lower(nombre)); `ck_ubicacion_abastecimiento` (solo las barras tienen origen y retiro directo). Máximo de barras activas por salón según `salon.maximo_barras`, validado en el backend.

#### STOCK_UBICACION

Saldo teórico por ubicación y artículo. Se actualiza en la misma transacción que cada movimiento; puede ser negativo (señal de un registro faltante, genera alerta).

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `ubicacion_id` | smallint | PK/FK | NO | → ubicacion. |
| `bebida_id` | bigint | PK/FK | NO | → bebida. |
| `cantidad` | numeric(10,2) |  | NO | En botellas. Sin CHECK ≥ 0 a propósito. |
| `fecha_actualizacion` | timestamptz |  | NO |  |

#### INGRESO

Cabecera de un ingreso de mercadería al depósito.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `ingreso_id` | bigint | PK | NO | Identificador. |
| `ubicacion_destino_id` | smallint | FK | NO | → ubicacion. |
| `proveedor_id` | bigint | FK | SÍ | → proveedor. |
| `numero_remito` | varchar(30) |  | SÍ | Remito contra el que se contó. |
| `fecha_ingreso` | date |  | NO | Default hoy. |
| `es_inventario_inicial` | boolean |  | NO | true para la carga de puesta en marcha. |
| `usuario_id` | bigint | FK | NO | → usuario. |
| `fecha_registro` | timestamptz |  | NO | Default now(). |

#### ORDEN_PREPARACION

Propuesta de bebida a retirar para un evento confirmado.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `orden_id` | bigint | PK | NO | Identificador. |
| `evento_id` | bigint | FK | NO | → evento. |
| `estado` | varchar(12) |  | NO | SUGERIDA · MODIFICADA · CONFIRMADA · DESCARTADA · CERRADA. |
| `ubicacion_origen_id` | smallint | FK | NO | → ubicacion (depósito o transición). |
| `ubicacion_destino_id` | smallint | FK | NO | → ubicacion (barra). |
| `responsable_id` | bigint | FK | NO | → usuario. Encargado de compras. |
| `fecha_creacion` | timestamptz |  | NO | Default now(). |
| `fecha_confirmacion` | timestamptz |  | SÍ |  |

*Restricciones:* CREATE UNIQUE INDEX ux_orden_evento_vigente ON orden_preparacion(evento_id) WHERE estado <> 'DESCARTADA'.

#### DETALLE_ORDEN_PREPARACION

Renglón de la orden.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `detalle_id` | bigint | PK | NO | Identificador. |
| `orden_id` | bigint | FK | NO | → orden_preparacion. |
| `bebida_id` | bigint | FK | NO | → bebida. |
| `cantidad_sugerida` | numeric(10,2) |  | NO | Calculada por el sistema, en botellas. |
| `cantidad_a_retirar` | numeric(10,2) |  | NO | Ajustable por el responsable. ≥ 0. |

*Restricciones:* UNIQUE (orden_id, bebida_id).

#### ENTREGA

Entrega abierta de un evento hacia una barra. Agrupa la salida inicial y los retiros adicionales.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `entrega_id` | bigint | PK | NO | Identificador. |
| `evento_id` | bigint | FK | NO | → evento. |
| `ubicacion_destino_id` | smallint | FK | NO | → ubicacion (barra). |
| `orden_id` | bigint | FK | SÍ | → orden_preparacion, si existía. |
| `estado` | varchar(10) |  | NO | ABIERTA · CERRADA. |
| `usuario_apertura_id` | bigint | FK | NO | → usuario. |
| `fecha_apertura` | timestamptz |  | NO | Default now(). |
| `usuario_cierre_id` | bigint | FK | SÍ | → usuario. |
| `fecha_cierre` | timestamptz |  | SÍ |  |

*Restricciones:* UNIQUE (evento_id, ubicacion_destino_id).

#### MOVIMIENTO_STOCK

Asiento inalterable de mercadería (RNF-SEG-03). Solo inserción: las correcciones son nuevos asientos de ajuste.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `movimiento_id` | bigint | PK | NO | Identificador. |
| `tipo` | varchar(18) |  | NO | INGRESO · INVENTARIO_INICIAL · SALIDA_BARRA · RETIRO_ADICIONAL · DEVOLUCION · REMANENTE · AJUSTE · MERMA. |
| `bebida_id` | bigint | FK | NO | → bebida. |
| `cantidad` | numeric(10,2) |  | NO | En botellas. > 0; el sentido lo dan origen y destino. |
| `ubicacion_origen_id` | smallint | FK | SÍ | → ubicacion. Null en ingresos. |
| `ubicacion_destino_id` | smallint | FK | SÍ | → ubicacion. Null en mermas. |
| `evento_id` | bigint | FK | SÍ | → evento. Obligatorio en salidas, retiros, devoluciones y remanentes. |
| `entrega_id` | bigint | FK | SÍ | → entrega. |
| `ingreso_id` | bigint | FK | SÍ | → ingreso. |
| `movimiento_corregido_id` | bigint | FK | SÍ | → movimiento_stock. Asiento que corrige este ajuste. |
| `motivo_id` | smallint | FK | SÍ | → motivo (ámbito AJUSTE). Obligatorio en AJUSTE y MERMA. |
| `observacion` | varchar(255) |  | SÍ |  |
| `usuario_id` | bigint | FK | NO | → usuario. Responsable del movimiento. |
| `fecha_hora` | timestamptz |  | NO | Default now(). |

*Restricciones:* CHECK (ubicacion_origen_id IS NOT NULL OR ubicacion_destino_id IS NOT NULL). Sin UPDATE ni DELETE: se revocan esos permisos al usuario de la aplicación sobre esta tabla.

#### CONSUMO_EVENTO

Resultado del cierre por artículo, congelado. Solo cantidades, en botellas: el precio y el costo se quitaron en V5.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `evento_id` | bigint | PK/FK | NO | → evento. |
| `bebida_id` | bigint | PK/FK | NO | → bebida. |
| `cantidad_entregada` | numeric(10,2) |  | NO | Salidas + retiros adicionales + remanente inicial. |
| `cantidad_devuelta` | numeric(10,2) |  | NO | Devuelto al depósito. |
| `cantidad_remanente` | numeric(10,2) |  | NO | Queda en barra para el evento siguiente. |
| `cantidad_consumida` | numeric(10,2) |  | NO | Entregada − devuelta − remanente. |

#### PROPORCION_CONSUMO

Estimación de consumo por asistente según tipo de evento. Alimenta la orden sugerida mientras no hay histórico propio.

| Campo | Tipo | Clave | Nulo | Descripción / valores posibles |
|---|---|---|---|---|
| `proporcion_id` | bigint | PK | NO | Identificador. |
| `tipo_evento_id` | smallint | FK | NO | → tipo_evento. |
| `bebida_id` | bigint | FK | NO | → bebida. |
| `botellas_por_asistente` | numeric(8,4) |  | NO | ≥ 0. |

*Restricciones:* UNIQUE (tipo_evento_id, bebida_id).

## 4. Puntos que el modelo deja preparados pero dependen de una decisión

- **Señado y Contratado como estados distintos** (a confirmar con Meli). Si se fusionan, se quita un valor del `CHECK`; ninguna tabla cambia.
- **Quién registra la seña** (vendedora o administración). No afecta al modelo, solo a la autorización.
- **Botellas abiertas al cierre.** Las cantidades admiten decimales para registrar, por ejemplo, 0,5 botella si se decide contarlas; si se cuentan solo cerradas, se usan enteros.
- **Almacenamiento de archivos del legajo.** Resuelto en el Sprint 2: disco local, en el volumen `legajo` del ambiente de prueba (`vastio.legajo.directorio`); `documento_evento.ruta` es relativa a ese directorio (`<evento_id>/<uuid>.<extensión>`). Pasar a un almacenamiento de objetos no cambia el modelo.
- **Revocación de sesiones.** Si se requiere cerrar sesiones de forma remota (por ejemplo al dar de baja un usuario), se agrega una tabla de tokens de refresco.