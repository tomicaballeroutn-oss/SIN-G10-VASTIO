import { api, type Rol } from './cliente';

export const ROLES_ESTADISTICAS: readonly Rol[] = ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'];
export interface FiltrosEstadisticas {
  desde?: string;
  hasta?: string;
  tipoEventoId?: number;
  salonId?: number;
  bebidaId?: number;
}
export interface MedidasConsumo {
  eventos: number;
  eventosConConsumoRegistrado: number;
  consumoTotal: number;
  promedioPorEvento: number | null;
  eventosConInvitadosValidos: number;
  invitadosRegistrados: number;
  consumoConInvitadosValidos: number;
  porInvitadoRegistrado: number | null;
}
export interface EstadisticasConsumo {
  unidad: 'BOTELLAS';
  denominador: 'INVITADOS_REGISTRADOS';
  filtros: { [K in keyof FiltrosEstadisticas]: FiltrosEstadisticas[K] | null };
  resumen: MedidasConsumo;
  porBebida: { bebidaId: number; nombre: string; presentacion: string; activo: boolean; medidas: MedidasConsumo }[];
  porTipoEvento: { tipoEventoId: number; nombre: string; activo: boolean; medidas: MedidasConsumo }[];
  detalle: { tipoEventoId: number; bebidaId: number; medidas: MedidasConsumo }[];
}
export function consultarEstadisticas(filtros: FiltrosEstadisticas) {
  const parametros = new URLSearchParams();
  for (const [clave, valor] of Object.entries(filtros)) {
    if (valor !== undefined && valor !== '') parametros.set(clave, String(valor));
  }
  const consulta = parametros.toString();
  return api<EstadisticasConsumo>(`/estadisticas/consumo${consulta ? `?${consulta}` : ''}`);
}
