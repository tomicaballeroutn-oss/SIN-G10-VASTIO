# Máquina de estados

## Evento

Estados (valor en base → nombre en pantalla):

| Código | Pantalla | Etapa | Significado |
|---|---|---|---|
| `PRE_RESERVA` | Pre-reserva | Comercial | La vendedora aparta la fecha mientras el cliente decide. No vence sola. |
| `SENADO` | Señado | Comercial | Seña paga y turno de firma sacado. |
| `CONTRATADO` | Contratado | Comercial | Contrato firmado y digitalizado en el legajo. |
| `CONFIRMADO` | Confirmado | Comercial | Datos operativos cerrados: planner, invitados definitivos, menú y tipo de barra. |
| `EN_CURSO` | En curso | Operativa | Día del evento. Habilita salidas a barra. |
| `REALIZADO` | Realizado | Operativa | Jornada terminada, pendiente de conciliar. |
| `CERRADO` | Cerrado | Final | Consumo y costo fijados. Solo lectura; se corrige con asientos de ajuste. |
| `LIBERADA` | Liberada | Final | Pre-reserva que no prosperó. **No es una cancelación.** |
| `CANCELADO` | Cancelado | Final | Evento dado de baja con motivo. |

Estados activos (ocupan la unidad): todos salvo `LIBERADA` y `CANCELADO`.

### Transiciones

| Desde → Hasta | Caso de uso | Quién | Condición | Efecto |
|---|---|---|---|---|
| — → PRE_RESERVA | Registrar pre-reserva | Vendedora, Coordinación | Unidad sin evento activo ni bloqueo | Crea la unidad si no existe. Notifica a administración y coordinación. |
| PRE_RESERVA → LIBERADA | Liberar pre-reserva | Vendedora titular, Coordinación | — | Libera la unidad. |
| PRE_RESERVA → SENADO | Registrar seña | Vendedora titular, Coordinación | Importe, fecha de seña y DNI del firmante | Notifica a administración y coordinación. |
| SENADO → CONTRATADO | Registrar firma de contrato | Coordinación | Contrato adjunto (documento tipo CONTRATO) y fecha de firma | Habilita asignar planner. |
| CONTRATADO → CONFIRMADO | Confirmar evento | Planner asignada, Coordinación | Planner asignada, invitados definitivos, servicios en las categorías requeridas | Habilita la orden de preparación. Notifica a compras y cocina. |
| CONFIRMADO → EN_CURSO | Automático | Sistema | Llega la hora de inicio del turno | Bloquea la edición comercial. Habilita salidas a barra. |
| EN_CURSO → REALIZADO | Automático | Sistema | Termina el turno (hora_fin, día siguiente si cruza medianoche) | — |
| REALIZADO → CERRADO | Registrar cierre de evento | Encargada de barra, Compras | Entregas cerradas; si el tipo usa segmentos, asistencia real cargada | Escribe `consumo_evento` con el precio vigente. |
| SENADO / CONTRATADO / CONFIRMADO → CANCELADO | Cancelar evento | Coordinación | Motivo obligatorio | Libera la unidad. Descarta la orden de preparación. Notifica a todas las áreas. |

### Operaciones que no cambian el estado

| Operación | Estados permitidos | Quién | Registro |
|---|---|---|---|
| Modificar evento | PRE_RESERVA a CONFIRMADO | Vendedora titular, Planner asignada, Coordinación | `modificacion_evento` |
| Reprogramar | PRE_RESERVA*, SENADO, CONTRATADO, CONFIRMADO | Coordinación, Vendedora titular | `reprogramacion` (conserva la unidad original). *En pre-reserva es simplemente modificar la fecha. |
| Asignar planner | CONTRATADO, CONFIRMADO | Coordinación | `modificacion_evento` + notificación a la planner |
| Registrar servicios contratados | PRE_RESERVA a CONFIRMADO | Vendedora titular, Planner asignada, Coordinación | `servicio_contratado` |
| Registrar salida / retiro / devolución | EN_CURSO (devolución también en REALIZADO) | Encargada de barra, Compras | `movimiento_stock` |

> Corrección respecto de la Definición del Producto v1: **«Reprogramado» no es un estado**. Reprogramar le pasa al evento; si fuera un estado, se perdería si estaba Señado, Contratado o Confirmado.

Todo cambio de estado escribe una fila en `cambio_estado_evento` (usuario null = sistema).

## Orden de preparación

`SUGERIDA` → (el responsable ajusta cantidades) → `MODIFICADA` → `CONFIRMADA` → `CERRADA` (al cerrar el evento).
`SUGERIDA` / `MODIFICADA` / `CONFIRMADA` → `DESCARTADA` si el evento se cancela o se reprograma fuera del horizonte; una orden descartada permite generar otra.
