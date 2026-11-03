# Contexto del negocio

Resumen para quien desarrolla. La fuente completa está en el Estudio Inicial, el Plan de Proyecto, el Seguimiento y la Definición del Producto (Google Drive del equipo).

## El cliente

Complejo privado de salones de eventos en Córdoba con unos 20 años de operación. Tres salones, en este orden fijo: **Avril, Club de Campo, Santa Bárbara**. Entre 100 y 150 eventos por año; habitual 150–170 invitados, rango 100 a 1.000, picos de 2.000 en un evento y 4.500 simultáneos en el complejo. Temporada alta de octubre a diciembre; en diciembre puede haber eventos todas las noches. Tipos: casamientos, quinces (el más frecuente), corporativos, egresados, cumpleaños.

**Unidad comercializable** = salón × fecha × turno (mediodía o noche). Hasta seis por día. La jornada de noche va de 20:00 a 06:00 (corte de música 05:00).

## El problema

No falta digitalización: falta un **modelo de datos común**. Hoy hay un Google Calendar compartido (se carga después de concretar la venta), planillas de compras y existencias, un cuaderno de barra en papel. Nada comparte un identificador de evento, así que:

- La fecha en negociación no está representada y dos vendedoras pueden ofrecer la misma.
- Nadie sabe cuánto consumió cada evento: la encargada de barra retira cajas, lo anota en papel y devuelve el sobrante sin verificación.

## El objetivo

**Consumo, no vigilancia.** Palabras del dueño: todo negocio que quiere datos los quiere para conocer su costo. El sistema da las cantidades; el costo lo calcula cada área con sus remitos de compra, porque el sistema no maneja precios ni dinero (la única excepción es el importe de la seña). Corolarios de diseño:

- Declarar una rotura o un faltante tiene que ser simple y sin castigo. Se miden tendencias, no incidentes.
- El trabajador no debe hacer trabajo extra.
- El lenguaje del sistema es de atribución de consumo: «consumo real», «se corrige con un ajuste». Nunca «faltante» ni «sospechoso».

Criterio medible: para cualquier evento realizado con el sistema, responder qué se entregó a la barra, cuánto se consumió y cuánto por asistente.

## Circuito de la bebida

Compra masiva una o dos veces al año. El encargado de compras (Nadir) recibe y cuenta contra remito. El depósito está a unos 30 m del salón. La encargada de barra retira **por cajas**, con retiros adicionales durante la noche. El sobrante de eventos grandes (sobre todo gaseosas) puede quedar en la barra para el día siguiente.

Ecuación de conciliación por evento y artículo:

`Consumo = Stock inicial de la barra + Entregado − Devuelto al depósito − Remanente en barra`

- Granularidad por caja en la operación; en la base todo se guarda en **botellas**. La caja es solo una forma de mostrar y cargar: cada artículo sabe cuántas botellas trae.
- Tres tipos de ubicación: depósito madre, depósito de transición y barra (una por salón; Avril, hasta dos). Las divisiones internas del depósito no se modelan.
- El código de barras del producto (leído con la cámara del celular) **identifica el artículo**, no cuenta unidades. La cantidad se tipea. Siempre existe carga manual.
- No hay encargado de depósito y no lo va a haber.
- El depósito de transición ya se probó y colapsó en diciembre: las ubicaciones son configurables y el circuito funciona con o sin él.
- La sugerencia de la orden de preparación es sugerencia: el responsable la ajusta.
- El hielo queda fuera.

## Egresados

Entre noviembre y diciembre: cena hasta las 00:00 y fiesta de 00:00 a 05:00 con **otro público**, entrada más barata y barra libre igual, más venta en puerta solo en efectivo. Es **un evento, no dos**: un slot, con segmentos de asistencia (comensales, anticipadas, puerta). La bebida se dimensiona sobre la suma.

## Fuera de alcance

Precios y costos (salvo el importe de la seña), bebidas sin alcohol (por ahora), hielo, vajilla y cristalería, decoración, facturación y contabilidad, pagos reales, integración en línea con la plataforma de tickets, gestión gastronómica, personal, consumo por unidad servida, canal público para el cliente. Notificaciones por WhatsApp: expansión futura.

## Piloto

Bodas de fin de octubre, en paralelo con las planillas actuales (no se abandonan durante la prueba).
