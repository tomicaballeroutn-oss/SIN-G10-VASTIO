import type { Agenda } from '../../api/agenda';
import type { EventoResumen } from '../../api/eventos';

/**
 * Datos del Inicio para el miércoles 21 de octubre de 2026 (semana del 19 al 25).
 * Lucía Ferreyra (1) es la vendedora; Ana Sosa (3), la planner; Melina Ruiz (8), otra vendedora.
 */
export const HOY = '2026-10-21';

const LUCIA = { id: 1, nombre: 'Lucía Ferreyra' };
const MELINA = { id: 8, nombre: 'Melina Ruiz' };
const ANA = { id: 3, nombre: 'Ana Sosa' };

export const OCTUBRE: Agenda = {
  mes: '2026-10',
  salones: [
    { id: 1, codigo: 'avril', nombre: 'Avril', activo: true },
    { id: 2, codigo: 'club', nombre: 'Club de Campo', activo: true },
    { id: 3, codigo: 'santa-barbara', nombre: 'Santa Bárbara', activo: true },
  ],
  turnos: [
    { id: 1, codigo: 'mediodia', nombre: 'Mediodía', horaInicio: '12:00:00', horaFin: '18:00:00', cruzaMedianoche: false },
    { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00', cruzaMedianoche: true },
  ],
  unidades: [
    { fecha: '2026-10-20', salonId: 1, turnoId: 2, estado: 'CONFIRMADO', evento: { id: 10, nombre: 'Cena CBU', vendedora: LUCIA, planner: ANA, detalle: true } },
    { fecha: '2026-10-22', salonId: 2, turnoId: 2, estado: 'CONTRATADO', evento: { id: 11, nombre: 'Casamiento Paz', vendedora: LUCIA, detalle: true } },
    { fecha: '2026-10-24', salonId: 1, turnoId: 2, estado: 'SENADO', evento: { id: 12, nombre: 'Cumpleaños de Marta', vendedora: MELINA, planner: ANA, detalle: true } },
    { fecha: '2026-10-24', salonId: 3, turnoId: 1, estado: 'PRE_RESERVA', evento: { id: 13, nombre: 'Bautismo Ledesma', vendedora: LUCIA, detalle: true } },
    { fecha: '2026-10-24', salonId: 2, turnoId: 2, estado: 'BLOQUEADO', bloqueo: { motivo: 'Mantenimiento', detalle: null } },
    { fecha: '2026-10-25', salonId: 1, turnoId: 1, estado: 'PRE_RESERVA', evento: { vendedora: MELINA, detalle: false } },
    { fecha: '2026-10-30', salonId: 1, turnoId: 2, estado: 'CONFIRMADO', evento: { id: 14, nombre: 'Fin de año Olmos', vendedora: LUCIA, planner: ANA, detalle: true } },
  ],
};

const SALON = {
  avril: { id: 1, codigo: 'avril', nombre: 'Avril', capacidad: null },
  club: { id: 2, codigo: 'club', nombre: 'Club de Campo', capacidad: null },
  sb: { id: 3, codigo: 'santa-barbara', nombre: 'Santa Bárbara', capacidad: null },
} as const;
const MEDIODIA = { id: 1, codigo: 'mediodia', nombre: 'Mediodía', horaInicio: '12:00:00', horaFin: '18:00:00' } as const;
const NOCHE = { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00' } as const;

function evento(cambios: Partial<EventoResumen> & Pick<EventoResumen, 'id' | 'estado' | 'nombre' | 'fecha'>): EventoResumen {
  return {
    codigo: `EV-2026-000${cambios.id}`, tipo: 'Casamiento', salon: SALON.avril, turno: NOCHE, cliente: 'Cliente',
    vendedora: LUCIA, planner: null, cantidadInvitados: null, ...cambios,
  };
}

/** Lo que devuelve /eventos a Dirección: los activos desde hoy. */
export const PROXIMOS: EventoResumen[] = [
  evento({ id: 11, estado: 'CONTRATADO', nombre: 'Casamiento Paz', fecha: '2026-10-22', salon: SALON.club, cantidadInvitados: 120 }),
  evento({ id: 12, estado: 'SENADO', nombre: 'Cumpleaños de Marta', fecha: '2026-10-24', vendedora: MELINA, planner: ANA, cantidadInvitados: 80 }),
  evento({ id: 13, estado: 'PRE_RESERVA', nombre: 'Bautismo Ledesma', fecha: '2026-10-24', salon: SALON.sb, turno: MEDIODIA }),
  evento({ id: 14, estado: 'CONFIRMADO', nombre: 'Fin de año Olmos', fecha: '2026-10-30', planner: ANA }),
  evento({ id: 16, estado: 'CONTRATADO', nombre: 'Quince de Valentina', fecha: '2026-11-07', vendedora: MELINA, planner: ANA }),
];
