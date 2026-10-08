# Sprint 3 — La bebida entra al sistema

**Objetivo:** la mercadería entra al sistema y cada ubicación sabe cuánto tiene.

Este documento cubre la primera parte del sprint. Las historias de la operación del evento (al final) se planifican aparte.

## Historias

| Historia | Enunciado | Prioridad |
|---|---|---|
| Registrar / Dar de baja / Modificar / Consultar bebida | Como administración, quiero mantener un catálogo de artículos de bebida con su presentación, unidad de manipulación y códigos de barras asociados, para disponer de una nómina única de productos utilizada en el control de existencias y movimientos. | Alta |
| Configurar ubicaciones de stock | Como administración, quiero configurar el depósito madre, los depósitos de transición y las barras de cada salón, para representar las ubicaciones utilizadas por el circuito de bebidas y permitir, cuando la operación lo requiera, registrar retiros directos desde el depósito madre como mecanismo de contingencia. | Media |
| Registrar inventario inicial | Como administradora, quiero cargar el inventario inicial de cada ubicación y los datos maestros de proveedores, para completar la puesta en marcha del sistema. | Media |
| Registrar ingreso de bebida | Como encargado de compras, quiero registrar el ingreso de mercadería al depósito con detalle de bebida, tipo de bebida, cantidad y fecha de ingreso, para actualizar las existencias del depósito con trazabilidad hacia el saldo. | Alta |
| Consultar stock | Como responsable de barra, quiero consultar las existencias disponibles en la barra correspondiente a mi evento, para conocer el stock con el que puedo operar durante la jornada. | Alta |
| Registrar ajuste de stock | Como administración o encargado de compras, quiero registrar un recuento físico o declarar una rotura, para que el saldo teórico refleje lo que hay sin editar los movimientos registrados. | Media |

## Orden de trabajo

El inventario inicial va después del ingreso porque los dos usan el servicio de movimientos, que se arma con el ingreso. El ajuste se hace desde la consulta de stock.

1. Catálogo de bebidas · 2. Ubicaciones de stock · 3. Ingreso de bebida · 4. Inventario inicial y proveedores · 5. Consultar stock · 6. Ajuste de stock

Ramas: `mateo/S3-catalogo-bebidas`, `mateo/S3-ubicaciones-stock`, `mateo/S3-ingreso-bebida`, `mateo/S3-inventario-inicial`, `mateo/S3-consultar-stock`, `mateo/S3-ajuste-stock`.

## Decisiones del equipo

1. **Sin precios ni dinero.** El sistema maneja cantidades; los costos los calcula cada área con sus remitos de compra. La única excepción es el importe de la seña. La migración del catálogo quita `bebida.precio_referencia` y el precio y el costo de `consumo_evento` (todavía sin uso). Se actualizan `CLAUDE.md`, el contexto del negocio, el diccionario de datos, la máquina de estados y perfiles y permisos.
2. **Solo bebida con alcohol** en esta etapa. Tipos: Vino, Espumante, Destilado, Aperitivo y Cerveza. Se borran Gaseosa y Agua (no los usa ninguna bebida). El hielo queda afuera. Tipos de bebida y unidades de manipulación son listas fijas, sin pantalla.
3. **Cantidades.** Todo se guarda y se calcula en **botellas**. La caja (o el pack) es solo la forma de mostrar y cargar: la pantalla pide cajas y botellas sueltas, en enteros, y las convierte con `unidades_por_bulto`. La unidad «Cajón» pasa a llamarse «Caja». Los decimales quedan para las botellas abiertas del cierre.
4. **Lector de código de barras (UI-30)** con la cámara del celular, en Android y en iOS (librería `@zxing/browser`; la API nativa del navegador no está en Safari). Requiere HTTPS. Se usa en el catálogo (asociar el código) y en el ingreso (identificar el artículo). Siempre está «Elegir a mano». El código identifica el artículo; la cantidad se tipea. Va al sistema de diseño como componente, junto con el de cantidad en cajas y botellas.
5. **Bebida:** nombre, presentación (ej. «750 ml»), tipo, unidad de manipulación, unidades por bulto (1 si la unidad es Botella), stock mínimo opcional y proveedor habitual opcional. No hay dos bebidas activas con el mismo nombre y presentación, sin distinguir mayúsculas.
6. **Códigos de barras:** cero o más por bebida, cada uno con las botellas que representa (1 la botella, 6 o 12 la caja). Solo dígitos, de 8 a 14. Un código pertenece a una sola bebida.
7. **Baja de bebida:** lógica y reversible. Se permite con saldo, mostrando antes en qué ubicaciones lo tiene. Una bebida dada de baja no se ofrece para cargar, pero aparece en la consulta mientras tenga saldo. Cambiar las unidades por bulto con movimientos registrados se permite: solo cambia cómo se muestra.
8. **Ubicaciones:** tres tipos, **Depósito madre** (uno solo activo), **Depósito de transición** (salón opcional) y **Barra** (salón obligatorio). Las divisiones internas del depósito no se modelan: se quita `SECTOR` del modelo. El tipo no se cambia después del alta.
9. **Barras por salón:** una por salón; Avril, hasta dos. Queda como dato del salón (`salon.maximo_barras`), sin pantalla.
10. **Retiro directo de contingencia:** cada barra dice desde dónde se abastece (el depósito madre o un depósito de transición activo). Si es una transición, se puede habilitar «Permitir retiro directo del depósito madre». En este sprint solo se configura; lo usa «Registrar salida a barra».
11. **Baja de ubicación:** se rechaza si tiene saldo distinto de cero, si es el depósito madre o si es una transición de la que se abastece alguna barra activa.
12. **Inventario inicial:** por ubicación (cualquiera de los tres tipos). «Guardar» registra lo cargado hasta ese momento, así se puede retomar. Una bebida nueva genera un `INVENTARIO_INICIAL`; una ya cargada que cambia genera un `AJUSTE` por la diferencia con el motivo «Error de carga» y `movimiento_corregido_id`. Se puede corregir así hasta que la ubicación tenga su primer movimiento de otro tipo; después, se corrige con un recuento físico.
13. **Proveedores:** razón social, CUIT (opcional, 11 dígitos con dígito verificador válido, único), teléfono y email. Alta, modificación, baja lógica y reactivación. «Bebidas que provee» es el proveedor habitual de cada bebida (sin tabla nueva). Se cargan en el paso 2 de la carga inicial y en su propia pestaña del catálogo.
14. **Ingreso:** solo al depósito madre; la transición y la barra reciben por transferencia. Proveedor y número de remito opcionales; fecha de ingreso por defecto hoy, nunca futura. No pide precio. No se edita ni se anula: un error se corrige con un recuento físico.
15. **Consultar stock — barra:** las encargadas de barra son fijas y no se asignan en el sistema. La encargada ve las barras de los salones con un evento confirmado en la jornada actual (lo que pasa antes de la hora de fin del turno noche cuenta como el día anterior) y elige cuál mirar. Ve solo el saldo de esa barra, en cajas y botellas: sin stock mínimo, sin saldo del depósito y sin comprometido (operación a ciegas).
16. **Consultar stock — resto:** Dirección, Coordinación, Administración y Compras eligen una ubicación o «Todas» (total por bebida). En los depósitos se listan todas las bebidas activas; en las barras, solo las que tienen saldo distinto de cero. Estados: «sin stock» (≤ 0) y, en el depósito madre, «bajo» (≤ stock mínimo). Un saldo negativo se muestra como negativo con «Falta registrar un movimiento».
17. **Ajuste de stock (UI-38):** lo registran Administración y Compras, además de Dirección y Coordinación, desde la consulta de stock, sobre una bebida en una ubicación. Dos acciones:
    - **Registrar recuento:** se carga lo contado y se registra un `AJUSTE` por la diferencia con el saldo teórico. Motivo obligatorio del ámbito Ajuste; detalle obligatorio con «Otro», hasta 255 caracteres.
    - **Declarar rotura:** registra una `MERMA` (sale de la ubicación, sin destino) con el motivo «Rotura».
18. **Saldo negativo:** si un ajuste o una rotura deja el saldo negativo, se acepta y se avisa a Compras y Administración (`ALERTA_STOCK`), salvo a quien lo registró. El aviso abre la consulta de stock.
19. **Datos de demostración:** catálogo de ejemplo, proveedores e inventario inicial, para el e2e del objetivo.
20. **Fuera de esta parte:** salida a barra, retiro adicional, devolución, cierre, orden de preparación, consumo por evento, movimientos de stock (UI-37) y corrección de un movimiento puntual.

## Criterios de aceptación

### Catálogo de bebidas (UI-25)

- Dirección, Coordinación, Administración y Compras listan, buscan (por nombre o código de barras), filtran por tipo, dan de alta, modifican, dan de baja y reactivan bebidas. El resto recibe 403.
- Alta y modificación validan los campos de la decisión 5; con unidad Botella, las unidades por bulto quedan en 1. Nombre y presentación repetidos entre activas se rechazan con un mensaje que nombra la bebida existente.
- Se pueden agregar y quitar códigos con sus unidades, tipeados o leídos con la cámara. Un código que ya es de otra bebida se rechaza diciendo de cuál.
- Antes de dar de baja una bebida con saldo, la pantalla muestra las ubicaciones y cantidades. La bebida dada de baja deja de ofrecerse para cargar.
- No aparece ningún precio ni importe.

### Ubicaciones de stock (UI-26)

- Dirección, Coordinación y Administración dan de alta, modifican, dan de baja y reactivan ubicaciones. Compras y Barra solo las consultan donde las necesitan (ingreso, stock). El resto recibe 403.
- Barra exige salón activo y respeta `salon.maximo_barras` (el mensaje dice el límite). No hay un segundo depósito madre activo. Nombres únicos sin distinguir mayúsculas.
- Una barra se abastece del depósito madre o de una transición activa; con transición, se puede marcar «Permitir retiro directo del depósito madre».
- La baja se rechaza en los casos de la decisión 11, con un mensaje que dice por qué.

### Registrar ingreso de bebida (UI-29 y UI-30)

- Lo registran Dirección, Coordinación, Administración y Compras. El resto recibe 403.
- Destino: el depósito madre. Fecha no futura. Al menos un renglón con cantidad mayor a 0; una bebida no se repite (leerla de nuevo lleva a su renglón). Solo bebidas activas.
- Leer un código agrega la bebida; un código desconocido dice «Ese código no está en el catálogo» y ofrece elegir a mano.
- En una transacción: la cabecera `ingreso`, un `movimiento_stock` `INGRESO` por renglón con usuario y momento, y el saldo actualizado. La pantalla resume «Ingresan 24 cajas al Depósito Avril» antes de registrar.

### Registrar inventario inicial y proveedores (UI-28)

- Lo registran Dirección, Coordinación, Administración y Compras. El resto recibe 403.
- Por ubicación: la lista de bebidas activas con lo ya cargado; guardar registra lo nuevo y corrige lo cambiado según la decisión 12.
- Una ubicación con movimientos de otro tipo muestra su carga inicial en solo lectura y remite al recuento físico.
- Paso 2: alta, modificación, baja y reactivación de proveedores con las validaciones de la decisión 13, y marcado de las bebidas que provee.

### Consultar stock (UI-27)

- La encargada de barra solo ve las barras de la decisión 15; pedir otra ubicación devuelve 403. Si no hay eventos en la jornada, lo dice.
- La respuesta para Barra no incluye stock mínimo ni saldos de otras ubicaciones (se verifica en el backend).
- Dirección, Coordinación, Administración y Compras ven cualquier ubicación y «Todas», con los estados de la decisión 16.
- Las cantidades se muestran en cajas y botellas sueltas.

### Registrar ajuste de stock (UI-38)

- Lo registran Dirección, Coordinación, Administración y Compras. El resto recibe 403.
- Recuento: muestra el saldo teórico, pide lo contado y el motivo; si no hay diferencia, no registra nada y lo dice. Rotura: pide la cantidad (mayor a 0).
- Ninguno edita ni borra movimientos: agregan un asiento con usuario y momento, y el saldo se actualiza en la misma transacción.
- Si el saldo queda negativo, se avisa según la decisión 18.

## Pendientes del sprint (se planifican aparte)

Registrar realización de evento · Registrar salida a barra · Registrar devolución de bebida · Registrar cierre de evento · Consultar consumo de bebida por evento · Registrar la preparación del evento.
