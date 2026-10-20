import { api } from './cliente';

/** Una bebida del ingreso, en botellas enteras. */
export interface RenglonIngreso {
  bebidaId: number;
  nombre: string;
  presentacion: string;
  unidad: string;
  unidadesPorBulto: number;
  cantidad: number;
}

export interface Ingreso {
  id: number;
  fechaIngreso: string;
  numeroRemito: string | null;
  destino: { id: number; nombre: string };
  renglones: RenglonIngreso[];
  usuario: { id: number; nombre: string };
  fechaRegistro: string;
}

/** Sin fecha, hoy. El destino es siempre el depósito madre. */
export interface DatosIngreso {
  fecha: string;
  numeroRemito: string;
  renglones: { bebidaId: number; cantidad: number }[];
}

export const ingresos = {
  registrar: (datos: DatosIngreso) => api<Ingreso>('/ingresos', { metodo: 'POST', cuerpo: datos }),
  recientes: (limite = 10) => api<Ingreso[]>(`/ingresos?limite=${limite}`),
};
