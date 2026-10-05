import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import type { Ficha } from '../../api/eventos';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';
import { fichaDePrueba, SIN_ACCIONES } from '../../test/fichas';

const CONTRATADO = fichaDePrueba({
  estado: 'CONTRATADO',
  version: 3,
  cantidadInvitados: 170,
  salon: { id: 1, codigo: 'avril', nombre: 'Avril', capacidad: 300 },
  acciones: { ...SIN_ACCIONES, modificar: true },
  historial: [
    { tipo: 'ESTADO', estadoNuevo: 'PRE_RESERVA', usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-21T10:15:00-03:00' },
    {
      tipo: 'MODIFICACION', campo: 'cantidad_invitados', valorAnterior: '150', valorNuevo: '170',
      usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-28T09:40:00-03:00',
    },
  ],
});

const GUARDADA: Ficha = { ...CONTRATADO, version: 4, cantidadInvitados: 180, invitadosDefinitivos: true };

function backend(ficha: Ficha = CONTRATADO, alGuardar: (p: Pedido) => Response = () => json(200, GUARDADA)) {
  return backendFalso(sesionDe(['VENDEDORA']), (p) => {
    if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, ficha);
    if (p.metodo === 'PUT' && p.ruta === '/eventos/5/invitados') return alGuardar(p);
    return undefined;
  });
}

async function abrir() {
  const usuario = userEvent.setup();
  montar('/eventos/5');
  await usuario.click(await screen.findByRole('button', { name: 'Cantidad de invitados' }));
  return { usuario, dialogo: screen.getByRole('dialog', { name: 'Cantidad de invitados' }) };
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-14 · cantidad de invitados', () => {
  it('registra la cantidad definitiva y la ficha la muestra', async () => {
    const fetchMock = backend();
    const { usuario, dialogo } = await abrir();

    expect(within(dialogo).getByText(/Cantidad anterior:/)).toHaveTextContent('Cantidad anterior: 170 (prevista)');
    expect(within(dialogo).getByText('Lucía Ferreyra')).toBeInTheDocument();
    await usuario.click(within(dialogo).getByRole('button', { name: 'Sumar 10' }));
    await usuario.click(within(dialogo).getByLabelText(/Cantidad definitiva/));
    await usuario.click(within(dialogo).getByRole('button', { name: 'Guardar cantidad' }));

    expect(await screen.findByText('Cantidad de invitados guardada.')).toBeInTheDocument();
    expect(screen.getByText('180 (definitivos)')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toEqual({ version: 3, cantidad: 180, definitivos: true });
  });

  it('advierte sin impedir si supera la capacidad del salón', async () => {
    const fetchMock = backend();
    const { usuario, dialogo } = await abrir();

    const campo = within(dialogo).getByLabelText('Cantidad de invitados');
    await usuario.clear(campo);
    await usuario.type(campo, '450');

    expect(within(dialogo).getByText('Supera la capacidad de Avril (300 personas)')).toBeInTheDocument();
    await usuario.click(within(dialogo).getByRole('button', { name: 'Guardar cantidad' }));
    await screen.findByText('Cantidad de invitados guardada.');
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toMatchObject({ cantidad: 450 });
  });

  it('no manda una cantidad en 0', async () => {
    const fetchMock = backend(fichaDePrueba({ cantidadInvitados: null, acciones: { ...SIN_ACCIONES, modificar: true } }));
    const { usuario, dialogo } = await abrir();

    await usuario.click(within(dialogo).getByRole('button', { name: 'Guardar cantidad' }));

    expect(within(dialogo).getByLabelText('Cantidad de invitados'))
      .toHaveAccessibleDescription('La cantidad de invitados tiene que ser mayor a 0.');
    expect(pedidos(fetchMock).some((p) => p.metodo === 'PUT')).toBe(false);
  });

  it('con el evento confirmado la cantidad sigue siendo definitiva', async () => {
    backend({ ...CONTRATADO, estado: 'CONFIRMADO', invitadosDefinitivos: true });
    const { dialogo } = await abrir();

    const definitiva = within(dialogo).getByLabelText(/Cantidad definitiva/);
    expect(definitiva).toBeChecked();
    expect(definitiva).toBeDisabled();
  });

  it('muestra si otra persona guardó en el medio', async () => {
    backend(CONTRATADO, () => problema(409, 'EVENTO_MODIFICADO', 'Otra persona modificó el evento mientras lo editabas. Volvé a abrir la ficha para ver los cambios.'));
    const { usuario, dialogo } = await abrir();

    await usuario.click(within(dialogo).getByRole('button', { name: 'Guardar cantidad' }));

    expect(await within(dialogo).findByText(/Otra persona modificó el evento/)).toBeInTheDocument();
  });

  it('no aparece para quien no puede modificar el evento', async () => {
    backend({ ...CONTRATADO, acciones: SIN_ACCIONES });
    montar('/eventos/5');

    await screen.findByRole('heading', { level: 1, name: 'Bruno y Martina' });
    expect(screen.queryByRole('button', { name: 'Cantidad de invitados' })).not.toBeInTheDocument();
  });
});
