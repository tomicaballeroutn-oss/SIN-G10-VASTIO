import { api, type Rol } from './cliente';
import type { UbicacionResumen, EventoDeBarra } from './entregas';
import type { RenglonIngreso } from './ingresos';

export const ROLES_PREPARACION: Rol[] = ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'];
export interface Preparacion {
  id: number;
  eventoId: number;
  barra: UbicacionResumen;
  origen: UbicacionResumen;
  destino: UbicacionResumen;
  usuario: { id: number; nombre: string };
  fechaHora: string;
  renglones: RenglonIngreso[];
}
export interface PantallaPreparacion {
  evento: EventoDeBarra;
  madre: UbicacionResumen | null;
  circuitos: { barra: UbicacionResumen; abastecimiento: UbicacionResumen; requierePreparacion: boolean }[];
  historial: Preparacion[];
}
export interface SolicitudPreparacion {
  barraId: number;
  destinoId: number;
  renglones: { bebidaId: number; cantidad: number }[];
}
export const preparaciones = {
  consultar: (id: number) => api<PantallaPreparacion>(`/eventos/${id}/preparaciones`),
  registrar: (id: number, clave: string, cuerpo: SolicitudPreparacion) => api<Preparacion>(`/eventos/${id}/preparaciones`, {
    metodo: 'POST', cuerpo, cabeceras: { 'Idempotency-Key': clave },
  }),
};
