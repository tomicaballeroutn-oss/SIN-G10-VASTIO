import { api } from './cliente';

/** Cantidad de una bebida en botellas; 0 la saca de la carga. */
export interface RenglonCarga {
  bebidaId: number;
  cantidad: number;
}

export interface CargaInicial {
  ubicacion: { id: number; nombre: string };
  /** La ubicación ya tiene otros movimientos: se corrige con un recuento. */
  cerrada: boolean;
  /** Lo cargado por bebida, ya corregido. */
  renglones: RenglonCarga[];
  /** Quién y cuándo guardó por última vez. */
  usuario: { id: number; nombre: string } | null;
  ultimaModificacion: string | null;
}

export const inventarioInicial = {
  consultar: (ubicacionId: number) => api<CargaInicial>(`/inventario-inicial/${ubicacionId}`),
  guardar: (ubicacionId: number, renglones: RenglonCarga[]) =>
    api<CargaInicial>(`/inventario-inicial/${ubicacionId}`, { metodo: 'PUT', cuerpo: { renglones } }),
};
