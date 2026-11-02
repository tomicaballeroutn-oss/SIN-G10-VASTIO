import { api } from './cliente';
import type { EventoDeBarra, SalonResumen, UbicacionResumen } from './entregas';

/** Consumo de una bebida en el evento, en botellas. `porAsistente` null si no hay invitados cargados. */
export interface RenglonCierre {
  bebidaId: number;
  nombre: string;
  presentacion: string;
  unidad: string;
  unidadesPorBulto: number;
  entregado: number;
  devuelto: number;
  remanente: number;
  consumo: number;
  porAsistente: number | null;
}

/**
 * Conciliación del evento. `pendientes`: barras sin devolución (no se puede cerrar). `usaSegmentos`: el tipo pide
 * asistencia por segmento, que todavía no se registra. `cerrado`: lo congelado. Sin importes.
 */
export interface Cierre {
  evento: EventoDeBarra;
  salon: SalonResumen;
  invitados: number | null;
  usaSegmentos: boolean;
  /** Total de la asistencia real por segmento, si ya se cargó: es el divisor del consumo por asistente. */
  asistenciaReal: number | null;
  pendientes: UbicacionResumen[];
  renglones: RenglonCierre[];
  consumoTotal: number;
  porAsistenteTotal: number | null;
  cerrado: boolean;
}

export interface EventoPorCerrar {
  evento: EventoDeBarra;
  salon: SalonResumen;
}

export const cierres = {
  pendientes: () => api<EventoPorCerrar[]>('/cierres/pendientes'),
  pantalla: (eventoId: number) => api<Cierre>(`/eventos/${eventoId}/cierre`),
  cerrar: (eventoId: number) => api<Cierre>(`/eventos/${eventoId}/cierre`, { metodo: 'POST' }),
};
