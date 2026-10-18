# Sprint 3 (segunda parte) — La noche del evento

**Objetivo:** un evento confirmado entra solo en curso, la barra retira bebida imputada al evento, al terminar se registra lo que vuelve y lo que queda, y el cierre fija el consumo real por asistente.

Continúa `docs/sprint-3.md`, que cubrió la entrada de la mercadería (catálogo, ubicaciones, ingreso, inventario inicial, consulta y ajuste de stock). Estas son las historias que ahí quedaron «Pendientes del sprint» y que construye Bernardita.

## Historias

| Historia | Enunciado | Prioridad |
|---|---|---|
| Registrar realización de evento | Registrar el inicio y la finalización de la jornada del evento para reflejar su transición a los estados En curso y Realizado y habilitar las operaciones correspondientes. Las transiciones las realiza el sistema de forma automática según la fecha y el turno del evento. | Media |
| Registrar salida a barra | Como encargada de barra, quiero registrar los retiros de bebida desde el depósito de transición hacia la barra, imputados al evento, mediante escaneo del artículo o selección manual, para que cada movimiento quede asociado al evento y pueda determinarse posteriormente su consumo. Los retiros directos desde el depósito madre se registran de igual manera cuando deban utilizarse como contingencia. | Alta |
| Registrar devolución de bebida | Como encargada de barra, quiero registrar la devolución del sobrante al depósito al cierre del evento y el remanente que permanece en la barra para el evento siguiente, para no perder ese saldo entre eventos. | Alta |
| Registrar cierre de evento | Como encargada de barra, quiero registrar la conciliación final de las entregas de un evento, para fijar su consumo real y pasarlo al estado "cerrado". | Alta |

## Orden de trabajo

Cada historia necesita la anterior: sin En curso no hay salida a barra, sin salidas no hay qué devolver y sin devolución no hay qué cerrar.

1. Registrar realización de evento · 2. Registrar salida a barra (incluye el retiro adicional) · 3. Registrar devolución de bebida · 4. Registrar cierre de evento

Ramas: `berni/A3-registrar-realizacion-evento`, `berni/B4-registrar-salida-barra`, `berni/B4-registrar-devolucion-bebida`, `berni/B4-registrar-cierre-evento`.

Se apoya en lo construido en la primera parte del sprint: `MovimientoService` (asientos y saldo), `Ubicacion` con su origen de abastecimiento y el retiro directo, el lector de códigos (UI-30), el componente de cantidad en cajas y botellas y la alerta `ALERTA_STOCK` del ajuste. Las historias «Registrar preparación del evento» y «Consultar consumo de bebida por evento» las construyen Mateo y Tomás en paralelo; esta parte no depende de ellas, pero el cierre escribe el `consumo_evento` que lee la consulta de consumo.

## Decisiones del equipo

1. **En curso y Realizado son automáticos.** Nadie los registra. Un proceso programado (`@Scheduled`, cada minuto, y una vez al arrancar para ponerse al día) pasa a En curso los eventos Confirmados cuya hora de inicio del turno ya llegó (fecha de la unidad + `turno.hora_inicio`) y a Realizado los En curso cuyo turno terminó (`turno.hora_fin`, el día siguiente si `cruza_medianoche`). Zona `America/Argentina/Cordoba`. Pasa por `MaquinaDeEstados` con usuario null («Sistema» en el historial), es idempotente y usa un `Clock` inyectable para poder probarlo. Un evento que no llegó a Confirmado no avanza solo. Estas transiciones no generan avisos.
2. **Cerrado no es automático.** Lo da el registro del cierre (decisión 12).
3. **Salida a barra (UI-31):** la registran la encargada de barra, Compras, Coordinación y Dirección, solo con el evento En curso. La encargada elige entre los eventos de la jornada (`ConsultaEventoService.eventosDeLaJornada`, la misma regla de jornada que la consulta de stock) y la barra del salón del evento (si el salón tiene dos, elige cuál).
4. **Entrega:** el primer retiro de un evento hacia una barra abre la `entrega` (evento + barra, ABIERTA) y genera movimientos `SALIDA_BARRA`; los siguientes, en la misma pantalla, son `RETIRO_ADICIONAL`. Una barra no puede tener dos entregas abiertas a la vez: si la del turno anterior sigue abierta, se rechaza con «Primero registrá la devolución de <evento>».
5. **Origen del retiro:** el origen es la ubicación de abastecimiento de la barra (el depósito de transición o el depósito madre). Si la barra permite el retiro directo, la pantalla ofrece «Retirar del depósito madre» como contingencia, y ese movimiento sale del madre igual imputado al evento.
6. **Renglones:** la bebida se identifica escaneando (UI-30) o eligiéndola a mano; la cantidad se carga en cajas y botellas sueltas, enteros, y se guarda en botellas. Una bebida no se repite en el mismo retiro. Solo bebidas activas.
7. **A ciegas:** la encargada no ve el saldo del origen. Si el retiro deja el origen negativo, se acepta y se avisa a Compras y Administración (`ALERTA_STOCK`), igual que en el ajuste. Sí ve lo que ya se entregó a su barra para ese evento («Ya entregado»).
8. **Botellas abiertas:** las botellas que quedan abiertas al cierre se imputan como consumidas por ese evento. Solo se devuelven o quedan como remanente las botellas cerradas, por eso la devolución y el remanente se cargan en cajas y botellas enteras. El evento siguiente no cuenta como consumo las botellas abiertas que hereda, sino las que abre él.
9. **Devolución (UI-32):** la registran la encargada de barra, Compras, Coordinación y Dirección, con el evento En curso o Realizado y la entrega abierta. Por cada bebida entregada pide cuánto vuelve al depósito y cuánto queda en la barra como remanente. Lo devuelto genera `DEVOLUCION` de la barra al depósito madre; el remanente queda en la barra y se registra como `REMANENTE` (origen y destino la barra) para que quede la constancia con usuario y momento. Si la barra se abastece de un depósito de transición, la misma pantalla pide lo que queda en la transición y lo devuelve al madre. Al registrar, la entrega queda CERRADA y no admite más retiros.
10. **Lo que se ve al devolver:** la encargada declara sin ver lo esperado. El consumo real se muestra recién después de registrar la devolución, nunca antes. No hay ningún importe en pantalla (el prototipo UI-33 muestra un «Total valorizado» que no va: el sistema no maneja dinero). La unidad se llama «caja», no «cajón».
11. **Cálculo del consumo, por evento y bebida:** `entregado = saldo teórico de la barra antes de la devolución` (remanente heredado + salidas + retiros adicionales ± ajustes de esa barra) · `consumo = entregado − devuelto − remanente`. Con esto el remanente del evento anterior entra solo, sin guardar un saldo inicial aparte.
12. **Cierre (UI-33):** lo registran la encargada de barra, Compras, Coordinación y Dirección, con el evento Realizado y todas sus entregas cerradas. Muestra por bebida entregado, devuelto, remanente y consumo, y el consumo por asistente. Al confirmar, en una transacción: escribe `consumo_evento` (congelado, no se recalcula), saca de la barra lo consumido para que su saldo quede igual al remanente, pasa el evento a Cerrado por la máquina de estados y, si había orden de preparación, la deja CERRADA. Después todo es solo lectura y se corrige con ajustes.
13. **Salida del consumo del stock:** para que el saldo de la barra quede en el remanente hace falta un asiento que saque lo consumido. Se agrega el tipo `CONSUMO` (origen la barra, sin destino, con `evento_id`) en una migración nueva, actualizando el `CHECK` de `movimiento_stock.tipo` y el diccionario de datos. Si en el plan aparece una alternativa con los tipos que ya existen, se discute antes de escribir código.
14. **Consumo por asistente:** lo consumido dividido por la suma de la asistencia real por segmento si el tipo de evento usa segmentos; si no, por `evento.cantidad_invitados`. La asistencia real por segmento («Registrar asistencia real de cierre») es del Sprint 4: mientras no exista, el cierre de un tipo con segmentos divide por la cantidad de invitados y la pantalla lo aclara.
15. **Documentación:** se quita del diccionario de datos (sección 4) el punto «Botellas abiertas al cierre», que ya está definido (decisión 8), y se agrega el tipo `CONSUMO`.
16. **Fuera de esta parte:** preparación del evento y orden de preparación, consulta de consumo por evento, consulta de movimientos de stock (UI-37), asistencia por segmento y vista de cocina.

## Criterios de aceptación

### Registrar realización de evento

- Un evento Confirmado pasa a En curso cuando llega la hora de inicio de su turno, y a Realizado cuando termina el turno (el día siguiente si el turno cruza la medianoche), sin intervención de nadie.
- Cada transición queda en `cambio_estado_evento` con usuario null; la ficha la muestra como «Sistema».
- Si el sistema estuvo apagado, al arrancar pone al día los eventos atrasados (un Confirmado cuyo turno ya terminó pasa a En curso y a Realizado, en ese orden).
- Un evento Pre-reserva, Señado o Contratado no cambia de estado aunque llegue su fecha. Cancelado, Liberada y Cerrado tampoco.
- En curso bloquea la edición comercial (ya lo hace la máquina de estados: modificar llega hasta Confirmado).
- Tests con `Clock` fijo para los dos turnos, incluido el que cruza la medianoche.

### Registrar salida a barra (UI-31 y UI-30)

- La registran la encargada de barra, Compras, Coordinación y Dirección. El resto recibe 403.
- Solo con el evento En curso; si no, 409 con un mensaje que dice el estado del evento.
- La encargada ve solo los eventos de la jornada y las barras de sus salones; pedir otro evento o barra devuelve 403.
- El primer retiro abre la entrega y registra `SALIDA_BARRA`; los siguientes registran `RETIRO_ADICIONAL` sobre la misma entrega.
- Origen según la decisión 5; «Retirar del depósito madre» solo aparece si la barra lo permite.
- En una transacción: un `movimiento_stock` por renglón con evento, entrega, usuario y momento, y los saldos de origen y barra actualizados.
- La respuesta para la barra no incluye el saldo del origen (se verifica en el backend). Un saldo negativo se acepta y avisa a Compras y Administración.
- La pantalla resume «Salen 6 cajas a Barra Avril» antes de registrar y muestra «Ya entregado» por bebida.

### Registrar devolución de bebida (UI-32)

- La registran la encargada de barra, Compras, Coordinación y Dirección, con el evento En curso o Realizado y la entrega abierta. El resto recibe 403.
- Lista cada bebida con saldo en la barra; pide devuelto y remanente en cajas y botellas enteras, ≥ 0.
- Si devuelto + remanente supera lo que tiene la barra, se rechaza con un mensaje que nombra la bebida.
- Registra `DEVOLUCION` (barra → depósito madre) y `REMANENTE` por bebida, y la devolución desde la transición si corresponde, en una transacción. La entrega queda CERRADA.
- Antes de registrar no se muestra ni el saldo teórico ni lo esperado; después se muestra el consumo real.

### Registrar cierre de evento (UI-33)

- Lo registran la encargada de barra, Compras, Coordinación y Dirección, con el evento Realizado y todas sus entregas cerradas. Si falta alguna devolución, 409 diciendo qué barra.
- Muestra por bebida entregado, devuelto, remanente y consumo, y el consumo por asistente (decisión 14). Sin importes.
- Al cerrar: `consumo_evento` escrito, asiento `CONSUMO` por bebida consumida, saldo de la barra igual al remanente, evento Cerrado con su fila en el historial y orden de preparación CERRADA si existía.
- Un evento Cerrado no admite más movimientos imputados ni se puede volver a cerrar.
- El remanente queda como saldo de la barra y lo ve la consulta de stock; en el evento siguiente entra al cálculo como parte de lo entregado.

### Objetivo del sprint (punta a punta)

- Con los datos de demostración: un evento confirmado de hoy pasa a En curso, la barra retira con salida y retiro adicional, registra devolución y remanente, el evento pasa a Realizado y al cerrarlo se ve el consumo real y por asistente. Un segundo evento en la misma barra hereda el remanente.
