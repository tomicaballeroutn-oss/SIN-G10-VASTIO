import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Motivo } from '../../api/catalogos';
import { _reiniciarCliente } from '../../api/cliente';
import type { Ficha } from '../../api/eventos';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';
import { fichaDePrueba, SIN_ACCIONES } from '../../test/fichas';

const MOTIVOS: Motivo[] = [
  { id: 1, ambito: 'CANCELACION', nombre: 'Desistimiento del cliente', activo: true },
  { id: 2, ambito: 'CANCELACION', nombre: 'Falta de pago', activo: false },
  { id: 3, ambito: 'CANCELACION', nombre: 'Otro', activo: true },
  { id: 4, ambito: 'REPROGRAMACION', nombre: 'Pedido del cliente', activo: true },
];

const CONTRATADO = fichaDePrueba({ estado: 'CONTRATADO', acciones: { ...SIN_ACCIONES, cancelar: true, modificar: true } });

const CANCELADO: Ficha = {
  ...CONTRATADO,
  estado: 'CANCELADO',
  cancelacion: { motivo: 'Desistimiento del cliente', detalle: 'Se mudan a Mendoza' },
  acciones: SIN_ACCIONES,
  historial: [
    ...CONTRATADO.historial,
    {
      tipo: 'ESTADO', estadoAnterior: 'CONTRATADO', estadoNuevo: 'CANCELADO', usuario: { id: 2, nombre: 'Melina Sifón' },
      fechaHora: '2026-10-05T10:00:00-03:00', observacion: 'Desistimiento del cliente: Se mudan a Mendoza',
    },
  ],
};

function backend(ficha: Ficha = CONTRATADO, alCancelar: (p: Pedido) => Response = () => json(200, CANCELADO)) {
  return backendFalso(sesionDe(['COORDINACION'], 'Melina Sifón', 2), (p) => {
    if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, ficha);
    if (p.metodo === 'GET' && p.ruta === '/motivos') return json(200, MOTIVOS);
    if (p.metodo === 'POST' && p.ruta === '/eventos/5/cancelacion') return alCancelar(p);
    return undefined;
  });
}

async function abrir() {
  const usuario = userEvent.setup();
  montar('/eventos/5');
  await usuario.click(await screen.findByRole('button', { name: 'Cancelar evento' }));
  return { usuario, dialogo: screen.getByRole('dialog', { name: 'Cancelar evento' }) };
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-17 · cancelar evento', () => {
  it('cancela con motivo y detalle y la ficha muestra la cancelación', async () => {
    const fetchMock = backend();
    const { usuario, dialogo } = await abrir();

    const motivo = await within(dialogo).findByLabelText('Motivo');
    expect(within(motivo).getAllByRole('option').map((o) => o.textContent)).toEqual(['Elegí un motivo', 'Desistimiento del cliente', 'Otro']);
    await usuario.selectOptions(motivo, 'Desistimiento del cliente');
    await usuario.type(within(dialogo).getByLabelText(/Detalle/), 'Se mudan a Mendoza');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Cancelar evento' }));

    expect(await screen.findByText('Evento cancelado. La fecha volvió a estar disponible y se avisó a las áreas.')).toBeInTheDocument();
    const cancelacion = screen.getByRole('heading', { name: 'Cancelación' }).closest('section') as HTMLElement;
    expect(within(cancelacion).getByText('Se mudan a Mendoza')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancelar evento' })).not.toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toEqual({ motivoId: 1, detalle: 'Se mudan a Mendoza' });
  });

  it('pide el motivo, y con «Otro» también el detalle', async () => {
    const fetchMock = backend();
    const { usuario, dialogo } = await abrir();

    await usuario.click(within(dialogo).getByRole('button', { name: 'Cancelar evento' }));
    expect(await within(dialogo).findByLabelText('Motivo')).toHaveAccessibleDescription('Elegí un motivo.');

    await usuario.selectOptions(within(dialogo).getByLabelText('Motivo'), 'Otro');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Cancelar evento' }));
    expect(within(dialogo).getByLabelText(/Detalle/)).toHaveAccessibleDescription('Contanos qué pasó: el motivo es «Otro».');
    expect(pedidos(fetchMock).some((p) => p.metodo === 'POST')).toBe(false);
  });

  it('muestra lo que rechaza el sistema', async () => {
    backend(CONTRATADO, () => problema(409, 'EDICION_SIMULTANEA', 'Otra persona guardó cambios al mismo tiempo. Volvé a abrir la pantalla y repetí lo que hiciste.'));
    const { usuario, dialogo } = await abrir();

    await usuario.selectOptions(await within(dialogo).findByLabelText('Motivo'), 'Desistimiento del cliente');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Cancelar evento' }));

    expect(await within(dialogo).findByText(/Otra persona guardó cambios/)).toBeInTheDocument();
  });

  it('el historial muestra la cancelación con su motivo', async () => {
    backend(CANCELADO);
    montar('/eventos/5?pestana=historial');

    expect(await screen.findByText('Evento cancelado')).toBeInTheDocument();
    expect(screen.getByText('Desistimiento del cliente: Se mudan a Mendoza')).toBeInTheDocument();
  });

  it('no aparece para quien no puede cancelar', async () => {
    backend({ ...CONTRATADO, acciones: { ...SIN_ACCIONES, modificar: true } });
    montar('/eventos/5');

    await screen.findByRole('heading', { level: 1, name: 'Bruno y Martina' });
    expect(screen.queryByRole('button', { name: 'Cancelar evento' })).not.toBeInTheDocument();
  });
});
