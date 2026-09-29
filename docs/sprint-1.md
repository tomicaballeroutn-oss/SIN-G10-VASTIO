# Sprint 1 — Agenda comercial

**Objetivo:** una vendedora ve la agenda, aparta una fecha y registra la seña sin pisar a otra.

## Historias

| Historia | Enunciado | Prioridad |
|---|---|---|
| Registrar pre-reserva | Como vendedora, quiero registrar una pre-reserva sobre una unidad comercializable mientras negocio con un cliente, para apartar la fecha sin comprometerla definitivamente. La pre-reserva no vence automáticamente. | Alta |
| Liberar pre-reserva | Como vendedora, quiero liberar una pre-reserva que no prosperó, para dejar disponible la fecha sin que se registre como la cancelación de un evento. | Alta |
| Registrar historial de cambios de estado | Como sistema, quiero registrar el historial de transiciones de estado de cada evento (usuario, fecha, hora), para garantizar la trazabilidad. | Alta |
| Registrar evento | Como vendedora, quiero registrar los datos básicos del evento (cliente, contactos, vendedora interviniente, tipo de evento, cantidad de invitados, salón, fecha, turno y observaciones internas), para que la información quede disponible para todo el equipo. La exclusividad de salón, fecha y turno quedará respaldada por una restricción en la base de datos. | Alta |
| Consultar agenda | Como vendedora, quiero consultar en un calendario único la disponibilidad de los tres salones por fecha y turno, para saber sin ambigüedad si puedo ofrecer una fecha a un cliente. | Alta |
| Consultar evento | Como integrante del equipo comercial, quiero consultar la ficha completa de un evento con su estado actual y el historial de cambios, para conocer en un solo lugar toda la información del evento sin recurrir a planillas ni al calendario. | Alta |
| Registrar seña de evento | Como vendedora, quiero registrar la seña de un evento pre-reservado, para pasarlo al estado "señado" y convertir la pre-reserva en una reserva firme. | Alta |
| Configurar parámetros del sistema | Como administración, quiero administrar los datos paramétricos del sistema (salones, turnos, tipos de evento, segmentos de asistencia, sectores, proporciones de consumo, categorías de servicios contratados y horizonte de la vista de cocina), para configurar el sistema sin intervención técnica. | Media |
| Alta mínima de usuarios | Adelantada del backlog para que el equipo pueda cargar vendedoras y planners reales: alta, listado y baja lógica. | — |

## Decisiones del equipo

1. **Seña:** la registran la vendedora titular (solo sus eventos), Coordinación y Dirección.
2. **Titular de la pre-reserva:** la vendedora siempre pre-reserva a su nombre; Coordinación y Dirección eligen la vendedora interviniente.
3. **Pre-reserva y evento:** la pre-reserva pide salón, fecha, turno, cliente y tipo. El nombre del evento se arma solo («Quince de Delfina Ríos») y se puede editar. «Registrar evento» completa contactos, invitados y observaciones.
4. **Cliente:** se busca por nombre o documento; si no existe, se crea en el mismo formulario. El DNI no es obligatorio.
5. **Contactos:** teléfono y correo del cliente en la pre-reserva; contactos adicionales en «Registrar evento».
6. **Notificaciones:** la pre-reserva y la seña guardan el aviso para Administración y Coordinación. La campana y la lista llegan en otro sprint.
7. **Agenda:** vista de mes con filtros de salón y estado. Al tocar un día se ven sus 6 unidades. La vendedora ve quién tiene una unidad ocupada, pero no los datos de eventos de otra vendedora.
8. **Ficha del evento:** en este sprint solo las pestañas Datos e Historial.
9. **Parámetros:** salones, turnos, tipos de evento, segmentos de asistencia, categorías de servicio, motivos y parámetros generales (sesión y horizonte de cocina). Baja lógica en todo. El horario de un turno se puede modificar aunque haya eventos futuros: el salón se adapta a cambios de último momento. Sectores y proporciones de consumo pasan al Sprint 3, con el módulo de bebida del que dependen.
10. **Código de evento:** `EV-AAAA-NNNNN`, año de creación y número correlativo por año.
11. **Seña:** importe mayor a 0, fecha de pago no futura, DNI del firmante obligatorio; nombre y contacto del firmante opcionales. El turno de firma no se guarda.
12. **Usuarios:** se adelanta un alta mínima (alta, listado y baja). El cambio de contraseña obligatorio llega con «Mi cuenta» (UI-03).

## Criterios de aceptación

### Configurar parámetros del sistema (UI-06)

- Administración, Coordinación y Dirección editan salones (nombre, capacidad, activo) y turnos (nombre y horario). No se agregan salones ni turnos: cada salón tiene su color en el sistema de diseño y la agenda tiene 3 columnas y 2 turnos.
- Tipos de evento, segmentos de asistencia, categorías de servicio y motivos se agregan, se editan y se dan de baja (baja lógica: lo ya registrado los conserva).
- El horario de un turno se puede cambiar aunque haya eventos futuros. Si la hora de fin es anterior a la de inicio, el turno cruza la medianoche.
- Los parámetros generales se validan: sesión de 5 a 480 minutos, aviso menor que la sesión, horizonte de cocina de 1 a 90 días.
- Un nombre repetido se rechaza con un mensaje que dice cuál es el problema.
- El resto de los perfiles recibe 403 al intentar modificar.

### Alta mínima de usuarios (UI-05)

- Dirección y Coordinación listan, dan de alta y dan de baja usuarios. Nadie más.
- El alta pide nombre y apellido, usuario, uno o más perfiles, contacto opcional y una contraseña inicial de 8 caracteres o más. El usuario queda con `debe_cambiar_contrasena = true`.
- Un nombre de usuario repetido se rechaza. Nadie puede darse de baja a sí mismo.
- Un usuario dado de baja no puede iniciar sesión y no aparece para elegir como vendedora.

### Consultar agenda (UI-07)

- Vista de mes con los 3 salones × 2 turnos de cada día, con anterior, siguiente y «Hoy».
- Cada unidad muestra su estado por relleno, ícono y palabra: disponible, bloqueada o el estado del evento. Liberadas y canceladas se ven disponibles.
- Filtros de salón y de estado.
- La vendedora ve estado y vendedora de los eventos de otras, sin cliente, nombre ni código del evento.
- La ven Dirección, Coordinación, Administración, Vendedora, Planner y Compras. Barra y Cocina reciben 403.

### Registrar pre-reserva (UI-08)

- Desde una unidad disponible de la agenda: salón, fecha y turno llegan elegidos.
- Pide cliente (existente o nuevo), tipo de evento y nombre del evento (propuesto automáticamente, editable).
- La vendedora pre-reserva a su nombre; Coordinación y Dirección eligen la vendedora.
- No se aceptan fechas pasadas, salones ni tipos dados de baja.
- Dos pre-reservas simultáneas sobre la misma unidad: una entra y la otra recibe «Esa fecha ya está tomada. Elegí otro salón, otra fecha u otro turno.»
- El evento queda en Pre-reserva, con código `EV-AAAA-NNNNN`, y no vence solo.
- Se avisa a Administración y Coordinación.

### Registrar historial de cambios de estado

- Toda transición pasa por un único servicio que valida la transición, escribe `cambio_estado_evento` (estado anterior, nuevo, usuario, fecha y hora) y genera los avisos.
- `cambio_estado_evento`, `modificacion_evento`, `reprogramacion` y `movimiento_stock` rechazan UPDATE y DELETE desde la base.

### Consultar evento (UI-09)

- Encabezado con estado, salón, fecha, turno, código, cliente, vendedora y planner. Pestañas Datos e Historial.
- El historial muestra quién, qué y cuándo, con el componente `Actor`.
- La vendedora solo abre las fichas de sus eventos. Administración, Planner, Compras, Coordinación y Dirección abren todas. Barra y Cocina reciben 403.
- El importe de la seña solo llega a Dirección, Coordinación, Administración y la vendedora titular.
- Lista de próximos eventos («Mis eventos» para la vendedora y la planner).

### Registrar evento (UI-08)

- Completa cliente, contactos adicionales, tipo, nombre, cantidad de invitados y observaciones internas.
- Lo hacen la vendedora titular, la planner asignada, Coordinación y Dirección, entre Pre-reserva y Confirmado.
- Cada dato cambiado queda en `modificacion_evento` con valor anterior, nuevo, usuario y momento.
- Salón, fecha y turno no se cambian acá: eso es reprogramar (Sprint 2).

### Liberar pre-reserva (UI-10)

- Solo desde Pre-reserva; la hacen la vendedora titular, Coordinación y Dirección.
- El evento pasa a Liberada (no es una cancelación) y la unidad vuelve a verse disponible.
- Queda en el historial como liberación.

### Registrar seña de evento (UI-11)

- Solo desde Pre-reserva; la registran la vendedora titular, Coordinación y Dirección.
- Pide importe (mayor a 0), fecha de pago (no futura) y DNI del firmante (7 u 8 dígitos); nombre y contacto del firmante opcionales.
- El evento pasa a Señado, queda en el historial y se avisa a Administración y Coordinación.
- La pantalla confirma «Seña registrada. El evento pasó a Señado.»
