import { api } from './cliente';

export type TipoNotificacion =
  | 'EVENTO_NUEVO' | 'SENA' | 'CONTRATO' | 'CONFIRMACION' | 'MODIFICACION' | 'REPROGRAMACION' | 'CANCELACION'
  | 'PLANNER_ASIGNADA' | 'ALERTA_STOCK';

export interface Notificacion {
  id: number;
  tipo: TipoNotificacion;
  mensaje: string;
  /** El aviso lleva a la ficha de ese evento. */
  eventoId: number | null;
  fechaHora: string;
  leida: boolean;
}

export interface PaginaNotificaciones {
  notificaciones: Notificacion[];
  pagina: number;
  hayMas: boolean;
  sinLeer: number;
}

/** Cada cuánto se actualiza el contador de la campana. */
export const INTERVALO_CONTADOR_MS = 60_000;

/** La lista avisa cuando cambia la cantidad sin leer, así la campana no espera al próximo minuto. */
const suscriptores = new Set<(cantidad: number) => void>();

export function suscribirSinLeer(fn: (cantidad: number) => void): () => void {
  suscriptores.add(fn);
  return () => {
    suscriptores.delete(fn);
  };
}

export function avisarSinLeer(cantidad: number) {
  suscriptores.forEach((fn) => fn(cantidad));
}

export const notificaciones = {
  listar: (soloSinLeer: boolean, pagina = 0) =>
    api<PaginaNotificaciones>(`/notificaciones?soloSinLeer=${soloSinLeer}&pagina=${pagina}`),
  sinLeer: () => api<{ cantidad: number }>('/notificaciones/sin-leer'),
  marcarLeida: (id: number) => api<void>(`/notificaciones/${id}/lectura`, { metodo: 'POST' }),
  marcarTodasLeidas: () => api<void>('/notificaciones/lectura', { metodo: 'POST' }),
};
