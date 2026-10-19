import { api, type Rol } from './cliente';

export const ROLES_CONSUMO: readonly Rol[] = ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'];

export interface DetalleConsumo {
  bebidaId: number;
  nombre: string;
  presentacion: string;
  unidad: string;
  unidadesPorBulto: number;
  entregado: number;
  devuelto: number;
  remanente: number;
  consumo: number;
  porInvitado: number | null;
}

export interface ConsumoEvento {
  invitados: number | null;
  denominador: 'INVITADOS';
  usaSegmentos: boolean;
  renglones: DetalleConsumo[];
  consumoTotal: number;
  porInvitadoTotal: number | null;
}

export const consultarConsumo = (eventoId: number) => api<ConsumoEvento>(`/eventos/${eventoId}/consumo`);
