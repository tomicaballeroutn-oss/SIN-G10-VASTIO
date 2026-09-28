import type { EventStatus, IconName, SalonId, StockStatus } from './tipos';

/** Chip fijo de cada estado: palabra, ícono y tono. Ningún estado se comunica solo con color. */
export const ESTADOS: Record<EventStatus | StockStatus, { label: string; icon: IconName; tone: string }> = {
  disponible: { label: 'Disponible', icon: 'circle-dashed', tone: 'outline' },
  prereserva: { label: 'Pre-reserva', icon: 'hourglass', tone: 'warning' },
  senado: { label: 'Señado', icon: 'banknote', tone: 'info' },
  confirmado: { label: 'Confirmado', icon: 'circle-check', tone: 'success' },
  realizado: { label: 'Realizado', icon: 'badge-check', tone: 'solid' },
  cancelado: { label: 'Cancelado', icon: 'circle-x', tone: 'danger' },
  bloqueado: { label: 'Bloqueado', icon: 'lock', tone: 'muted' },
  ok: { label: 'En stock', icon: 'circle-check', tone: 'success' },
  bajo: { label: 'Stock bajo', icon: 'triangle-alert', tone: 'warning' },
  'sin-stock': { label: 'Sin stock', icon: 'circle-x', tone: 'danger' },
};

export const SALONES: Record<SalonId, string> = {
  avril: 'Avril',
  club: 'Club de Campo',
  'santa-barbara': 'Santa Bárbara',
};

/** Orden fijo de la agenda. */
export const ORDEN_SALONES: SalonId[] = ['avril', 'club', 'santa-barbara'];
