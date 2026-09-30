import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fechaLarga, hoyEnCordoba, sumarMeses, type Agenda } from '../../api/agenda';
import { _reiniciarCliente } from '../../api/cliente';
import { backendFalso, json, montar, pedidos, sesionDe } from '../../test/backendFalso';

const MARZO: Agenda = {
  mes: '2030-03',
  salones: [
    { id: 1, codigo: 'avril', nombre: 'Avril', activo: true },
    { id: 2, codigo: 'club', nombre: 'Club de Campo', activo: true },
    { id: 3, codigo: 'santa-barbara', nombre: 'Santa Bárbara', activo: false },
  ],
  turnos: [
    { id: 1, codigo: 'mediodia', nombre: 'Mediodía', horaInicio: '12:00:00', horaFin: '18:00:00', cruzaMedianoche: false },
    { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00', cruzaMedianoche: true },
  ],
  unidades: [
    { fecha: '2030-03-09', salonId: 2, turnoId: 1, estado: 'SENADO', evento: { vendedora: { id: 8, nombre: 'Melina Ruiz' }, detalle: false } },
    {
      fecha: '2030-03-09', salonId: 1, turnoId: 2, estado: 'PRE_RESERVA',
      evento: { id: 5, codigo: 'EV-2030-00005', nombre: 'Quince de Delfina', tipo: 'Quince', cliente: 'Delfina Ríos', vendedora: { id: 1, nombre: 'Lucía Ferreyra' }, detalle: true },
    },
    { fecha: '2030-03-09', salonId: 2, turnoId: 2, estado: 'BLOQUEADO', bloqueo: { motivo: 'Mantenimiento', detalle: 'Pintura del salón' } },
  ],
};

function backend() {
  return backendFalso(sesionDe(['VENDEDORA']), ({ ruta }) => {
    if (ruta === '/agenda?mes=2030-03') return json(200, MARZO);
    if (ruta.startsWith('/agenda?mes=')) return json(200, { ...MARZO, mes: ruta.slice(-7), unidades: [] });
    return undefined;
  });
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-07 · consultar agenda', () => {
  it('muestra el mes con cada día descripto y, al tocarlo, sus seis unidades', async () => {
    backend();
    const usuario = userEvent.setup();
    const router = montar('/agenda?mes=2030-03');

    expect(await screen.findByRole('heading', { name: 'Marzo 2030' })).toBeInTheDocument();
    const dia = screen.getByRole('button', { name: /^sábado 9 de marzo/ });
    expect(dia).toHaveAccessibleName(
      'sábado 9 de marzo. Club de Campo mediodía: Señado; Avril noche: Pre-reserva; Club de Campo noche: Bloqueado',
    );
    expect(screen.getByRole('button', { name: 'domingo 10 de marzo. Todo disponible' })).toBeInTheDocument();

    await usuario.click(dia);

    expect(router.state.location.search).toContain('dia=2030-03-09');
    const detalle = screen.getByRole('heading', { name: 'sábado 9 de marzo' }).closest('section') as HTMLElement;
    expect(within(detalle).getByText('Mediodía · 12:00 a 18:00')).toBeInTheDocument();
    expect(within(detalle).getByText('La tiene Melina Ruiz.')).toBeInTheDocument();
    expect(within(detalle).getByText('Quince de Delfina')).toBeInTheDocument();
    expect(within(detalle).getByText('Quince · Delfina Ríos')).toBeInTheDocument();
    expect(within(detalle).getByText('Mantenimiento · Pintura del salón')).toBeInTheDocument();
    expect(within(detalle).getAllByText('Libre para ofrecer.')).toHaveLength(1);
    expect(within(detalle).getAllByText('Salón dado de baja: no acepta pre-reservas.')).toHaveLength(2);
  });

  it('pasa al mes siguiente y pide sus datos', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    const router = montar('/agenda?mes=2030-03&dia=2030-03-09');

    await usuario.click(await screen.findByRole('button', { name: 'Mes siguiente' }));

    expect(await screen.findByRole('heading', { name: 'Abril 2030' })).toBeInTheDocument();
    expect(router.state.location.search).toBe('?mes=2030-04');
    expect(pedidos(fetchMock).map((p) => p.ruta)).toContain('/agenda?mes=2030-04');
  });

  it('el filtro atenúa las unidades que no coinciden, sin ocultarlas', async () => {
    backend();
    const usuario = userEvent.setup();
    montar('/agenda?mes=2030-03');
    await screen.findByRole('heading', { name: 'Marzo 2030' });
    const pipsDel9 = () => screen.getByRole('button', { name: /^sábado 9 de marzo/ }).querySelectorAll('.v-pip');

    expect(screen.getByRole('button', { name: /^sábado 9 de marzo/ }).querySelectorAll('.is-dim')).toHaveLength(0);
    await usuario.selectOptions(screen.getByLabelText('Estado'), 'disponible');

    const atenuados = [...pipsDel9()].filter((p) => p.classList.contains('is-dim'));
    expect(atenuados).toHaveLength(3);
    expect(atenuados.every((p) => !p.classList.contains('v-pip--disponible'))).toBe(true);
  });
});

describe('fechas de la agenda', () => {
  it('hoy es el de Córdoba aunque en UTC ya sea mañana', () => {
    expect(hoyEnCordoba(new Date('2030-03-10T01:30:00Z'))).toBe('2030-03-09');
  });

  it('suma meses cruzando el año', () => {
    expect(sumarMeses('2030-12', 1)).toBe('2031-01');
    expect(sumarMeses('2030-01', -1)).toBe('2029-12');
  });

  it('escribe la fecha larga sin correrse de día', () => {
    expect(fechaLarga('2030-03-09')).toBe('sábado 9 de marzo');
    expect(fechaLarga('2030-03-09', true)).toBe('sábado 9 de marzo de 2030');
  });
});
