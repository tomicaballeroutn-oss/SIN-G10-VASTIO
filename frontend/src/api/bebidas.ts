import { api } from './cliente';

export interface TipoBebida {
  id: number;
  nombre: string;
}

export interface UnidadManipulacion {
  id: number;
  nombre: string;
  /** Con esta unidad, el bulto es de 1 botella. */
  esBotella: boolean;
}

/** Código de barras y botellas que representa una lectura (1 la botella, 6 o 12 la caja). */
export interface CodigoBarra {
  codigo: string;
  unidades: number;
}

/** Las cantidades van en botellas. Sin precio: el sistema no maneja dinero. */
export interface Bebida {
  id: number;
  nombre: string;
  presentacion: string;
  tipo: TipoBebida;
  unidad: UnidadManipulacion;
  unidadesPorBulto: number;
  stockMinimo: number | null;
  codigos: CodigoBarra[];
  proveedorHabitual: { id: number; razonSocial: string; activo: boolean } | null;
  activo: boolean;
  fechaBaja: string | null;
}

/** Tipo y unidad en null si no se eligieron: el backend responde qué falta. */
export interface DatosBebida {
  nombre: string;
  presentacion: string;
  tipoId: number | null;
  unidadId: number | null;
  unidadesPorBulto: number;
  stockMinimo: number | null;
  codigos: CodigoBarra[];
  proveedorId: number | null;
}

/** Ubicación donde la bebida tiene saldo, en botellas. */
export interface SaldoBebida {
  ubicacionId: number;
  ubicacion: string;
  cantidad: number;
}

export const bebidas = {
  listar: () => api<Bebida[]>('/bebidas'),
  porCodigo: (codigo: string) => api<Bebida>(`/bebidas/codigos/${encodeURIComponent(codigo)}`),
  saldos: (id: number) => api<SaldoBebida[]>(`/bebidas/${id}/saldos`),
  crear: (datos: DatosBebida) => api<Bebida>('/bebidas', { metodo: 'POST', cuerpo: datos }),
  modificar: (id: number, datos: DatosBebida) => api<Bebida>(`/bebidas/${id}`, { metodo: 'PUT', cuerpo: datos }),
  darDeBaja: (id: number) => api<Bebida>(`/bebidas/${id}/baja`, { metodo: 'POST' }),
  reactivar: (id: number) => api<Bebida>(`/bebidas/${id}/reactivacion`, { metodo: 'POST' }),
  tipos: () => api<TipoBebida[]>('/tipos-bebida'),
  unidades: () => api<UnidadManipulacion[]>('/unidades-manipulacion'),
};

/** «Caja de 6», «Pack de 4», «Botella». */
export function presentacionDeBulto(b: Pick<Bebida, 'unidad' | 'unidadesPorBulto'>): string {
  return b.unidadesPorBulto > 1 ? `${b.unidad.nombre} de ${b.unidadesPorBulto}` : b.unidad.nombre;
}
