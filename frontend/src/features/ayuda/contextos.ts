import type { AyudaContextualProps } from './AyudaContextual';

export const AYUDAS = {
  filtrosAgenda: { titulo: 'Filtros de la agenda', explicacion: 'Seleccioná salones, turnos o estados para destacar eventos. Los demás permanecen visibles, pero atenuados.', articuloId: 'filtros-estados' },
  estadosAgenda: { titulo: 'Estados de los eventos', explicacion: 'Los colores indican la etapa del evento. Las unidades bloqueadas aparecen con un candado.', articuloId: 'filtros-estados' },
  historial: { titulo: 'Historial del evento', explicacion: 'Consultá qué cambios se realizaron, quién los hizo y cuándo. El historial no se puede modificar.', articuloId: 'ficha-historial' },
  confirmacion: { titulo: 'Requisitos de confirmación', explicacion: 'Antes de confirmar, revisá los requisitos. Vastio te indica cuáles están completos y cuáles faltan.', articuloId: 'confirmacion-reprogramacion' },
  existencias: { titulo: 'Estado de las existencias', explicacion: '«Bajo» indica que se alcanzó el stock mínimo. Un saldo negativo puede señalar un movimiento pendiente.', articuloId: 'existencias' },
  devoluciones: { titulo: 'Devoluciones y remanentes', explicacion: 'Al confirmar la devolución, Vastio calcula y registra el consumo: entregado menos devuelto y remanente.', articuloId: 'devoluciones' },
  cierre: { titulo: 'Consumo real y cierre', explicacion: 'El consumo se registra al devolver las bebidas. El cierre consolida lo consumido por todas las barras, sin volver a descontar stock.', articuloId: 'cierre' },
  ocupacion: { titulo: 'Cálculo de ocupación', explicacion: 'La ocupación cuenta reservas firmes y posteriores, no pre-reservas ni eventos cancelados o liberados.', articuloId: 'ocupacion' },
} satisfies Record<string, AyudaContextualProps>;
