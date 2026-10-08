import { api } from './cliente';

/** Ids de V2 (ámbito Ajuste): los que se preseleccionan. */
export const MOTIVO_ROTURA = 11;
export const MOTIVO_RECUENTO = 12;

export interface DatosAjuste {
  ubicacionId: number;
  bebidaId: number;
  motivoId: number;
  detalle: string;
}

/** Cantidades en botellas. `registrado` false si el recuento coincidió con el saldo. */
export interface ResultadoAjuste {
  registrado: boolean;
  movimientoId: number | null;
  tipo: 'AJUSTE' | 'MERMA' | null;
  saldoAnterior: number;
  saldo: number;
  diferencia: number;
  /** Quedó negativo y se avisó a Compras y Administración. */
  avisoSaldoNegativo: boolean;
}

export const ajustes = {
  recuento: (datos: DatosAjuste & { cantidadContada: number }) => api<ResultadoAjuste>('/ajustes/recuento', { metodo: 'POST', cuerpo: datos }),
  rotura: (datos: DatosAjuste & { cantidad: number }) => api<ResultadoAjuste>('/ajustes/rotura', { metodo: 'POST', cuerpo: datos }),
};
