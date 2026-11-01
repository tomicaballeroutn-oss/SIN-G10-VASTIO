/** Datos sintéticos compartidos por Vitest y Playwright, sin dependencias de React. */
export const EVENTOS_FILTROS = [
  { id: 1, codigo: 'EV-2030-00001', estado: 'SENADO', nombre: 'Quince de Delfina', tipo: 'Quince',
    salon: { id: 1, codigo: 'avril', nombre: 'Avril', capacidad: 200 }, fecha: '2030-03-09',
    turno: { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00', horaFin: '06:00' },
    cliente: 'Delfina Ríos', vendedora: { id: 1, nombre: 'Lucía Ferreyra' }, planner: { id: 7, nombre: 'Ana Sosa' }, cantidadInvitados: 100 },
  { id: 2, codigo: 'EV-2030-00002', estado: 'CONFIRMADO', nombre: 'Convención Sur', tipo: 'Corporativo',
    salon: { id: 2, codigo: 'club', nombre: 'Club de Campo', capacidad: 300 }, fecha: '2030-03-10',
    turno: { id: 1, codigo: 'mediodia', nombre: 'Mediodía', horaInicio: '12:00', horaFin: '18:00' },
    cliente: 'Empresa Sur', vendedora: { id: 2, nombre: 'Sofía Méndez' }, planner: null, cantidadInvitados: null },
  { id: 3, codigo: 'EV-2030-00003', estado: 'CONTRATADO', nombre: 'Quince de Sofía', tipo: 'Quince',
    salon: { id: 3, codigo: 'santa-barbara', nombre: 'Santa Bárbara', capacidad: 200 }, fecha: '2030-03-11',
    turno: { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00', horaFin: '06:00' },
    cliente: 'Sofía Paz', vendedora: { id: 1, nombre: 'Lucía Ferreyra' }, planner: { id: 8, nombre: 'Carla Núñez' }, cantidadInvitados: 150 },
];

export const AGENDA_FILTROS = {
  mes: '2030-03',
  salones: [
    { id: 1, codigo: 'avril', nombre: 'Avril', activo: true },
    { id: 2, codigo: 'club', nombre: 'Club de Campo', activo: true },
    { id: 3, codigo: 'santa-barbara', nombre: 'Santa Bárbara', activo: true },
  ],
  turnos: [
    { id: 1, codigo: 'mediodia', nombre: 'Mediodía', horaInicio: '12:00', horaFin: '18:00', cruzaMedianoche: false },
    { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00', horaFin: '06:00', cruzaMedianoche: true },
  ],
  unidades: [
    { fecha: '2030-03-09', salonId: 1, turnoId: 2, estado: 'SENADO',
      evento: { id: 1, codigo: 'EV-2030-00001', nombre: 'Quince de Delfina', tipo: 'Quince', cliente: 'Delfina Ríos',
        vendedora: { id: 1, nombre: 'Lucía Ferreyra' }, detalle: true } },
    { fecha: '2030-03-09', salonId: 2, turnoId: 1, estado: 'SENADO',
      evento: { vendedora: { id: 2, nombre: 'Sofía Méndez' }, detalle: false } },
    { fecha: '2030-03-09', salonId: 2, turnoId: 2, estado: 'BLOQUEADO', bloqueo: { motivo: 'Mantenimiento', detalle: null } },
  ],
};
