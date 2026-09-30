import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import type { Ficha } from '../../api/eventos';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';

const PRE_RESERVA: Ficha = {
  id: 5,
  codigo: 'EV-2026-00005',
  estado: 'PRE_RESERVA',
  nombre: 'Quince de Delfina Ríos',
  tipo: { id: 2, nombre: 'Quince' },
  salon: { id: 1, codigo: 'avril', nombre: 'Avril' },
  fecha: '2026-10-10',
  turno: { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00' },
  cliente: { id: 7, nombre: 'Delfina Ríos', documento: null, telefono: null, email: null },
  contactos: [],
  vendedora: { id: 1, nombre: 'Lucía Ferreyra' },
  planner: null,
  cantidadInvitados: null,
  invitadosDefinitivos: false,
  observacionesInternas: null,
  sena: null,
  fechaCreacion: '2026-09-21T10:15:00-03:00',
  version: 0,
  acciones: { modificar: true, liberar: true, registrarSena: true },
  historial: [{ tipo: 'ESTADO', estadoNuevo: 'PRE_RESERVA', usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-21T10:15:00-03:00' }],
};

const LIBERADA: Ficha = {
  ...PRE_RESERVA,
  estado: 'LIBERADA',
  version: 1,
  acciones: { modificar: false, liberar: false, registrarSena: false },
  historial: [
    ...PRE_RESERVA.historial,
    { tipo: 'ESTADO', estadoAnterior: 'PRE_RESERVA', estadoNuevo: 'LIBERADA', usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-25T09:00:00-03:00' },
  ],
};

function backend(alLiberar: (p: Pedido) => Response = () => json(200, LIBERADA), ficha = PRE_RESERVA) {
  return backendFalso(sesionDe(['VENDEDORA']), (p) => {
    if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, ficha);
    if (p.metodo === 'POST' && p.ruta === '/eventos/5/liberacion') return alLiberar(p);
    return undefined;
  });
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-10 · liberar pre-reserva', () => {
  it('confirma, libera y la ficha queda en Liberada con la liberación en el historial', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    montar('/eventos/5');

    await usuario.click(await screen.findByRole('button', { name: 'Liberar pre-reserva' }));
    const dialogo = screen.getByRole('dialog', { name: 'Liberar pre-reserva' });
    expect(dialogo).toHaveTextContent('La fecha vuelve a estar disponible en la agenda y queda registrada en el historial del evento como liberación.');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Liberar pre-reserva' }));

    expect(await screen.findByText('Pre-reserva liberada. La fecha volvió a estar disponible en la agenda.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByText('Liberada')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Liberar pre-reserva' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Modificar datos' })).not.toBeInTheDocument();
    expect(pedidos(fetchMock).filter((p) => p.metodo === 'POST')).toEqual([{ metodo: 'POST', ruta: '/eventos/5/liberacion', cuerpo: undefined }]);

    await usuario.click(screen.getByRole('tab', { name: /Historial/ }));
    expect(screen.getByText('Pre-reserva liberada')).toBeInTheDocument();
    expect(screen.getByText('liberó la fecha · vie 25 sep 09:00')).toBeInTheDocument();
  });

  it('cancelar no libera', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    montar('/eventos/5');

    await usuario.click(await screen.findByRole('button', { name: 'Liberar pre-reserva' }));
    await usuario.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(pedidos(fetchMock).some((p) => p.metodo === 'POST')).toBe(false);
  });

  it('si ya no se puede liberar, lo explica en el diálogo', async () => {
    backend(() => problema(422, 'ESTADO_NO_PERMITE', 'El evento está en Señado: solo se liberan pre-reservas.'));
    const usuario = userEvent.setup();
    montar('/eventos/5');

    await usuario.click(await screen.findByRole('button', { name: 'Liberar pre-reserva' }));
    await usuario.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Liberar pre-reserva' }));

    expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent('solo se liberan pre-reservas');
  });

  it('sin la acción disponible, no aparece el botón', async () => {
    backend(undefined, { ...PRE_RESERVA, acciones: { modificar: false, liberar: false, registrarSena: false } });
    montar('/eventos/5');

    await screen.findByRole('heading', { level: 1, name: 'Quince de Delfina Ríos' });
    expect(screen.queryByRole('button', { name: 'Liberar pre-reserva' })).not.toBeInTheDocument();
  });
});
