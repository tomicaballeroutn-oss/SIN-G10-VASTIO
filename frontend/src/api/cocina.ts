import type { SalonId } from '../ds';
import { api } from './cliente';
import type { RenglonServicio } from './eventos';

/** Lo cargado en una categoría visible en cocina. */
export interface ServicioCocina {
  categoria: string;
  renglones: RenglonServicio[];
  detalle: string | null;
}

/**
 * Tarjeta de un evento en la vista de cocina (A7-02): solo lo gastronómico. Sin estado, importes, observaciones
 * internas ni datos del cliente: el backend no los manda.
 */
export interface TarjetaCocina {
  id: number;
  nombre: string;
  tipo: string;
  /** yyyy-mm-dd: día en que empieza la jornada. */
  fecha: string;
  salon: { codigo: SalonId; nombre: string };
  turno: 'mediodia' | 'noche';
  turnoNombre: string;
  /** «21:30:00»: la de inicio del evento o, si no se cargó, la del turno. */
  hora: string;
  invitados: number | null;
  servicios: ServicioCocina[];
}

export interface Cocina {
  /** yyyy-mm */
  mes: string;
  hoy: string;
  /** Último día que ve el perfil Cocina (HORIZONTE_COCINA_DIAS); null para el resto. */
  hasta: string | null;
  salones: { codigo: SalonId; nombre: string }[];
  eventos: TarjetaCocina[];
}

export const cocina = {
  mes: (mes?: string) => api<Cocina>(mes ? `/cocina?mes=${mes}` : '/cocina'),
  evento: (id: number) => api<TarjetaCocina>(`/cocina/eventos/${id}`),
};
