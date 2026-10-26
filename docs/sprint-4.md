# Sprint 4 — Asistencia por segmento, orden de preparación y movimientos de stock

Este documento cubre las historias del sprint 4 que no estaban construidas al 26/10/2026. Las demás del sprint tienen su propio documento o rama: vista de cocina y seña (`sprint-4-cocina-y-sena.md`), reporte de ocupación (`a7-01-reporte-ocupacion.md`), estadística de consumo (`b5-01-estadisticas-consumo.md`), cuenta y preferencias y bloqueo de unidades (ramas de Nehuén). La reprogramación de evento ya estaba hecha desde el sprint 2.

**Objetivo:** la bebida de cada evento se anticipa con una orden sugerida a partir de lo consumido antes, y en los egresados se cuenta a todo el público, no solo a los comensales.

## Historias

| Historia | Enunciado | Prioridad |
|---|---|---|
| Registrar tipo de asistencia | Como planner, quiero registrar por separado los segmentos de asistencia prevista de un evento de egresados (comensales de la cena y entradas anticipadas a la fiesta posterior), para reflejar la asistencia real y no un número único estimado. | Media |
| Registrar asistencia real de cierre | Como planner, quiero registrar al cierre del evento la asistencia real por segmento, incorporando el recuento de la plataforma de control de acceso y de la venta en puerta, para reflejar la asistencia efectiva del evento. | Media |
| Generar orden de preparación | Como sistema, quiero generar una orden de preparación sugerida para cada evento confirmado a partir de la información del evento y los datos históricos de consumo, para anticipar las cantidades de bebida necesarias. | Alta |
| Ajustar orden de preparación | Como encargado de compras, quiero ajustar las cantidades sugeridas en la orden de preparación, para adecuarlas a las necesidades concretas del evento antes de preparar la bebida. | Media |
| Consultar movimientos de stock | Como responsable de barra, quiero consultar el historial de movimientos de stock para auditar una ubicación, una bebida o un evento. | Media |

## Orden de trabajo

La asistencia real necesita la prevista; la orden usa la asistencia prevista para dimensionar los egresados, y el ajuste necesita la orden. Los movimientos no dependen de nada.

1. Asistencia prevista · 2. Asistencia real · 3. Generar orden · 4. Ajustar orden · 5. Movimientos de stock

Ramas: `mateo/S4-asistencia-prevista` → `mateo/S4-asistencia-real` → `mateo/S4-generar-orden` → `mateo/S4-ajustar-orden`, encadenadas en ese orden, y `mateo/S4-movimientos-stock` desde `main`.

## Decisiones del equipo (26/10/2026)

### Asistencia por segmento

1. **Qué es cada número en egresados.** La cantidad de invitados de siempre («Registrar cantidad de invitados») son los **comensales de la cena**: es lo que cocina prepara y lo que ve la vista de cocina. Los segmentos agregan el otro público, el de la fiesta posterior: entradas anticipadas y venta en puerta. Nadie se cuenta dos veces.
2. **Segmento que equivale a los invitados.** Los segmentos son configurables; uno de ellos se marca en Parámetros como «Equivale a los invitados del evento» (en la carga inicial, «Comensales cena»). Solo uno puede tenerla: marcarla en otro se la quita al anterior. Su asistencia prevista es la cantidad de invitados del evento y no se carga aparte.
3. **Asistencia prevista (UI-19).** La registran la planner asignada, Coordinación y Dirección (la vendedora no), de Pre-reserva a Confirmado. Se listan los segmentos activos; todos son opcionales (la venta en puerta prevista se puede dejar vacía). Solo en los tipos de evento que usan segmentos (Egresados). Cada cambio queda en el historial del evento y, si el evento está confirmado, avisa a Compras como un cambio de invitados.
4. **Total previsto** = invitados + la suma de los demás segmentos. Es sobre lo que se dimensiona la bebida (contexto del negocio, «Egresados»).
5. **Requisito para confirmar** un evento de un tipo con segmentos: al menos un segmento, además del que equivale a los invitados, con asistencia prevista mayor a 0.
6. **Asistencia real (UI-20).** Un número real por segmento, cargado a mano: el de los comensales y las anticipadas sale de la plataforma de control de acceso; el de «Venta en puerta», de la venta en puerta. No hay integración con la plataforma. Se carga con el evento Realizado o Cerrado, todos los segmentos a la vez (0 vale). La registran la planner asignada, Coordinación y Dirección.
7. **No bloquea el cierre.** La barra cierra a la madrugada y la planner puede no estar. Lo consumido queda congelado al cerrar; la asistencia real solo cambia el divisor del consumo por asistente, que se calcula al consultar.
8. **Consumo por asistente.** En los tipos con segmentos y la asistencia real cargada, lo consumido se divide por el total real; si no, por la cantidad de invitados, y la pantalla lo aclara («falta la asistencia real»). Aplica al cierre (UI-33), a la pestaña Consumo de la ficha (B4-05) y a la estadística de consumo (B5-01), evento por evento.
9. **Aviso.** Cuando un evento de un tipo con segmentos pasa a Realizado, la planner asignada recibe «Cargá la asistencia real de cierre», que abre la ficha en la pestaña Asistencia.
10. **Pendiente para más adelante:** un perfil «Puerta» (el guardia que vende las entradas en puerta) que solo pueda cargar la asistencia real. No entra en este sprint.

### Orden de preparación

11. **Cuándo se genera.** Automáticamente cuando el evento pasa a Confirmado. Además, «Generar orden» para un evento Confirmado o En curso sin orden vigente (los confirmados antes de esta historia o con la orden descartada).
12. **Cálculo de la sugerencia, por bebida:** botellas por asistente × asistentes del evento (decisión 4; sin segmentos, los invitados), más el margen, redondeado hacia arriba a **cajas completas** según las unidades por bulto de la bebida. La sugerencia es solo eso: el número final lo pone Compras.
13. **De dónde sale «botellas por asistente», en este orden:**
    1. Los eventos Cerrados del mismo tipo de evento (cualquier salón), si son al menos 3: consumo total sobre asistentes totales (decisión 8 para el divisor de cada evento), como en B5-01.
    2. Si no alcanzan, las **proporciones de consumo** cargadas para ese tipo de evento en Parámetros.
    3. Si no hay proporciones, los eventos Cerrados de todos los tipos.
    4. Si no hay ningún evento cerrado, la orden sale vacía y Compras agrega las bebidas a mano.

    Entran las bebidas activas con consumo o proporción mayor a 0. La orden guarda de dónde salió la sugerencia y sobre cuántos eventos.
14. **Proporciones de consumo (ABM nuevo en Parámetros).** Por tipo de evento y bebida, las botellas por asistente. Las cargan Administración, Coordinación y Dirección. Sirven para arrancar con datos reales de eventos anteriores al sistema.
15. **Margen:** parámetro nuevo `MARGEN_ORDEN_PORCENTAJE`, por defecto 10 (de 0 a 100).
16. **Una orden por evento**, con los totales de todas sus barras (el consumo histórico está consolidado por evento). Origen: el depósito madre; sin destino. Si no hay depósito madre activo no se genera.
17. **Responsable y auditoría.** Una orden generada al confirmar no tiene usuario (la pantalla dice «Sistema»); la generada a mano guarda quién. Se guarda además quién y cuándo la modificó, la confirmó o la reabrió, y se muestra con `Actor`.
18. **«Comprometido por otros eventos próximos»** (UI-35): por cada bebida de la orden, la suma de «a retirar» de las órdenes vigentes de otros eventos de los próximos 15 días.
19. **Cambios después de generada.** La reprogramación no descarta la orden; la cancelación sí (queda Descartada y se puede generar otra si el evento vuelve a estar en condiciones, que hoy no pasa). Si los asistentes cambiaron desde que se generó, la orden lo avisa con el número anterior y el actual, y mientras no esté Confirmada se puede «Recalcular sugerencia» (vuelve a Sugerida y pisa los ajustes, con confirmación).
20. **Aviso.** Al generarse la orden, Compras recibe «Orden de preparación sugerida», que abre la ficha en la pestaña Orden.
21. **Dónde.** Pestaña «Orden» en la ficha del evento, y página «Órdenes de preparación» con los eventos Confirmados y En curso desde hoy y el estado de su orden. «Vista para imprimir».
22. **Quién.** Compras, Coordinación y Dirección generan, recalculan, ajustan, confirman y reabren; Administración consulta. El resto no la ve (la barra nunca: operación a ciegas).
23. **Ajustar.** Se cambia «a retirar» cargando cajas y botellas sueltas; se pueden agregar bebidas no sugeridas (sugerida 0) y «sacar» una dejándola en 0. No se guarda historial por renglón: solo quién modificó y cuándo. Con el evento Confirmado o En curso.
24. **Estados** (máquina de estados de la orden): el primer ajuste pasa de Sugerida a Modificada; «Confirmar orden» la pasa a Confirmada y ya no se edita; «Reabrir orden» la devuelve a Modificada si algo cambió sobre la marcha. Al cerrar el evento queda Cerrada; al cancelarlo, Descartada.
25. **Para qué sirve la orden confirmada.** La salida a barra queda vinculada a ella (`entrega.orden_id`). La pestaña Preparación (B5-04) la muestra como referencia, en una columna «Según orden», sin precargar nada. A la encargada de barra no se le muestra.

### Movimientos de stock (UI-37)

26. **Quién.** Como dice la matriz: Administración y Compras, además de Coordinación y Dirección. La barra no, por la operación a ciegas (el enunciado dice «responsable de barra», pero prevalece la matriz).
27. **Filtros:** ubicación (como origen o destino), bebida, evento, tipo de movimiento y rango de fechas desde/hasta (por la fecha y hora del movimiento). Paginado en el servidor.
28. **Presentación.** Una tarjeta por operación: se agrupan los asientos del mismo tipo, con el mismo origen, destino, evento, usuario y momento (un ingreso, una preparación, una salida a barra). Ajustes y mermas muestran el motivo, la observación y el movimiento que corrigen. Se muestran todos los tipos, incluidos Remanente y Consumo.

## Modelo

- **V14** (asistencia prevista): `tipo_segmento_asistencia.equivale_invitados` (boolean, uno solo en true; «Comensales cena» lo trae).
- **V15** (asistencia real): tipo de aviso `ASISTENCIA_REAL`.
- **V16** (orden): `orden_preparacion` con destino y responsable opcionales, versión, fuente de la sugerencia, asistentes y eventos de base, quién la modificó, confirmó y reabrió; parámetro `MARGEN_ORDEN_PORCENTAJE`; tipo de aviso `ORDEN_PREPARACION`.

## Criterios de aceptación

### Asistencia prevista

- En un evento de Egresados, la pestaña Asistencia muestra cada segmento con su previsto, los comensales tomados de los invitados y el total previsto. En otros tipos, la pestaña no aparece.
- La planner asignada, Coordinación y Dirección la registran entre Pre-reserva y Confirmado; la vendedora y el resto reciben 403. Un evento de otro tipo devuelve 422.
- Confirmar un evento de Egresados sin asistencia prevista de la fiesta se rechaza y el diálogo de confirmación lo muestra como requisito pendiente.
- El historial muestra cada cambio de segmento.

### Asistencia real

- Con el evento Realizado o Cerrado, se cargan los reales de todos los segmentos; antes, 422.
- El cierre, la pestaña Consumo y la estadística dividen por el total real cuando está cargado y lo dicen; si falta, dividen por invitados y avisan.
- Al pasar a Realizado un evento de Egresados, la planner recibe el aviso.

### Generar orden

- Confirmar un evento genera su orden Sugerida y avisa a Compras; el cálculo sigue las decisiones 12 a 16 y redondea a cajas completas.
- Sin historia ni proporciones, la orden sale vacía y lo explica.
- Cancelar el evento descarta la orden; cerrarlo la cierra.
- Si cambian los invitados o la asistencia prevista, la orden avisa y permite recalcular.
- Administración consulta; Vendedora, Planner, Barra y Cocina reciben 403.

### Ajustar orden

- Compras cambia cantidades en cajas y botellas, agrega bebidas, confirma y reabre. Confirmada, no se edita.
- La salida a barra de un evento con orden confirmada queda vinculada a ella y la pestaña Preparación la muestra como referencia.

### Movimientos de stock

- Administración, Compras, Coordinación y Dirección filtran por ubicación, bebida, evento, tipo y fechas, con paginado. Barra, Cocina, Planner y Vendedora reciben 403.
- Cada operación se ve como una tarjeta con su tipo, origen y destino, el evento, las bebidas en cajas y botellas, y quién y cuándo la registró.
