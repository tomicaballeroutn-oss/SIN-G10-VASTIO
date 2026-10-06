import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import type { Ficha, RequisitoConfirmacion } from '../../api/eventos';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';
import { fichaDePrueba, SIN_ACCIONES } from '../../test/fichas';

const CUMPLIDOS: RequisitoConfirmacion[] = [
  { codigo: 'PLANNER', titulo: 'Planner asignada', cumplido: true, detalle: 'Ana Sosa' },
  { codigo: 'INVITADOS', titulo: 'Cantidad definitiva de invitados', cumplido: true, detalle: '180' },
  { codigo: 'SERVICIOS', titulo: 'Plato principal y Tipo de barra', cumplido: true, detalle: 'Cargado' },
  { codigo: 'FECHA', titulo: 'Fecha del evento', cumplido: true, detalle: 'Todavía no pasó.' },
];

const CONTRATADO = fichaDePrueba({
  estado: 'CONTRATADO',
  requisitosConfirmacion: CUMPLIDOS,
  acciones: { ...SIN_ACCIONES, modificar: true, confirmar: true },
});

const CONFIRMADO: Ficha = {
  ...CONTRATADO,
  estado: 'CONFIRMADO',
  requisitosConfirmacion: null,
  acciones: { ...SIN_ACCIONES, modificar: true },
  historial: [
    ...CONTRATADO.historial,
    { tipo: 'ESTADO', estadoAnterior: 'CONTRATADO', estadoNuevo: 'CONFIRMADO', usuario: { id: 3, nombre: 'Ana Sosa' }, fechaHora: '2026-10-05T10:00:00-03:00' },
  ],
};

function backend(ficha: Ficha = CONTRATADO, alConfirmar: (p: Pedido) => Response = () => json(200, CONFIRMADO)) {
  return backendFalso(sesionDe(['PLANNER'], 'Ana Sosa', 3), (p) => {
    if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, ficha);
    if (p.metodo === 'POST' && p.ruta === '/eventos/5/confirmacion') return alConfirmar(p);
    return undefined;
  });
}

async function abrir() {
  const usuario = userEvent.setup();
  montar('/eventos/5');
  await usuario.click(await screen.findByRole('button', { name: 'Confirmar evento' }));
  return { usuario, dialogo: screen.getByRole('dialog', { name: 'Confirmar evento' }) };
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-15 · confirmar evento', () => {
  it('con todo cumplido confirma y la ficha pasa a Confirmado', async () => {
    const fetchMock = backend();
    const { usuario, dialogo } = await abrir();

    const lista = within(dialogo).getByRole('list', { name: 'Requisitos para confirmar' });
    expect(within(lista).getAllByRole('listitem')).toHaveLength(4);
    expect(within(lista).getByText('Ana Sosa')).toBeInTheDocument();
    await usuario.click(within(dialogo).getByRole('button', { name: 'Confirmar evento' }));

    expect(await screen.findByText('Evento confirmado. Se avisó a Compras y Cocina.')).toBeInTheDocument();
    expect(screen.getByText('Confirmado')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmar evento' })).not.toBeInTheDocument();
    expect(pedidos(fetchMock).some((p) => p.metodo === 'POST' && p.ruta === '/eventos/5/confirmacion')).toBe(true);
  });

  it('si falta algo, la fila dice qué y el botón queda deshabilitado', async () => {
    backend({
      ...CONTRATADO,
      requisitosConfirmacion: CUMPLIDOS.map((r) => (r.codigo === 'SERVICIOS'
        ? { ...r, cumplido: false, detalle: 'Falta cargar Tipo de barra.' } : r)),
    });
    const { dialogo } = await abrir();

    const fila = within(dialogo).getByText('Falta cargar Tipo de barra.').closest('li') as HTMLElement;
    expect(fila).toHaveTextContent('Pendiente: Falta cargar Tipo de barra.');
    expect(within(dialogo).getByRole('button', { name: 'Confirmar evento' })).toBeDisabled();
    expect(within(dialogo).getByText('Completá lo que falta para poder confirmar el evento.')).toBeInTheDocument();
  });

  it('muestra lo que rechaza el sistema', async () => {
    backend(CONTRATADO, () => problema(422, 'REQUISITOS_PENDIENTES', 'Todavía no se puede confirmar. Falta asignar la planner.'));
    const { usuario, dialogo } = await abrir();

    await usuario.click(within(dialogo).getByRole('button', { name: 'Confirmar evento' }));

    expect(await within(dialogo).findByText('Todavía no se puede confirmar. Falta asignar la planner.')).toBeInTheDocument();
  });

  it('el historial muestra la confirmación', async () => {
    backend(CONFIRMADO);
    montar('/eventos/5?pestana=historial');

    expect(await screen.findByText('Evento confirmado')).toBeInTheDocument();
  });
});
