import { ESTADO_POR_CODIGO, type EventStatus, type SalonId } from '../ds';
import { api } from './cliente';

/** Código de `evento.estado` en la base. */
export type EstadoEvento = keyof typeof ESTADO_POR_CODIGO;

export interface PersonaAgenda {
  id: number;
  nombre: string;
}

/** Sin `detalle`, solo llegan vendedora y planner: es un evento de otra vendedora. */
export interface EventoAgenda {
  id?: number;
  codigo?: string;
  nombre?: string;
  tipo?: string;
  cliente?: string;
  vendedora: PersonaAgenda;
  planner?: PersonaAgenda;
  detalle: boolean;
}

export interface UnidadOcupada {
  /** yyyy-mm-dd: día en que empieza la jornada. */
  fecha: string;
  salonId: number;
  turnoId: number;
  estado: EstadoEvento | 'BLOQUEADO';
  evento?: EventoAgenda;
  bloqueo?: { motivo: string; detalle: string | null };
}

export interface Agenda {
  /** yyyy-mm */
  mes: string;
  salones: { id: number; codigo: SalonId; nombre: string; activo: boolean }[];
  turnos: { id: number; codigo: 'mediodia' | 'noche'; nombre: string; horaInicio: string; horaFin: string; cruzaMedianoche: boolean }[];
  /** Solo las ocupadas; las que faltan están disponibles. */
  unidades: UnidadOcupada[];
}

export const agenda = {
  mes: (mes: string) => api<Agenda>(`/agenda?mes=${mes}`),
};

/** Estado de una unidad para el sistema de diseño. */
export function estadoDs(estado: UnidadOcupada['estado']): EventStatus {
  return estado === 'BLOQUEADO' ? 'bloqueado' : ESTADO_POR_CODIGO[estado];
}

const ZONA = 'America/Argentina/Cordoba';

/** Hoy en Córdoba, yyyy-mm-dd, sin importar la zona del dispositivo. */
export function hoyEnCordoba(ahora = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONA, year: 'numeric', month: '2-digit', day: '2-digit' }).format(ahora);
}

/** «2030-03-09» → «sábado 9 de marzo». La fecha es de calendario: se arma a mediodía UTC para no correrse de día. */
export function fechaLarga(iso: string, conAnio = false): string {
  const fecha = new Date(`${iso}T12:00:00Z`);
  return new Intl.DateTimeFormat('es-AR', {
    timeZone: 'UTC', weekday: 'long', day: 'numeric', month: 'long', ...(conAnio ? { year: 'numeric' } : {}),
  }).format(fecha).replace(',', '');
}

/** «2030-03» ± n meses. */
export function sumarMeses(mes: string, n: number): string {
  const [anio, m] = mes.split('-').map(Number);
  const total = anio * 12 + (m - 1) + n;
  return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}`;
}
