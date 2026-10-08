# Perfiles y permisos

Un usuario puede tener **más de un perfil** (`usuario_rol`); sus permisos son la unión. La autorización **siempre** se verifica en el backend.

## Perfiles

| Código | Perfil | Quiénes | Dispositivo principal |
|---|---|---|---|
| `DIRECCION` | Dirección | Propietario e hijo | PC y celular |
| `COORDINACION` | Coordinación comercial | Meli | PC |
| `ADMINISTRACION` | Administración | Gabi, Ayelén | PC |
| `VENDEDORA` | Vendedora | 2 | PC y celular |
| `PLANNER` | Planner | 2 | Celular y PC |
| `COMPRAS` | Encargado de compras | Nadir | Celular y PC |
| `BARRA` | Encargada de barra | 2 | Celular, de noche |
| `COCINA` | Cocina | Jefes de cocina | Celular o tableta |

**Dirección y Coordinación tienen acceso total**, incluida la administración de usuarios. La tabla siguiente detalla al resto.

## Matriz historia × perfil

E = ejecuta · P = ejecuta solo sobre sus propios eventos (vendedora: los que vendió; planner: los asignados) · C = consulta · — = sin acceso

| Historia | ADM | VEND | PLAN | COMP | BARRA | COCINA |
|---|---|---|---|---|---|---|
| Iniciar sesión / Mi cuenta | E | E | E | E | E | E |
| Administrar usuarios | — | — | — | — | — | — |
| Configurar parámetros | E | — | — | — | — | — |
| Consultar agenda | C | C | C | C | — | — |
| Registrar bloqueo de unidades | E | — | — | — | — | — |
| Registrar pre-reserva | — | E | — | — | — | — |
| Liberar pre-reserva | — | P | — | — | — | — |
| Registrar / modificar evento | — | P | P | — | — | — |
| Consultar evento (ficha e historial) | C | P | C | C | — | — |
| Registrar seña | — | P | — | — | — | — |
| Registrar firma de contrato | — | — | — | — | — | — |
| Registrar cantidad de invitados | — | P | P | — | — | — |
| Registrar servicios contratados | — | P | P | — | — | — |
| Asignar planner | — | — | — | — | — | — |
| Confirmar evento | — | — | P | — | — | — |
| Reprogramar evento | — | P | — | — | — | — |
| Cancelar evento | — | — | — | — | — | — |
| Asistencia por segmento (prevista y real) | — | — | P | — | — | — |
| Consultar vista de cocina | — | — | C | C | — | C |
| Notificaciones | E | E | E | E | E | E |
| Reporte de ocupación | C | — | — | — | — | — |
| Catálogo de bebidas y proveedores (ABM) | E | — | — | E | — | — |
| Configurar ubicaciones de stock | E | — | — | — | — | — |
| Carga inicial / ingreso de bebida | E | — | — | E | — | — |
| Consultar stock | C | — | — | C | Solo su barra | — |
| Orden de preparación | C | — | — | E | — | — |
| Salida a barra, retiro adicional, devolución | — | — | — | E | E | — |
| Cierre de evento | — | — | — | E | E | — |
| Ajuste de stock (recuento y rotura) | E | — | — | E | — | — |
| Movimientos de stock | C | — | — | C | — | — |
| Consumo por evento | C | — | — | C | — | — |
| Estadística de consumo | C | — | — | C | — | — |

## Reglas especiales

- **Importe de la seña** (el único dato económico: el sistema no maneja precios ni costos): Dirección, Coordinación y Administración lo ven siempre; la vendedora solo el de sus eventos; el resto nada. El backend no lo serializa para quien no corresponde (no alcanza con ocultarlo en la pantalla).
- **Vendedora:** nada del histórico, nada de eventos de otras vendedoras en detalle (en la agenda ve la unidad ocupada y quién la tiene, no los datos del evento).
- **Planner:** ve toda la agenda, incluidos los eventos de la otra planner.
- **Operación a ciegas (barra):** ve solo el saldo de su barra (las barras de los salones con evento en la jornada; las encargadas son fijas y no se asignan en el sistema); registra retiros del depósito sin ver su saldo; si retira más que el saldo teórico, el sistema lo **acepta**, lo registra y alerta a Compras y Administración; al cerrar declara lo que devuelve sin ver lo esperado.
- **Cocina:** solo la vista de cocina, dentro del horizonte del parámetro `HORIZONTE_COCINA_DIAS`, sin importes ni observaciones internas.

## Supuestos a validar

- Que la vendedora solo pueda reprogramar y registrar la seña de **sus** eventos (el equipo definió «vendedora o coordinación»).
- La excepción de la vendedora a RNF-SEG-04 todavía no figura en el Plan de Proyecto.
