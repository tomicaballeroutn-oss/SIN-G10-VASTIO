# Sprint 2 — Del señado al confirmado

**Objetivo:** un evento señado llega a Confirmado con su legajo completo (contrato, invitados, servicios y planner), y cada área se entera de los cambios sin revisar el calendario.

## Historias

| Historia | Enunciado | Prioridad |
|---|---|---|
| Registrar firma de contrato | Como coordinadora comercial, quiero registrar la firma del contrato de un evento señado adjuntando el contrato digitalizado, para pasarlo al estado "contratado" y habilitar su organización. | Alta |
| Registrar servicios contratados | Como vendedora o planner, quiero registrar los servicios contratados por el cliente (recepción, plato principal, postre, after, bodega, tipo de barra, técnica, mobiliario, menús especiales y extras) en categorías configurables con descripción libre, para que todas las áreas trabajen sobre un único legajo del evento sin planillas paralelas. | Alta |
| Confirmar evento | Como planner, quiero confirmar un evento contratado una vez cerrados la cantidad definitiva de invitados, el menú y el tipo de barra, para pasarlo al estado "confirmado" y habilitar la generación de su orden de preparación. | Alta |
| Notificar cambios de evento | Como integrante de un área de la organización, quiero recibir una notificación en el sistema cuando se registra, modifica, reprograma o cancela un evento que involucra a mi área, para no depender de revisar el calendario ni de los grupos de WhatsApp. | Alta |
| Registrar cantidad de invitados | Como vendedora, quiero registrar la confirmación de la cantidad definitiva de invitados para reflejar la información más actualizada. | Media |
| Modificar evento | Como vendedora o coordinación comercial, quiero modificar los datos del evento ya creado, para atender eventuales cambios solicitados por el cliente o por cualquier otro motivo. | Media |
| Cancelar evento | Como coordinadora comercial, quiero cancelar un evento señado, contratado o confirmado registrando el motivo, para liberar la unidad comercializable cuando el cliente desiste. | Media |
| Asignar planner a eventos | Como coordinadora, quiero asignar una planner a cada evento contratado e incorporarla a la programación operativa, de modo que cada jornada tenga un responsable identificado. | Media |
| Consultar notificaciones | Como usuario del sistema, quiero consultar mis notificaciones y marcarlas como leídas, para llevar control de las novedades pendientes. | Media |
| Administrar usuarios | Como Dirección o Coordinación, quiero dar de alta, modificar y dar de baja usuarios, para mantener actualizado el acceso al sistema. | Media |

## Orden de trabajo

Se respeta la prioridad salvo donde hay dependencias: Confirmar necesita planner e invitados definitivos, y todas las historias que cambian un evento usan el ruteo de avisos.

1. Notificar cambios de evento · 2. Registrar firma de contrato · 3. Registrar servicios contratados · 4. Registrar cantidad de invitados · 5. Asignar planner · 6. Confirmar evento · 7. Cancelar evento · 8. Modificar evento (incluye reprogramar) · 9. Consultar notificaciones · 10. Administrar usuarios

## Decisiones del equipo

1. **Usuario inicial:** no se crea un perfil de superusuario. El primer usuario es el de Dirección que crea el sistema al arrancar con `VASTIO_ADMIN_CONTRASENA`; con él se cargan los demás. Administrar usuarios es de Dirección y Coordinación.
2. **Firma de contrato:** la registran Coordinación y Dirección. La fecha de firma no es anterior a la fecha de la seña ni futura: se registra cuando el contrato ya está firmado. Uno o más archivos (un contrato puede ser varias fotos), PDF, JPG o PNG, hasta 10 MB cada uno y hasta 10 archivos. Los archivos se guardan en disco (volumen de Docker); la base guarda la referencia en `documento_evento`.
3. **Quién ve el contrato:** Dirección, Coordinación, Administración y la vendedora titular, con el mismo criterio que el importe de la seña (el contrato tiene importes).
4. **Cantidad de invitados:** se registra desde su propio diálogo (UI-14) con el tilde «Cantidad definitiva»; el formulario de datos del evento deja de editarla para que haya un solo camino. Entero mayor a 0. Si supera la capacidad del salón se advierte, sin bloquear. En Confirmado se puede cambiar la cantidad pero no quitar el tilde.
5. **Servicios contratados:** un texto por categoría activa, hasta 2.000 caracteres. Las categorías dadas de baja que ya tienen texto se ven sin poder editarse. En Confirmado no se pueden vaciar las categorías requeridas para confirmar (Plato principal y Tipo de barra). Cada cambio queda en `modificacion_evento` como `servicio.<categoría>`.
6. **Asignar planner:** sigue la máquina de estados: Coordinación y Dirección, en Contratado y Confirmado. Se puede reasignar; quitarla solo en Contratado. Si la planner ya tiene otro evento activo esa fecha (cualquier salón o turno) se advierte, sin bloquear. «Programación operativa»: la planner ve en Próximos eventos el filtro «Asignados a mí».
7. **Confirmar evento:** lo hacen la planner asignada, Coordinación y Dirección. Exige planner asignada, cantidad de invitados mayor a 0 y marcada como definitiva, servicios cargados en las categorías requeridas para confirmar y que la fecha del evento no haya pasado. El diálogo muestra cada requisito y, si falta alguno, dice cuál.
8. **Cancelar evento:** Coordinación y Dirección, desde Señado, Contratado o Confirmado (la pre-reserva se libera, no se cancela). Motivo obligatorio del catálogo (ámbito Cancelación); el detalle es obligatorio solo si el motivo es «Otro».
9. **Modificar evento:** incluye reprogramar (salón, fecha y turno) y la hora de inicio. Modifican la vendedora titular, la planner asignada, Coordinación y Dirección, entre Pre-reserva y Confirmado (la planner queda a revisar con el cliente). Reprogramar lo hacen la vendedora titular, Coordinación y Dirección, con motivo obligatorio del catálogo (ámbito Reprogramación), y deja una fila en `reprogramacion`. En Pre-reserva cambiar la fecha es una modificación más: sin motivo, queda en `modificacion_evento`.
10. **Ruteo de avisos.** Nunca le llega el aviso a quien hizo el cambio. Un solo aviso por guardado, no uno por dato. Administración recibe todos los eventos nuevos y cambios; el resto de las áreas, solo lo que las afecta:

    | Aviso | Destinatarios |
    |---|---|
    | Evento nuevo, seña (Sprint 1) | Administración y Coordinación |
    | Contrato firmado | Administración, Coordinación y vendedora titular |
    | Planner asignada | Planner nueva, planner anterior (si había) y Administración |
    | Confirmación | Compras, Cocina, Administración y vendedora titular |
    | Modificación | Vendedora titular, planner asignada, Coordinación y Administración. Si el evento está Confirmado, además: Cocina si cambian invitados, hora de inicio o servicios visibles en cocina; Compras si cambian invitados, Bodega o Tipo de barra |
    | Reprogramación y cancelación | Dirección, Coordinación, Administración, Compras, Cocina, vendedora titular y planner asignada |

11. **Notificaciones (UI-22):** panel desde una campana en el encabezado con el contador de las sin leer. Más recientes primero, de a 20, con filtro «Sin leer» / «Todas». Abrir un aviso lo marca como leído y lleva a la ficha del evento; hay «Marcar todas como leídas». El contador se actualiza cada 60 segundos. Cocina no tiene acceso a la ficha: hasta que exista la vista de cocina (Sprint 3) su aviso se lee pero no abre nada.
12. **Administrar usuarios:** se suman la modificación (nombre, perfiles y contacto; el nombre de usuario no cambia), la reactivación y la búsqueda con filtro por perfil. No se puede dar de baja ni quitarle Dirección al último usuario activo de Dirección, y nadie se quita a sí mismo un perfil de acceso total. Dar de baja a alguien con eventos activos (como vendedora titular o planner) se permite, mostrando antes la lista de eventos afectados. Mi cuenta (UI-03) y el restablecimiento de contraseña quedan fuera de este sprint.
13. **Fuera del sprint:** paso automático a En curso y Realizado, orden de preparación, asistencia por segmento, bloqueo de unidades y vista de cocina.

## Criterios de aceptación

### Notificar cambios de evento

- Toda transición y toda modificación, reprogramación o asignación de planner genera el aviso que corresponde según la decisión 10, en la misma transacción que el cambio.
- Quien hizo el cambio no recibe el aviso. Los usuarios dados de baja no reciben avisos.
- El mensaje dice qué pasó y con qué evento (p. ej. «Evento reprogramado: Quince de Delfina Ríos») y lleva el id del evento.

### Registrar firma de contrato (UI-12)

- Solo desde Señado; la registran Coordinación y Dirección. El resto recibe 403.
- Pide fecha de firma (no anterior a la seña ni futura) y al menos un archivo PDF, JPG o PNG de hasta 10 MB. Se valida el tipo real del archivo, no solo la extensión.
- El evento pasa a Contratado, los archivos quedan como documentos de tipo CONTRATO y queda en el historial.
- Se avisa según la decisión 10. La pantalla confirma «Firma registrada. El evento pasó a Contratado.»
- La ficha muestra la pestaña Documentos; los archivos se descargan solo con los perfiles de la decisión 3.

### Registrar servicios contratados (UI-13)

- Pestaña Servicios de la ficha con un texto por categoría activa, en el orden configurado.
- La editan la vendedora titular, la planner asignada, Coordinación y Dirección, entre Pre-reserva y Confirmado. El resto la ve en solo lectura (si ve la ficha).
- Cada categoría cambiada queda en el historial con valor anterior y nuevo.
- En Confirmado, vaciar Plato principal o Tipo de barra se rechaza con un mensaje que dice cuál.
- Dos personas editando a la vez: la segunda recibe «Otra persona modificó el evento mientras lo editabas…».

### Registrar cantidad de invitados (UI-14)

- La registran la vendedora titular, la planner asignada, Coordinación y Dirección, entre Pre-reserva y Confirmado.
- Entero mayor a 0, con el tilde «Cantidad definitiva». Muestra la cantidad anterior y quién la cambió por última vez (`Actor`).
- Si supera la capacidad del salón, advierte sin bloquear.
- En Confirmado no se puede quitar el tilde.
- El cambio queda en el historial.

### Asignar planner (UI-16)

- Coordinación y Dirección, en Contratado o Confirmado. Solo usuarios activos con perfil Planner.
- Si la planner ya tiene otro evento activo esa fecha, la pantalla lo advierte y permite asignarla igual.
- Reasignar avisa a la planner nueva y a la anterior. Quitar la planner solo en Contratado.
- La asignación queda en el historial. La planner ve el evento con el filtro «Asignados a mí».

### Confirmar evento (UI-15)

- Solo desde Contratado; lo hacen la planner asignada, Coordinación y Dirección.
- El diálogo lista los requisitos (planner, invitados definitivos, servicios requeridos, fecha no pasada) con su estado; si falta alguno, el botón queda deshabilitado y la fila dice qué falta. El backend valida lo mismo.
- El evento pasa a Confirmado, queda en el historial y se avisa según la decisión 10.

### Cancelar evento (UI-17)

- Desde Señado, Contratado o Confirmado; Coordinación y Dirección. El resto recibe 403.
- Motivo obligatorio (solo motivos activos de Cancelación); detalle obligatorio si el motivo es «Otro», hasta 255 caracteres.
- El evento pasa a Cancelado, la fecha vuelve a verse disponible en la agenda y queda en el historial con el motivo.
- Se avisa a todas las áreas según la decisión 10.

### Modificar evento (UI-08 y UI-18)

- Los datos del Sprint 1 más la hora de inicio, con los mismos permisos y estados; cada cambio queda en `modificacion_evento` y se avisa según la decisión 10.
- Reprogramar: se elige la nueva unidad en la agenda; las ocupadas o bloqueadas no se pueden elegir. Pide motivo (salvo en Pre-reserva). No se aceptan fechas pasadas ni salones dados de baja.
- La reprogramación conserva la unidad original en `reprogramacion`, no cambia el estado, y la ficha muestra la fecha original y la nueva.
- Dos reprogramaciones simultáneas hacia la misma unidad: una entra y la otra recibe «Esa fecha ya está tomada. Elegí otro salón, otra fecha u otro turno.»

### Consultar notificaciones (UI-22)

- Todos los perfiles ven sus propias notificaciones, nunca las de otro usuario.
- Campana con contador de sin leer; lista más recientes primero, de a 20, con filtro Sin leer / Todas.
- Abrir un aviso lo marca como leído y lleva a la ficha (salvo Cocina). «Marcar todas como leídas» deja el contador en 0.

### Administrar usuarios (UI-05)

- Dirección y Coordinación listan, buscan, filtran por perfil, dan de alta, modifican, dan de baja y reactivan usuarios. Nadie más.
- Modificar cambia nombre, perfiles y contacto; el nombre de usuario no se edita. Al menos un perfil.
- No se puede dar de baja ni quitarle Dirección al último usuario activo de Dirección. Nadie se quita a sí mismo Dirección o Coordinación ni se da de baja.
- Antes de dar de baja a alguien con eventos activos, la pantalla muestra cuáles son (como vendedora titular o planner).
- Un usuario reactivado vuelve a poder iniciar sesión y a aparecer para elegir.
