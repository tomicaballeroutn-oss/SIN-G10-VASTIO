import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import type { EventoResumen, Ficha, PlannerCandidata } from '../../api/eventos';
import { backendFalso, json, montar, pedidos, sesionDe, type Pedido } from '../../test/backendFalso';
import { fichaDePrueba, SIN_ACCIONES } from '../../test/fichas';

const SIN_PLANNER = fichaDePrueba({ estado: 'CONTRATADO', planner: null, acciones: { ...SIN_ACCIONES, asignarPlanner: true } });

const CANDIDATAS: PlannerCandidata[] = [
  { id: 3, nombre: 'Ana Sosa', otrosEventos: ['Quince de Delfina · Club de Campo · sáb 14/11 · Mediodía'] },
  { id: 4, nombre: 'Carla Núñez', otrosEventos: [] },
];

function backend(ficha: Ficha = SIN_PLANNER, alAsignar: (p: Pedido) => Response = (p) =>
  json(200, { ...ficha, planner: p.cuerpo && (p.cuerpo as { plannerId: number | null }).plannerId === 4 ? { id: 4, nombre: 'Carla Núñez' } : null })) {
  return backendFalso(sesionDe(['COORDINACION'], 'Melina Sifón', 2), (p) => {
    if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, ficha);
    if (p.metodo === 'GET' && p.ruta === '/eventos/5/planners') return json(200, CANDIDATAS);
    if (p.metodo === 'PUT' && p.ruta === '/eventos/5/planner') return alAsignar(p);
    return undefined;
  });
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-16 · asignar planner', () => {
  it('asigna una planner y la ficha la muestra', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    montar('/eventos/5');

    await usuario.click(await screen.findByRole('button', { name: 'Asignar planner' }));
    const dialogo = screen.getByRole('dialog', { name: 'Asignar planner' });
    await usuario.selectOptions(await within(dialogo).findByLabelText('Planner'), 'Carla Núñez');
    expect(within(dialogo).queryByText(/ya tiene otro evento/)).not.toBeInTheDocument();
    await usuario.click(within(dialogo).getByRole('button', { name: 'Asignar planner' }));

    expect(await screen.findByText('Planner asignada: Carla Núñez.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cambiar planner' })).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toEqual({ plannerId: 4 });
  });

  it('advierte si la planner ya tiene otro evento esa jornada, sin impedir asignarla', async () => {
    backend();
    const usuario = userEvent.setup();
    montar('/eventos/5');

    await usuario.click(await screen.findByRole('button', { name: 'Asignar planner' }));
    const dialogo = screen.getByRole('dialog', { name: 'Asignar planner' });
    await usuario.selectOptions(await within(dialogo).findByLabelText('Planner'), 'Ana Sosa');

    expect(within(dialogo).getByText('Ana Sosa ya tiene otro evento esa jornada')).toBeInTheDocument();
    expect(within(dialogo).getByText(/Quince de Delfina · Club de Campo/)).toBeInTheDocument();
    expect(within(dialogo).getByRole('button', { name: 'Asignar planner' })).toBeEnabled();
  });

  it('en Contratado se puede quitar la planner', async () => {
    const fetchMock = backend({ ...SIN_PLANNER, planner: { id: 3, nombre: 'Ana Sosa' } });
    const usuario = userEvent.setup();
    montar('/eventos/5');

    await usuario.click(await screen.findByRole('button', { name: 'Cambiar planner' }));
    const dialogo = screen.getByRole('dialog', { name: 'Asignar planner' });
    await usuario.selectOptions(await within(dialogo).findByLabelText('Planner'), 'Sin planner');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Quitar planner' }));

    expect(await screen.findByText('Planner quitada.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toEqual({ plannerId: null });
  });

  it('en Confirmado no ofrece quitarla', async () => {
    backend({ ...SIN_PLANNER, estado: 'CONFIRMADO', planner: { id: 3, nombre: 'Ana Sosa' } });
    const usuario = userEvent.setup();
    montar('/eventos/5');

    await usuario.click(await screen.findByRole('button', { name: 'Cambiar planner' }));
    const select = await within(screen.getByRole('dialog')).findByLabelText('Planner');
    expect(within(select).queryByRole('option', { name: 'Sin planner' })).not.toBeInTheDocument();
  });

  it('no aparece para quien no puede asignarla', async () => {
    backend({ ...SIN_PLANNER, acciones: SIN_ACCIONES });
    montar('/eventos/5');

    await screen.findByRole('heading', { level: 1, name: 'Bruno y Martina' });
    expect(screen.queryByRole('button', { name: 'Asignar planner' })).not.toBeInTheDocument();
  });
});

describe('programación operativa de la planner', () => {
  const evento = (id: number, nombre: string, planner: { id: number; nombre: string } | null): EventoResumen => ({
    id, codigo: `EV-2026-0000${id}`, estado: 'CONTRATADO', nombre, tipo: 'Casamiento',
    salon: { id: 1, codigo: 'avril', nombre: 'Avril', capacidad: null }, fecha: '2026-11-14',
    turno: { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00' },
    cliente: 'Cliente', vendedora: { id: 1, nombre: 'Lucía Ferreyra' }, planner, cantidadInvitados: 150,
  });

  it('la planner filtra los eventos que tiene asignados', async () => {
    backendFalso(sesionDe(['PLANNER'], 'Ana Sosa', 3), ({ ruta }) => (ruta === '/eventos'
      ? json(200, [evento(1, 'Bruno y Martina', { id: 3, nombre: 'Ana Sosa' }), evento(2, 'Quince de Delfina', { id: 4, nombre: 'Carla Núñez' })])
      : undefined));
    const usuario = userEvent.setup();
    montar('/eventos');

    expect(await screen.findByText('Quince de Delfina')).toBeInTheDocument();
    await usuario.click(screen.getByRole('tab', { name: 'Asignados a mí' }));

    expect(screen.getByText('Bruno y Martina')).toBeInTheDocument();
    expect(screen.queryByText('Quince de Delfina')).not.toBeInTheDocument();
  });

  it('sin eventos asignados lo dice', async () => {
    backendFalso(sesionDe(['PLANNER'], 'Ana Sosa', 3), ({ ruta }) => (ruta === '/eventos'
      ? json(200, [evento(2, 'Quince de Delfina', null)]) : undefined));
    const usuario = userEvent.setup();
    montar('/eventos');

    await usuario.click(await screen.findByRole('tab', { name: 'Asignados a mí' }));
    expect(screen.getByText('No tenés eventos asignados')).toBeInTheDocument();
  });

  it('solo la planner ve el filtro', async () => {
    backendFalso(sesionDe(['VENDEDORA']), ({ ruta }) => (ruta === '/eventos' ? json(200, [evento(1, 'Bruno y Martina', null)]) : undefined));
    montar('/eventos');

    await screen.findByText('Bruno y Martina');
    expect(screen.queryByRole('tab', { name: 'Asignados a mí' })).not.toBeInTheDocument();
  });
});
