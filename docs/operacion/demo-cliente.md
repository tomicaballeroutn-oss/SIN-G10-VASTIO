# Demo para el cliente — guion

Recorrido de punta a punta: un evento desde que se vende hasta que se cierra, y la bebida desde que entra al depósito hasta que se consume. Cada paso dice con qué usuario entrar y qué mostrar. Duración estimada: 60 a 75 minutos.

## Antes de la reunión (el mismo día)

1. Abrí Docker Desktop y cerrá el backend de desarrollo si está corriendo (usa el puerto 8080).
2. En PowerShell, desde la raíz del repo: `.\scripts\demo-cliente.ps1`. Se abren dos ventanas, backend y frontend.
3. Esperá en la del backend el mensaje «Escenario completo de demostración cargado» y entrá a http://localhost:5173.
4. Todos los usuarios de ejemplo usan la contraseña `VASTIO_DEMO_CONTRASENA` del `.env`. Dirección es el usuario inicial (`direccion`, con `VASTIO_ADMIN_CONTRASENA`).

Las fechas son relativas al día de la carga, por eso conviene correrlo el mismo día: el evento de hoy queda **En curso** para poder operar la barra. Si algo se rompe durante la prueba, volvé a correr el script y la base queda como nueva. Tu base de desarrollo no se toca: la demo usa otra base, `vastio-demo`.

Tip: usá dos navegadores, o una ventana normal y otra de incógnito, para cambiar de perfil sin cerrar sesión.

| Usuario | Perfil |
|---|---|
| `direccion` | Dirección (ve todo) |
| `melina.sifon` | Coordinación |
| `gabriela.paz` | Administración |
| `lucia.ferreyra`, `sofia.mendez` | Vendedoras |
| `ana.sosa`, `carla.nunez` | Planners |
| `nicolas.herrera` | Compras |
| `rocio.blanco` | Encargada de barra |
| `julian.torres` | Cocina |

## Qué hay cargado

- **Historia:** 12 eventos cerrados en los últimos cinco meses (casamientos, quinces, dos egresados, un corporativo y un cumpleaños), con entregas a barra, devoluciones y consumo. También ingresos al depósito, un recuento y una rotura.
- **Ayer:** «Egresados Escuela Normal Superior» (Santa Bárbara), **Realizado**. Ya se hizo la devolución; falta la asistencia real y el cierre.
- **Hoy:** «Quince de Martina Ortiz» (Avril, **mediodía, 12 a 18**). A las 12 el sistema lo pasa solo a **En curso**. Tiene la orden confirmada y la bebida ya preparada en la Transición Avril.
- **Cargar la base el mismo día de la presentación** (las fechas son relativas a hoy). Con la base de desarrollo alcanza con reiniciarla (`docker compose down -v; docker compose up -d db`) y levantar el backend: el escenario completo se carga por defecto.
- **Próximos días:**
  - Órdenes en tres estados: «Agustina y Lucas» (en 6 días, Modificada), «Quince de Emma Torres» (en 9, Confirmada) y el casamiento de Camila Ruiz (en 31, Sugerida).
  - «Egresados Colegio Nacional» (en 13 días, Contratado): falta la asistencia prevista para poder confirmarlo.
  - Una seña en dólares: «Jornada anual Mercado Libre».
  - Una cancelación («Cumple 18 de Mateo»), una pre-reserva liberada («Quince de Sol Díaz») y una reprogramación («Brenda y Nicolás»).
  - Dos bloqueos: Santa Bárbara en 18 días y el Club en 25.
- **Stock:** Espumante, Gin y Vodka quedan bajos en el depósito. La Barra Avril tiene un remanente del quince anterior.

## Recorrido

### 1. Entrada y perfiles (3 min) — `direccion`

- Iniciar sesión e **Inicio**: el tablero con lo pendiente y los atajos.
- El menú cambia según el perfil. Para mostrarlo, más adelante se entra como Barra y como Cocina y se ve cuánto menos tienen.
- **Mi cuenta:** cambiar la contraseña y las preferencias, como el tema claro u oscuro.

### 2. Agenda (5 min) — `melina.sifon`

- **Agenda**: el mes con los tres salones por color. El estado se ve por relleno, ícono y palabra.
- Tocar un día para ver el detalle por salón y abrir un evento.
- Mostrar en el calendario los **bloqueos** (Santa Bárbara en 18 días, el Club en 25).
- **Eventos**: la lista con los filtros de búsqueda.

### 3. Un evento de punta a punta (12 min)

Elegí una fecha libre, por ejemplo un sábado del mes que viene en Avril a la noche.

1. `lucia.ferreyra` → **Agenda** → registrar la **pre-reserva** con un cliente nuevo. Mostrar que la fecha queda tomada: no se puede vender dos veces la misma.
2. Misma usuaria: **Registrar seña**. Mostrar que se puede elegir pesos o dólares y descargar el **comprobante en PDF**.
3. `melina.sifon`: **Registrar firma** con el contrato adjunto y **Asignar planner** (Ana Sosa).
4. `ana.sosa`: **Cantidad de invitados** (marcarla definitiva) y pestaña **Servicios**: menú, menús especiales con cantidades (celíacos, vegetarianos) y tipo de barra.
5. `ana.sosa`: **Confirmar evento**. El diálogo muestra los requisitos cumplidos.
6. Mostrar qué pasa al confirmar:
   - Llegan **avisos** a Compras y Cocina.
   - Se genera sola la **orden de preparación**: pestaña «Orden de preparación» de la ficha.
7. Pestaña **Historial**: quién hizo cada cosa y cuándo.

Casos ya cargados para mostrar sin hacerlos en vivo:

- **Cancelación con motivo:** «Cumple 18 de Mateo».
- **Pre-reserva liberada:** «Quince de Sol Díaz». No es una cancelación.
- **Reprogramación:** «Brenda y Nicolás». La ficha muestra la fecha original y el historial.
- **Seña en USD:** «Jornada anual Mercado Libre».

### 4. Egresados: asistencia por segmento (5 min) — `ana.sosa`

- Abrir «Egresados Colegio Nacional» (Contratado). Explicar que los invitados son los comensales de la cena y que la fiesta posterior tiene otro público.
- Pestaña **Asistencia** → **Registrar asistencia prevista** (entradas anticipadas y venta en puerta). Mostrar el total previsto.
- **Confirmar evento**: sin la asistencia prevista no deja. Al confirmar, la orden sale de las **proporciones de consumo**, porque todavía hay pocos egresados cerrados.

### 5. Bloqueos (2 min) — `melina.sifon`

- **Bloqueos**: los activos, con motivo, quién y cuándo. Crear uno, por ejemplo por mantenimiento, y mostrar que esa fecha ya no se puede vender.

### 6. Cocina (3 min) — `julian.torres`

- Solo ve la **Vista de cocina**: los eventos de los próximos 15 días con menú, menús especiales y cantidades.
- No ve importes, observaciones internas ni datos del cliente.

### 7. Avisos (2 min)

- La campana y **Notificaciones**: cada tipo tiene su color, y al tocar un aviso se abre la ficha en la pestaña que corresponde.

### 8. Bebidas: catálogo y depósito (8 min) — `nicolas.herrera`

- **Catálogo**: bebidas con presentación, unidad (caja o botella) y códigos de barras.
- **Parámetros → Ubicaciones de stock** (con `gabriela.paz`): el depósito principal, la Transición Avril y las barras.
- **Existencias**: los saldos en cajas y botellas, con Espumante, Gin y Vodka **bajo el mínimo**.
- **Ingreso de mercadería**: cargar un remito en vivo, con el lector de código de barras o a mano. Volver a Existencias y ver el saldo actualizado.
- Desde Existencias, **Ajuste**: un recuento (faltante) o una rotura, siempre con motivo.

### 9. Orden de preparación (8 min) — `nicolas.herrera`

- **Órdenes de preparación**: los eventos confirmados, con el estado de su orden (Sugerida, Modificada, Confirmada) y el aviso «Cambiaron los asistentes» cuando corresponde.
- Abrir «Agustina y Lucas»:
  - De dónde sale la sugerencia: el consumo de 4 casamientos cerrados, con 10 % de margen y en cajas completas.
  - La columna **Comprometido por otros eventos**.
- **Ajustar cantidades** en cajas y botellas sueltas, **agregar una bebida**, **Confirmar orden**. Confirmada ya no se edita; mostrar **Reabrir orden**.
- **Vista para imprimir**.
- Con `gabriela.paz`, en **Parámetros**:
  - **Proporciones de consumo** (por tipo de evento).
  - El **margen** de la orden, en «Generales».

### 10. La noche del evento (10 min)

1. `nicolas.herrera` → ficha de «Quince de Martina Ortiz» → pestaña **Preparación**. La bebida ya pasó del depósito a la Transición Avril, siguiendo la orden («Según orden»).
2. `rocio.blanco` (Barra): **Barra** muestra solo el evento de la jornada.
   1. **Salida a barra**: retirar bebida de la transición, con el lector o a mano.
   2. **Retiro adicional** a mitad de la noche.
   3. **Devolución** al final: declara lo que vuelve **a ciegas**, sin ver lo esperado ni el saldo del depósito.
   4. Si se retira más de lo que hay, se acepta igual y llega un aviso a Compras y a Administración.
3. El cierre de este evento solo se puede hacer después de las 6 de la mañana, cuando pasa a Realizado. Por eso el cierre se muestra con el de ayer (paso 11).

### 11. Cierre y asistencia real (6 min)

1. `carla.nunez`: tiene el aviso «Cargá la asistencia real de cierre». Abre «Egresados Escuela Normal Superior» → pestaña **Asistencia** → **Registrar asistencia real**, con lo de la plataforma de acceso y la venta en puerta.
2. `rocio.blanco` o `nicolas.herrera` → **Barra** → **Cierre** del evento de ayer:
   - Muestra entregado, devuelto, remanente y consumido por bebida.
   - Muestra el **consumo por asistente**, calculado sobre la asistencia real.
   - Al cerrar, el consumo queda fijado y la orden queda Cerrada.
3. En la ficha, pestaña **Consumo de bebida**: el consumo congelado.

### 12. Control y estadísticas (8 min) — `melina.sifon` o `nicolas.herrera`

- **Movimientos**: el historial filtrable por ubicación, bebida, evento, tipo y fechas.
  - Cada tarjeta es una operación (ingreso, preparación, salida, devolución, ajuste) con quién y cuándo.
  - Nada se edita: se corrige con un ajuste.
- **Estadísticas de consumo**: cinco meses de eventos cerrados por bebida y por tipo de evento.
  - El promedio por evento y por asistente.
  - En los egresados se usa la asistencia real.
- **Reportes → Ocupación** (Dirección o Coordinación): ocupación por salón y temporada, para imprimir.

### 13. Administración (3 min) — `direccion`

- **Usuarios y perfiles**: alta, perfiles combinables y baja (lo que la persona registró se conserva).
- **Parámetros**: salones, turnos, tipos de evento, segmentos de asistencia, categorías de servicio, motivos y generales.
- **Pendientes de Google Calendar**: la publicación de los eventos en el calendario. Solo funciona si el ambiente tiene configurada la cuenta de Google; en la demo local, mostrarlo solo como pantalla.

## Cierre de la reunión

Repasar el objetivo del negocio: **saber qué consume cada evento**. Responder con datos de la demo:

- qué se entregó,
- cuánto se consumió,
- cuánto por asistente.

Anotar los pedidos del cliente para el próximo sprint. Queda pendiente, por ejemplo, el perfil «Puerta» para la asistencia real.
