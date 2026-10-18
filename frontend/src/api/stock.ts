import type { StockStatus } from '../ds';
import { api } from './cliente';

export type EstadoStock = 'NEGATIVO' | 'SIN_STOCK' | 'BAJO' | 'OK';

export const ESTADO_STOCK: Record<EstadoStock, StockStatus> = {
  NEGATIVO: 'negativo',
  SIN_STOCK: 'sin-stock',
  BAJO: 'bajo',
  OK: 'ok',
};

/** Para la encargada de barra, con el evento de la jornada en su salón. */
export interface UbicacionConsultable {
  ubicacion: { id: number; nombre: string; tipo: 'DEPOSITO' | 'TRANSICION' | 'BARRA' };
  evento?: { id: number; codigo: string; nombre: string };
}

/** Cantidades en botellas. A la barra no le llegan `estado` ni `stockMinimo`. */
export interface RenglonStock {
  bebida: { id: number; nombre: string; presentacion: string; tipo: string; unidad: string; unidadesPorBulto: number; activo: boolean };
  cantidad: number;
  estado?: EstadoStock;
  stockMinimo?: number | null;
}

/** Sin ubicación: el total del complejo. */
export interface Existencias {
  ubicacion: { id: number; nombre: string } | null;
  renglones: RenglonStock[];
}

/** La barra la mira durante la noche: se refresca sola. */
export const INTERVALO_STOCK_MS = 60_000;

export const stock = {
  ubicaciones: () => api<UbicacionConsultable[]>('/stock/ubicaciones'),
  existencias: (ubicacionId: number | null) => api<Existencias>(ubicacionId === null ? '/stock' : `/stock?ubicacionId=${ubicacionId}`),
};
