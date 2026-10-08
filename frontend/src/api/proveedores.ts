import { api } from './cliente';

export interface Proveedor {
  id: number;
  razonSocial: string;
  /** 11 dígitos sin guiones. */
  cuit: string | null;
  telefono: string | null;
  email: string | null;
  activo: boolean;
  /** Las que lo tienen como proveedor habitual. */
  bebidas: { id: number; nombre: string; presentacion: string }[];
}

/** CUIT con o sin guiones. */
export interface DatosProveedor {
  razonSocial: string;
  cuit: string;
  telefono: string;
  email: string;
}

export const proveedores = {
  listar: () => api<Proveedor[]>('/proveedores'),
  crear: (datos: DatosProveedor) => api<Proveedor>('/proveedores', { metodo: 'POST', cuerpo: datos }),
  modificar: (id: number, datos: DatosProveedor) => api<Proveedor>(`/proveedores/${id}`, { metodo: 'PUT', cuerpo: datos }),
  /** Deja exactamente esas bebidas con este proveedor habitual. */
  fijarBebidas: (id: number, bebidaIds: number[]) => api<Proveedor>(`/proveedores/${id}/bebidas`, { metodo: 'PUT', cuerpo: { bebidaIds } }),
  darDeBaja: (id: number) => api<Proveedor>(`/proveedores/${id}/baja`, { metodo: 'POST' }),
  reactivar: (id: number) => api<Proveedor>(`/proveedores/${id}/reactivacion`, { metodo: 'POST' }),
};

/** «30711222339» → «30-71122233-9». */
export function cuitLegible(cuit: string | null): string {
  return cuit && cuit.length === 11 ? `${cuit.slice(0, 2)}-${cuit.slice(2, 10)}-${cuit.slice(10)}` : (cuit ?? '—');
}
