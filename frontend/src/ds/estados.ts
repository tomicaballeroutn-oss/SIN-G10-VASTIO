import type { EventStatus, IconName, SalonId, StockStatus } from './tipos';

/** Chip fijo de cada estado: palabra, ícono y tono. Ningún estado se comunica solo con color. */
export const ESTADOS: Record<EventStatus | StockStatus, { label: string; icon: IconName; tone: string }> = {
  disponible: { label: 'Disponible', icon: 'circle-dashed', tone: 'outline' },
  prereserva: { label: 'Pre-reserva', icon: 'hourglass', tone: 'warning' },
  senado: { label: 'Señado', icon: 'banknote', tone: 'info' },
  contratado: { label: 'Contratado', icon: 'file-check', tone: 'brand' },
  confirmado: { label: 'Confirmado', icon: 'circle-check', tone: 'success' },
  'en-curso': { label: 'En curso', icon: 'circle-play', tone: 'success-solid' },
  realizado: { label: 'Realizado', icon: 'badge-check', tone: 'solid' },
  cerrado: { label: 'Cerrado', icon: 'archive', tone: 'ink' },
  liberada: { label: 'Liberada', icon: 'lock-open', tone: 'outline' },
  cancelado: { label: 'Cancelado', icon: 'circle-x', tone: 'danger' },
  bloqueado: { label: 'Bloqueado', icon: 'lock', tone: 'muted' },
  ok: { label: 'En stock', icon: 'circle-check', tone: 'success' },
  bajo: { label: 'Stock bajo', icon: 'triangle-alert', tone: 'warning' },
  'sin-stock': { label: 'Sin stock', icon: 'circle-x', tone: 'danger' },
  // Sin culpa: el saldo es teórico y se corrige registrando lo que falta.
  negativo: { label: 'Falta registrar un movimiento', icon: 'circle-alert', tone: 'warning' },
};

/** Código de `evento.estado` en la base → estado del sistema de diseño. */
export const ESTADO_POR_CODIGO = {
  PRE_RESERVA: 'prereserva',
  SENADO: 'senado',
  CONTRATADO: 'contratado',
  CONFIRMADO: 'confirmado',
  EN_CURSO: 'en-curso',
  REALIZADO: 'realizado',
  CERRADO: 'cerrado',
  LIBERADA: 'liberada',
  CANCELADO: 'cancelado',
} as const satisfies Record<string, EventStatus>;

/** Los nueve estados del evento, en el orden de la máquina de estados. */
export const ESTADOS_DEL_EVENTO: EventStatus[] = Object.values(ESTADO_POR_CODIGO);

export const SALONES: Record<SalonId, string> = {
  avril: 'Avril',
  club: 'Club de Campo',
  'santa-barbara': 'Santa Bárbara',
};

/** Orden fijo de la agenda. */
export const ORDEN_SALONES: SalonId[] = ['avril', 'club', 'santa-barbara'];
