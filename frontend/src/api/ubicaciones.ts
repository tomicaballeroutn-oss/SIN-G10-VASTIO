import type { SalonId } from '../ds';
import { api } from './cliente';

/** Depósito madre (uno solo activo), depósito de transición o barra. */
export type TipoUbicacion = 'DEPOSITO' | 'TRANSICION' | 'BARRA';

export const NOMBRE_DE_TIPO: Record<TipoUbicacion, string> = {
  DEPOSITO: 'Depósito madre',
  TRANSICION: 'Depósito de transición',
  BARRA: 'Barra',
};

export interface Ubicacion {
  id: number;
  nombre: string;
  tipo: TipoUbicacion;
  /** En barras (siempre) y transiciones (opcional). */
  salon: { id: number; codigo: SalonId; nombre: string } | null;
  /** Solo barras: el depósito madre o la transición desde donde se abastece. */
  abastecimiento: { id: number; nombre: string; tipo: TipoUbicacion } | null;
  /** Solo barras abastecidas por una transición. */
  permiteRetiroDirecto: boolean;
  activo: boolean;
}

/** El tipo va en el alta y no cambia después. Sin abastecimiento, la barra se abastece del depósito madre. */
export interface DatosUbicacion {
  nombre: string;
  tipo?: TipoUbicacion;
  salonId: number | null;
  abastecimientoId: number | null;
  permiteRetiroDirecto: boolean;
}

export const ubicaciones = {
  listar: () => api<Ubicacion[]>('/ubicaciones'),
  crear: (datos: DatosUbicacion) => api<Ubicacion>('/ubicaciones', { metodo: 'POST', cuerpo: datos }),
  modificar: (id: number, datos: DatosUbicacion) => api<Ubicacion>(`/ubicaciones/${id}`, { metodo: 'PUT', cuerpo: datos }),
  darDeBaja: (id: number) => api<Ubicacion>(`/ubicaciones/${id}/baja`, { metodo: 'POST' }),
  reactivar: (id: number) => api<Ubicacion>(`/ubicaciones/${id}/reactivacion`, { metodo: 'POST' }),
};
