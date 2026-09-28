# De la persona a la pantalla

El orden de trabajo es el mismo para cada funcionalidad: primero el flujo de la persona, después la arquitectura de la información, después el wireframe en grises y recién al final los componentes de Vastio. Si un wireframe no se entiende en blanco y negro, el problema es estructural, no de diseño visual.

## Quién usa qué

| Perfil | Dispositivo habitual | Tarea principal | Componentes clave |
| --- | --- | --- | --- |
| Vendedora | Escritorio, tableta, teléfono | Ver disponibilidad, pre-reservar, registrar seña, confirmar | `AgendaGrid`, `EventCard`, `StatusChip`, `Timeline` |
| Administración | Escritorio | Configurar parámetros, usuarios, planner, ingreso de mercadería, reportes | `Table`, `Input`, `Select`, `Stat` |
| Planner | Tableta, teléfono | Consultar los eventos que tiene asignados | `EventCard`, `AgendaGrid` |
| Encargado de compras | Escritorio, teléfono | Registrar recepción de mercadería, mirar existencias y sugerencias | `Table`, `StockLevel`, `Stepper` |
| Encargada de barra | Tableta, teléfono, de noche | Recibir la orden de preparación, entregar a barra, retiros adicionales, devolver | `Stepper`, `MovementCard`, `StockLevel`, tema oscuro |
| Propiedad | Escritorio | Leer ocupación, consumo y costo por evento | `Stat`, `Table` |

## Flujos principales

**Reservar una fecha (Módulo A).** Consultar la agenda → elegir día → ver la disponibilidad de los seis turnos → registrar pre-reserva (la fecha queda apartada a nombre de la vendedora) → registrar seña (Señado) → confirmar (Confirmado) → registrar realización (Realizado). Salidas posibles: liberar la pre-reserva, cancelar el evento (libera la fecha) o reprogramarlo (conserva la programación original). Si la fecha ya está tomada, `Alert` de tono `danger` con "Elegí otro salón, otra fecha u otro turno". Cada paso muestra quién lo hizo y cuándo con `Actor`.

**Bebida de un evento (Módulo B).** Orden de preparación propuesta a partir de la agenda, el remanente en barra y el consumo histórico (la persona revisa y ajusta con `Stepper`) → entrega a barra imputada al evento → retiros adicionales como transferencias sucesivas de la misma entrega → devolución del sobrante y remanente que queda en barra → consumo real por diferencia → estadísticas. Cada movimiento es un `MovementCard` no editable: se corrige con un ajuste.

## Estados de un evento

Los estados siguen el story map del Seguimiento del Proyecto. Cada uno tiene un chip fijo (`StatusChip`).

| Estado | Chip | Cómo se llega | Cómo se sale |
| --- | --- | --- | --- |
| Disponible | `disponible` | Fecha libre o liberada | Registrar pre-reserva |
| Pre-reserva | `prereserva` | Vendedora aparta la fecha mientras negocia | Registrar seña, o liberar |
| Señado | `senado` | Se registra la seña | Confirmar, o cancelar |
| Confirmado | `confirmado` | Se confirma el evento; queda habilitado para planificación | Realizar, reprogramar o cancelar |
| Realizado | `realizado` | Transcurrida la jornada | Fin del ciclo comercial |
| Cancelado | `cancelado` | Cancelación registrada; libera la unidad | Queda en el historial |
| Bloqueado | `bloqueado` | Administración bloquea la unidad (mantenimiento, feriado, evento propio) | Desbloquear |

Reprogramar no es un estado: es una acción que mueve el evento a otra fecha, turno o salón y guarda la programación original en el historial.

## Arquitectura de información

```
Agenda            Calendario · Eventos · Bloqueos
Bebidas           Existencias · Movimientos · Órdenes de preparación · Catálogo
Gestión           Reportes · Usuarios y perfiles · Parámetros
```

- En escritorio, `Nav` lateral con esas tres secciones como grupos. En teléfono y tableta, `Nav` inferior con cinco ítems: Agenda, Eventos, Existencias, Movimientos y Más (el resto de las secciones).
- Cada perfil recibe solo sus ítems; una sección sin ítems permitidos no se dibuja.
- La agenda es la pantalla de inicio de comercial; Movimientos, la de barra.

## Pantallas por sprint

Inventario para diseñar wireframes, según el story map: Sprint 1, inicio de sesión, agenda, pre-reserva, seña, confirmación, realización, cantidad de invitados y parámetros. Sprint 2, usuarios, detalle de evento con historial, cancelar, modificar, reprogramar, planner y asistencia por segmento. Sprint 3, catálogo de bebidas, compras e ingreso, existencias y devolución. Sprint 4, bloqueos, orden de preparación, reportes de ocupación y estadística de consumo.

## Reglas de wireframe

- Cajas grises, texto real (no lorem ipsum) y sin color: la estructura tiene que entenderse sola.
- Cada pantalla nombra su acción principal (un único botón `solid`) antes de dibujar nada más.
- Verificá cada wireframe en tres anchos: 390, 768 y 1280 px.
- Recién cuando el wireframe funciona se reemplazan las cajas por componentes de Vastio, sin inventar variantes.
