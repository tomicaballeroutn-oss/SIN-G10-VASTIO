import { api, type Rol } from './cliente';
import type { TipoUbicacion } from './ubicaciones';

/** Consultan los movimientos de stock (docs/sprint-4.md, decisión 26). La barra no: operación a ciegas. */
export const ROLES_MOVIMIENTOS: Rol[] = ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'];

export type TipoMovimiento = 'INGRESO' | 'INVENTARIO_INICIAL' | 'PREPARACION' | 'SALIDA_BARRA' | 'RETIRO_ADICIONAL'
  | 'DEVOLUCION' | 'REMANENTE' | 'CONSUMO' | 'AJUSTE' | 'MERMA';

export interface EventoDelMovimiento {
  id: number;
  codigo: string;
  nombre: string;
  /** yyyy-mm-dd, fecha de la jornada. */
  fecha: string;
  /** Código del salón: avril, club, santa-barbara. */
  salon: string;
}

/** Un asiento de la operación, en botellas. `corrige`: el movimiento que corrige un ajuste. */
export interface Asiento {
  movimientoId: number;
  bebidaId: number;
  nombre: string;
  presentacion: string;
  unidad: string;
  unidadesPorBulto: number;
  cantidad: number;
  corrige: number | null;
}

/** Los asientos de un mismo ingreso, preparación, salida a barra o devolución. */
export interface OperacionDeStock {
  tipo: TipoMovimiento;
  fechaHora: string;
  usuario: { id: number; nombre: string };
  evento: EventoDelMovimiento | null;
  origen: { id: number; nombre: string; tipo: TipoUbicacion } | null;
  destino: { id: number; nombre: string; tipo: TipoUbicacion } | null;
  motivo: string | null;
  observacion: string | null;
  asientos: Asiento[];
}

export interface PaginaDeMovimientos {
  operaciones: OperacionDeStock[];
  hayMas: boolean;
}

/** Filtros opcionales; las fechas, yyyy-mm-dd inclusive. */
export interface FiltrosMovimientos {
  ubicacionId?: number;
  bebidaId?: number;
  eventoId?: number;
  tipo?: TipoMovimiento;
  desde?: string;
  hasta?: string;
}

export const NOMBRE_DE_MOVIMIENTO: Record<TipoMovimiento, string> = {
  INGRESO: 'Ingreso al depósito',
  INVENTARIO_INICIAL: 'Inventario inicial',
  PREPARACION: 'Preparación',
  SALIDA_BARRA: 'Entrega a barra',
  RETIRO_ADICIONAL: 'Retiro adicional',
  DEVOLUCION: 'Devolución',
  REMANENTE: 'Remanente en barra',
  CONSUMO: 'Consumo',
  AJUSTE: 'Ajuste por recuento',
  MERMA: 'Merma',
};

export const movimientos = {
  consultar: (filtros: FiltrosMovimientos, pagina: number) => {
    const parametros = new URLSearchParams();
    Object.entries(filtros).forEach(([clave, valor]) => {
      if (valor !== undefined && valor !== '') parametros.set(clave, String(valor));
    });
    parametros.set('pagina', String(pagina));
    return api<PaginaDeMovimientos>(`/movimientos?${parametros.toString()}`);
  },
  eventos: () => api<EventoDelMovimiento[]>('/movimientos/eventos'),
};
