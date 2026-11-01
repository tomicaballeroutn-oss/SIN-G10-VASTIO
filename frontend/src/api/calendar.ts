import { api, type Rol } from './cliente';

export const ROLES_CALENDAR: readonly Rol[] = ['DIRECCION', 'COORDINACION'];
export interface EstadoCalendar {
  eventoId: number;
  codigo: string;
  salon: string;
  fecha: string;
  turno: string;
  estado: 'NO_EXPORTABLE' | 'PENDIENTE' | 'EXPORTADO' | 'RETIRADO' | 'EN_PROCESO' | 'ERROR' | 'INCIERTO';
  pendiente: 'EXPORTAR' | 'ACTUALIZAR' | 'RETIRAR' | 'NINGUNA';
  error: string | null;
  ultimoExito: string | null;
  configurado: boolean;
  puedeExportar: boolean;
  puedeRetirar: boolean;
  seguimientoPendiente?: boolean;
  errorSeguimiento?: string | null;
}
export const calendar = {
  estado: (id: number) => api<EstadoCalendar>(`/eventos/${id}/calendar`),
  pendientes: () => api<EstadoCalendar[]>('/calendar/pendientes'),
  verificar: (id: number) => api<{ etag: string; coincideConVastio: boolean }>(`/eventos/${id}/calendar/verificacion`, { metodo: 'POST' }),
  aceptarRevision: (id: number, etag: string) => api<EstadoCalendar>(`/eventos/${id}/calendar/reconciliacion`, { metodo: 'POST', cuerpo: { etag } }),
  operar: (id: number, operacion: 'EXPORTAR' | 'ACTUALIZAR' | 'RETIRAR') => api<EstadoCalendar>(`/eventos/${id}/calendar`, {
    metodo: operacion === 'RETIRAR' ? 'DELETE' : operacion === 'ACTUALIZAR' ? 'PUT' : 'POST',
  }),
};
export function etiquetaCalendar(e: EstadoCalendar): string {
  if (!e.configurado) return 'Integración desactivada';
  if (e.seguimientoPendiente || e.errorSeguimiento) return 'Seguimiento local pendiente';
  if (e.estado === 'EN_PROCESO') return 'Operación en proceso';
  if (e.estado === 'INCIERTO') return 'Resultado incierto: requiere verificación';
  if (e.error) return 'Error: requiere atención';
  if (e.pendiente === 'RETIRAR') return 'Pendiente de retirada';
  if (e.pendiente === 'ACTUALIZAR') return 'Publicación desactualizada';
  if (e.pendiente === 'EXPORTAR') return 'Sin exportar';
  if (e.estado === 'EXPORTADO') return 'Exportado';
  if (e.estado === 'RETIRADO') return 'Publicación retirada';
  return 'El estado del evento no permite una publicación nueva';
}
