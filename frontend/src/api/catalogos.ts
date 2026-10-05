import type { SalonId } from '../ds';
import { api } from './cliente';

/**
 * Catálogos de configuración (UI-06). Los listados traen también lo dado de baja (`activo: false`):
 * cada pantalla decide qué ofrecer. Las horas llegan como «HH:mm:ss».
 */

export interface Salon {
  id: number;
  codigo: SalonId;
  nombre: string;
  capacidad: number | null;
  activo: boolean;
}

export interface Turno {
  id: number;
  codigo: string;
  nombre: string;
  horaInicio: string;
  horaFin: string;
  cruzaMedianoche: boolean;
}

export interface TipoEvento {
  id: number;
  nombre: string;
  usaSegmentos: boolean;
  activo: boolean;
}

export interface Segmento {
  id: number;
  nombre: string;
  activo: boolean;
}

export interface Categoria {
  id: number;
  nombre: string;
  orden: number;
  visibleEnCocina: boolean;
  requeridaParaConfirmar: boolean;
  /** Categoría de bebida: sus cambios en un evento confirmado le llegan a Compras. */
  avisaACompras: boolean;
  activo: boolean;
}

export type AmbitoMotivo = 'CANCELACION' | 'REPROGRAMACION' | 'BLOQUEO' | 'AJUSTE';

export const AMBITOS: Record<AmbitoMotivo, string> = {
  CANCELACION: 'Cancelación',
  REPROGRAMACION: 'Reprogramación',
  BLOQUEO: 'Bloqueo',
  AJUSTE: 'Ajuste de stock',
};

export interface Motivo {
  id: number;
  ambito: AmbitoMotivo;
  nombre: string;
  activo: boolean;
}

export type ClaveParametro = 'HORIZONTE_COCINA_DIAS' | 'MINUTOS_EXPIRACION_SESION' | 'MINUTOS_AVISO_EXPIRACION';

export interface Parametro {
  clave: ClaveParametro;
  valor: string;
  descripcion: string | null;
  minimo: number | null;
  maximo: number | null;
}

type SinId<T> = Omit<T, 'id'>;

export const catalogos = {
  salones: () => api<Salon[]>('/salones'),
  actualizarSalon: (id: number, datos: Pick<Salon, 'nombre' | 'capacidad' | 'activo'>) =>
    api<Salon>(`/salones/${id}`, { metodo: 'PUT', cuerpo: datos }),

  turnos: () => api<Turno[]>('/turnos'),
  actualizarTurno: (id: number, datos: Pick<Turno, 'nombre' | 'horaInicio' | 'horaFin'>) =>
    api<Turno>(`/turnos/${id}`, { metodo: 'PUT', cuerpo: datos }),

  tiposEvento: () => api<TipoEvento[]>('/tipos-evento'),
  crearTipoEvento: (datos: Omit<SinId<TipoEvento>, 'activo'>) => api<TipoEvento>('/tipos-evento', { metodo: 'POST', cuerpo: datos }),
  actualizarTipoEvento: (id: number, datos: SinId<TipoEvento>) => api<TipoEvento>(`/tipos-evento/${id}`, { metodo: 'PUT', cuerpo: datos }),

  segmentos: () => api<Segmento[]>('/segmentos-asistencia'),
  crearSegmento: (datos: Omit<SinId<Segmento>, 'activo'>) => api<Segmento>('/segmentos-asistencia', { metodo: 'POST', cuerpo: datos }),
  actualizarSegmento: (id: number, datos: SinId<Segmento>) => api<Segmento>(`/segmentos-asistencia/${id}`, { metodo: 'PUT', cuerpo: datos }),

  categorias: () => api<Categoria[]>('/categorias-servicio'),
  crearCategoria: (datos: Omit<SinId<Categoria>, 'activo'>) => api<Categoria>('/categorias-servicio', { metodo: 'POST', cuerpo: datos }),
  actualizarCategoria: (id: number, datos: SinId<Categoria>) => api<Categoria>(`/categorias-servicio/${id}`, { metodo: 'PUT', cuerpo: datos }),

  motivos: () => api<Motivo[]>('/motivos'),
  crearMotivo: (datos: Pick<Motivo, 'ambito' | 'nombre'>) => api<Motivo>('/motivos', { metodo: 'POST', cuerpo: datos }),
  actualizarMotivo: (id: number, datos: Pick<Motivo, 'nombre' | 'activo'>) => api<Motivo>(`/motivos/${id}`, { metodo: 'PUT', cuerpo: datos }),

  parametros: () => api<Parametro[]>('/parametros'),
  actualizarParametro: (clave: ClaveParametro, valor: string) =>
    api<Parametro>(`/parametros/${clave}`, { metodo: 'PUT', cuerpo: { valor } }),
};

/** «20:00:00» → «20:00». */
export function hora(valor: string): string {
  return valor.slice(0, 5);
}
