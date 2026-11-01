import { api } from './cliente';

export interface ItemDeBloqueo {
  id: number;
  nombre: string;
}

/**
 * Un bloqueo activo (UI-21): el rectángulo salones × fechas × turnos que saca de la venta.
 * Las fechas son de calendario («2026-12-25»).
 */
export interface Bloqueo {
  id: number;
  motivo: ItemDeBloqueo;
  detalle: string | null;
  salones: ItemDeBloqueo[];
  desde: string;
  hasta: string;
  turnos: ItemDeBloqueo[];
  cantidadUnidades: number;
  registradoPorId: number;
  registradoPor: string;
  fechaRegistro: string;
}

export interface PedidoDeBloqueo {
  salonIds: number[];
  desde: string;
  /** Vacío: un solo día. */
  hasta: string | null;
  turnoIds: number[];
  motivoId: number;
  detalle: string | null;
}

/** Hasta cuántos días admite un bloqueo (el backend rechaza más). */
export const MAXIMO_DIAS_DE_BLOQUEO = 180;

export const bloqueos = {
  listar: () => api<Bloqueo[]>('/bloqueos'),
  registrar: (pedido: PedidoDeBloqueo) => api<Bloqueo>('/bloqueos', { metodo: 'POST', cuerpo: pedido }),
  desbloquear: (id: number) => api<void>(`/bloqueos/${id}/desbloqueo`, { metodo: 'POST' }),
};
