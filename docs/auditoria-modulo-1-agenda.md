# Auditoría del módulo 1: Agenda de eventos

Alcance: Sprint 0 técnico, Sprint 1 (agenda comercial) y pantallas de eventos ampliadas en Sprint 2.

## Procedencia y seguimiento

Documento histórico recuperado del commit baf3ca6 de #52, que ya lo retiró en 0f5eb45. Se publica separado sobre main f94fd73, sin repetir el recorrido ni atribuir vigencia actual a los hallazgos. La nota de instalación al final describe aquella sesión, no las pruebas de este PR. UTF-8 sin BOM.

Hallazgos Alta registrados tras revisar todos los issues abiertos y cerrados para evitar duplicados:

- Estado: [#61](https://github.com/tomicaballeroutn-oss/SIN-G10-VASTIO/issues/61).
- Encabezado: [#62](https://github.com/tomicaballeroutn-oss/SIN-G10-VASTIO/issues/62).
- Obligatorios: [#63](https://github.com/tomicaballeroutn-oss/SIN-G10-VASTIO/issues/63).

Cada incidencia requiere revalidación contra main antes de implementar; no son correcciones funcionales de este documento.

## Estado

Revisión estática y recorrido visual de escritorio/teléfono completados con cuentas de prueba de Dirección y Vendedora. Se revisaron la agenda mensual, el detalle del día, las listas, una ficha, sus pestañas, formularios y diálogos de acciones. No se guardaron cambios en los eventos.

## Recorrido realizado

- Agenda mensual y detalle de las seis unidades en escritorio (1440 px) y teléfono (390 px); sin desborde horizontal. Con un estado seleccionado, se atenúan los puntos que no coinciden y el detalle mantiene las seis unidades.
- Diálogo de pre-reserva abierto desde una unidad disponible; no se confirmó.
- Lista de siete eventos, ficha y pestañas Datos, Servicios, Documentos e Historial.
- Formularios de datos y reprogramación abiertos sin guardar.
- Diálogos abiertos: seña, liberación, firma, cantidad de invitados, cambio de planner, confirmación y cancelación. No se ejecutaron acciones.
- La confirmación enumera requisitos cumplidos y pendientes y deshabilita el botón mientras falten servicios.
- Perfiles revisados visualmente: Dirección y Vendedora. No se guardaron cambios ni se probaron permisos directos del servidor.

## Hallazgos y propuestas

| Prioridad | Pantalla | Hallazgo observado | Propuesta |
|---|---|---|---|
| Alta | Agenda mensual | El control dice “Estado” y parece un filtro, pero la cuadrícula atenúa los puntos que no coinciden; el detalle del día mantiene las seis unidades. No oculta resultados. | Cambiar el rótulo a “Resaltar estado” y explicar que las otras unidades siguen visibles, o implementar un filtro real que mantenga visibles los estados ocupados/bloqueados. Alinear el comportamiento con el criterio de Sprint 1. |
| Alta | Ficha del evento | El encabezado muestra estado, salón, fecha, turno, nombre, código, tipo y planner, pero no cliente ni vendedora. El criterio UI-09 pide ambos en el encabezado. | Agregar cliente y vendedora al resumen superior, cuidando que siga legible en teléfono. |
| Alta | Pre-reserva, seña, firma, invitados y cancelación | Los botones principales aparecen activos con datos obligatorios vacíos: cliente/tipo/vendedora, DNI e importe, archivo del contrato, cantidad de invitados en 0 o motivo. La obligatoriedad solo se explica en algunos campos o llega después como error. | Marcar los campos obligatorios y deshabilitar la acción hasta cumplir los mínimos válidos. Mantener los errores del servidor para conflictos y reglas que no se puedan comprobar en el cliente. |
| Media | Agenda mensual | La leyenda visible incluye Pre-reserva, Señado, Contratado, Confirmado y Bloqueado. El sistema también contempla En curso, Realizado y Cerrado; en la cuadrícula mensual esos estados se distinguen solo por los puntos. | Completar la leyenda con todos los estados que puedan aparecer al navegar meses anteriores, o explicar qué estados se muestran. Revisar contraste y formas en tema claro y oscuro. |
| Media | Agenda en teléfono | A 390 px no hay desborde horizontal. La cuadrícula de siete columnas y seis puntos por día quedan compactos; el detalle pasa a una lista vertical larga con seis botones repetidos. | Mantener el acceso al detalle completo, pero probar etiquetas más cortas en los botones y un resumen de disponibilidad por turno para acelerar el escaneo. |
| Media | Diálogo de pre-reserva en teléfono | El diálogo ocupa casi todo el alto disponible y puede requerir desplazamiento para llegar a las acciones en pantallas más bajas. No hay desborde horizontal. | Probar con teclado virtual y menor altura; mantener el botón principal accesible al final del desplazamiento y el contexto de salón/fecha visible. |
| Baja | Agenda mensual | El antetítulo “AGENDA” queda inmediatamente encima del título “Agenda”; la navegación ya agrupa el módulo bajo ese nombre. | Quitar el antetítulo repetido para reducir ruido. |
| Baja | Eventos / Mis eventos | Las tarjetas se leen bien y se adaptan a varias columnas. Solo Planner tiene una pestaña de filtro; no hay búsqueda ni filtros por fecha/estado. | Evaluar búsqueda y filtros cuando crezca el volumen; no hace falta priorizarlo para la muestra actual. |
| Baja | Reprogramación | Se navega mes a mes desde el mes del evento y hay que elegir un día antes de ver sus unidades. La confirmación sí permanece deshabilitada hasta elegir una unidad. | Agregar “Ir a hoy” o un acceso rápido al mes actual; conservar la confirmación deshabilitada hasta tener una unidad válida. |
| A favor | Confirmación, planner y servicios | El diálogo de confirmación detalla requisitos cumplidos y faltantes y bloquea la acción si faltan servicios. Asignar planner y guardar servicios también bloquean la acción cuando no hay cambios. | Conservar este patrón de validación visible y reutilizarlo en pre-reserva, seña, firma, invitados y cancelación. |

## Otras observaciones

- La agenda muestra salón, turno y estado en el detalle del día; la vendedora y los datos del evento quedan disponibles allí según el perfil.
- Los estados se presentan con palabra e ícono en chips; el mes usa puntos con formas/rellenos y una leyenda.
- La ficha organiza datos y trazabilidad en pestañas, y mantiene las acciones relacionadas con el estado a mano.
- En el perfil de Dirección, la lista de muestra carga siete eventos y permite llegar a cada ficha desde la tarjeta.
- En el perfil de Vendedora, “Mis eventos” muestra cuatro eventos propios. En la agenda, una unidad contratada de otra vendedora muestra el estado y quién la tiene, pero no el nombre del evento, cliente, código ni un acceso a su ficha.
- La vista de Servicios es extensa en escritorio porque muestra todas las categorías en una columna; en teléfono conviene comprobar el desplazamiento.

## Privacidad por perfil

La comprobación visual de Vendedora confirma que la lista personal está acotada a sus eventos y que las unidades ocupadas por otra vendedora no exponen la identidad ni los datos de la reserva. La agenda sí muestra el nombre de la persona que tiene la unidad para explicar por qué no está disponible. No se probó el acceso directo a una ficha ajena ni la autorización del servidor; esta observación describe solo las pantallas recorridas.

## Pendiente

- Revisar el teclado virtual y la altura real de los diálogos en un dispositivo móvil.
- Revisar estados de catálogo vacío y fallos de red.
- Acordar si el control Estado atenúa unidades o filtra resultados.

## Nota de instalación

npm install --no-audit --no-fund completó sin cambios en package.json ni package-lock.json. npm mostró avisos EBADENGINE: el entorno tiene Node 24.14.1 y algunas dependencias declaran mínimo Node 24.15.0. No se ejecutaron tests.

