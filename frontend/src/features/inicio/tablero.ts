import type { Agenda, UnidadOcupada } from '../../api/agenda';
import type { Rol, UsuarioSesion } from '../../api/cliente';
import type { EventoResumen } from '../../api/eventos';
import { ORDEN_SALONES, type AtajoTone, type DiaOcupado, type IconName } from '../../ds';
import { fechaCorta } from '../comun/formato';

/**
 * Cuentas del Inicio (UI-04). Solo ordenan y cuentan lo que ya devuelven agenda y eventos: no hay reglas nuevas.
 * Dirección, Coordinación, Administración y Compras miran el complejo; la vendedora y la planner, lo suyo.
 */

const VEN_EL_COMPLEJO: readonly Rol[] = ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'];
const ACCESO_TOTAL: readonly Rol[] = ['DIRECCION', 'COORDINACION'];

export interface Alcance {
  id: number;
  roles: readonly Rol[];
  /** Mira todo el complejo; si no, solo lo que vendió o tiene asignado. */
  complejo: boolean;
}

export function alcanceDe(usuario: UsuarioSesion): Alcance {
  return { id: usuario.id, roles: usuario.roles, complejo: usuario.roles.some((r) => VEN_EL_COMPLEJO.includes(r)) };
}

/** Para la vendedora, los que vendió; para la planner, los que tiene asignados. */
export function esPropio(a: Alcance, vendedoraId: number | undefined, plannerId: number | null | undefined): boolean {
  return a.complejo
    || (a.roles.includes('VENDEDORA') && vendedoraId === a.id)
    || (a.roles.includes('PLANNER') && plannerId != null && plannerId === a.id);
}

/** «2026-10-21» + n días. Fecha de calendario: se opera a mediodía UTC. */
export function sumarDias(iso: string, n: number): string {
  const d = new Date(`${iso}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

/** Los siete días, de lunes a domingo, de la semana de esa fecha. */
export function semanaDe(iso: string): string[] {
  const desdeLunes = (new Date(`${iso}T12:00:00Z`).getUTCDay() + 6) % 7;
  const lunes = sumarDias(iso, -desdeLunes);
  return Array.from({ length: 7 }, (_, i) => sumarDias(lunes, i));
}

/** Meses (yyyy-mm) que hay que pedir a la agenda para cubrir esas fechas. */
export function mesesDe(fechas: string[]): string[] {
  return [...new Set(fechas.map((f) => f.slice(0, 7)))].sort();
}

/** Unidades con evento (sin bloqueos) que le tocan a esa persona. */
function unidadesDeEventos(unidades: UnidadOcupada[], a: Alcance): UnidadOcupada[] {
  return unidades.filter((u) => u.estado !== 'BLOQUEADO' && u.evento && esPropio(a, u.evento.vendedora.id, u.evento.planner?.id));
}

export interface ResumenSemana {
  /** Eventos por día, de lunes a domingo. */
  porDia: number[];
  total: number;
  /** Confirmados, y los que ya pasaron por Confirmado (en curso, realizados, cerrados): así las tres cuentas suman el total. */
  confirmados: number;
  contratados: number;
  contratadosSinPlanner: number;
  /** Señados y pre-reservas. */
  porCerrar: number;
}

export function resumenSemana(unidades: UnidadOcupada[], semana: string[], a: Alcance): ResumenSemana {
  const deLaSemana = unidadesDeEventos(unidades, a).filter((u) => semana.includes(u.fecha));
  const contar = (estados: UnidadOcupada['estado'][]) => deLaSemana.filter((u) => estados.includes(u.estado)).length;
  return {
    porDia: semana.map((dia) => deLaSemana.filter((u) => u.fecha === dia).length),
    total: deLaSemana.length,
    confirmados: contar(['CONFIRMADO', 'EN_CURSO', 'REALIZADO', 'CERRADO']),
    contratados: contar(['CONTRATADO']),
    contratadosSinPlanner: deLaSemana.filter((u) => u.estado === 'CONTRATADO' && !u.evento?.planner).length,
    porCerrar: contar(['SENADO', 'PRE_RESERVA']),
  };
}

/** Para el MiniCalendar: el complejo entero, como la agenda. Un día con alguna reserva firme es `reservado`. */
export function diasOcupados(unidades: UnidadOcupada[]): Record<string, DiaOcupado> {
  const dias: Record<string, DiaOcupado> = {};
  for (const u of unidades) {
    if (u.estado === 'BLOQUEADO') continue;
    dias[u.fecha] = u.estado === 'PRE_RESERVA' && dias[u.fecha] !== 'reservado' ? 'prereserva' : 'reservado';
  }
  return dias;
}

/** Unidades libres del día (salones activos × turnos, menos las ocupadas por eventos o bloqueos). */
export function libresDelDia(agenda: Agenda, unidades: UnidadOcupada[], dia: string): { libres: number; total: number } {
  const activos = new Set(agenda.salones.filter((s) => s.activo).map((s) => s.id));
  const total = activos.size * agenda.turnos.length;
  const ocupadas = unidades.filter((u) => u.fecha === dia && activos.has(u.salonId)).length;
  return { libres: Math.max(total - ocupadas, 0), total };
}

/** Eventos de ese día que le tocan a la persona, por turno y después por salón. */
export function eventosDelDia(eventos: EventoResumen[], dia: string, a: Alcance): EventoResumen[] {
  return eventos
    .filter((e) => e.fecha === dia && esPropio(a, e.vendedora.id, e.planner?.id))
    .sort((x, y) => x.turno.horaInicio.localeCompare(y.turno.horaInicio)
      || ORDEN_SALONES.indexOf(x.salon.codigo) - ORDEN_SALONES.indexOf(y.salon.codigo));
}

export interface Tarea {
  clave: string;
  icono: IconName;
  tono: AtajoTone;
  titulo: string;
  detalle: string;
  eventoId: number;
}

/** ¿Tiene recordatorios? Solo quien puede hacer alguna de las tareas (docs/perfiles-y-permisos.md). */
export function tieneTareas(roles: readonly Rol[]): boolean {
  return roles.some((r) => [...ACCESO_TOTAL, 'VENDEDORA', 'PLANNER'].includes(r));
}

/**
 * Lo que le toca hacer, sacado del estado de los próximos eventos. Cada fila lleva a la ficha, donde está la acción:
 * - Dirección y Coordinación: firma de los señados y planner de los contratados que no la tienen.
 * - Vendedora: seña de sus pre-reservas.
 * - Planner: confirmar los contratados que tiene asignados.
 */
export function tareasDe(eventos: EventoResumen[], a: Alcance): Tarea[] {
  const total = a.roles.some((r) => ACCESO_TOTAL.includes(r));
  const tareas: Tarea[] = [];
  const agregar = (e: EventoResumen, clave: string, icono: IconName, tono: AtajoTone, titulo: string) =>
    tareas.push({ clave: `${clave}-${e.id}`, icono, tono, titulo, detalle: `${e.nombre} · ${fechaCorta(e.fecha)}`, eventoId: e.id });

  for (const e of [...eventos].sort((x, y) => x.fecha.localeCompare(y.fecha))) {
    if (total && e.estado === 'SENADO') agregar(e, 'firma', 'file-check', 'brand', 'Registrar firma de contrato');
    if (total && e.estado === 'CONTRATADO' && !e.planner) agregar(e, 'planner', 'users', 'warning', 'Asignar planner');
    if (a.roles.includes('VENDEDORA') && e.estado === 'PRE_RESERVA' && e.vendedora.id === a.id) {
      agregar(e, 'sena', 'banknote', 'accent', 'Registrar seña');
    }
    if (a.roles.includes('PLANNER') && e.estado === 'CONTRATADO' && e.planner?.id === a.id) {
      agregar(e, 'confirmar', 'circle-check', 'brand', 'Confirmar evento');
    }
  }
  return tareas;
}
