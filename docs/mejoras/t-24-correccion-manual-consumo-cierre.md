# T-24 — Textos corregidos para el Manual de Usuario V1

Pendiente de incorporar al documento original. Entre los archivos locales disponibles se encontró únicamente el PDF del manual. Los siguientes bloques conservan los textos solicitados para actualizar sus apartados 5.11 y 8.5.

En el PDF disponible, el apartado 5.11 se titula «Consultar el consumo de bebida de un evento» y el 8.5, «Cerrar el evento». Al incorporar estos bloques al original, revisar los títulos y su correspondencia con el índice.

## Sección 5.11 — Cierre del evento

El cierre de un evento se realiza una vez finalizada la jornada, cuando el evento se encuentra en estado Realizado y se registraron las devoluciones de todas las barras.

Al confirmar cada devolución, el sistema calcula el consumo real de las bebidas, registra los movimientos de stock correspondientes y conserva el remanente disponible para los siguientes eventos.

Durante el cierre, Vastio verifica que no existan devoluciones pendientes, consolida los consumos registrados y guarda el resultado definitivo del evento.

El cierre no vuelve a descontar las bebidas consumidas de las existencias.

Una vez confirmado, el evento pasa al estado Cerrado y la orden de preparación correspondiente también se cierra, si existe.

## Sección 8.5 — Cierre y consumo de bebidas

El consumo real de bebidas se registra cuando se confirma la devolución de cada barra.

La persona responsable indica las cantidades de bebidas cerradas que regresan al depósito y las que permanecen en la barra como remanente.

El sistema calcula

**Consumo real = Entregado − Devuelto − Remanente.**

A partir de ese resultado, registra los movimientos de consumo y actualiza las existencias. La entrega queda cerrada y no permite nuevos retiros.

Cuando todas las barras registraron sus devoluciones y el evento está Realizado, puede ejecutarse el cierre.

El cierre reúne los consumos de las diferentes barras, guarda el consolidado definitivo y cambia el estado del evento a Cerrado, sin volver a generar movimientos de consumo ni descontar stock.
