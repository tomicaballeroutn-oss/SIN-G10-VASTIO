import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Agenda } from '../../api/agenda';
import type { Motivo } from '../../api/catalogos';
import { _reiniciarCliente } from '../../api/cliente';
import type { Ficha } from '../../api/eventos';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';
import { fichaDePrueba, SIN_ACCIONES } from '../../test/fichas';

/** Evento contratado en Avril, noche del sábado 9 de marzo de 2030. */
const CONTRATADO = fichaDePrueba({
  estado: 'CONTRATADO',
  fecha: '2030-03-09',
  acciones: { ...SIN_ACCIONES, modificar: true, reprogramar: true },
});

const MARZO: Agenda = {
  mes: '2030-03',
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
    { fecha: '2030-03-09', salonId: 1, turnoId: 2, estado: 'CONTRATADO', evento: { id: 5, vendedora: { id: 1, nombre: 'Lucía Ferreyra' }, detalle: true } },
    { fecha: '2030-03-16', salonId: 2, turnoId: 2, estado: 'SENADO', evento: { vendedora: { id: 8, nombre: 'Sofía Méndez' }, detalle: false } },
  ],
};

const MOTIVOS: Motivo[] = [
  { id: 4, ambito: 'REPROGRAMACION', nombre: 'Pedido del cliente', activo: true },
  { id: 1, ambito: 'CANCELACION', nombre: 'Desistimiento del cliente', activo: true },
];

const REPROGRAMADO: Ficha = {
  ...CONTRATADO,
  fecha: '2030-03-16',
  salon: { id: 3, codigo: 'santa-barbara', nombre: 'Santa Bárbara', capacidad: null },
  reprogramadoDesde: 'Avril · sáb 9/3 · Noche',
  historial: [
    ...CONTRATADO.historial,
    {
      tipo: 'REPROGRAMACION', campo: 'unidad', valorAnterior: 'Avril · sáb 9/3 · Noche', valorNuevo: 'Santa Bárbara · sáb 16/3 · Noche',
      usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-10-05T10:00:00-03:00', observacion: 'Pedido del cliente',
    },
  ],
};

function backend(ficha: Ficha = CONTRATADO, alReprogramar: (p: Pedido) => Response = () => json(200, REPROGRAMADO)) {
  // Después de reprogramar, la ficha que se vuelve a pedir es la nueva.
  let actual = ficha;
  return backendFalso(sesionDe(['VENDEDORA']), (p) => {
    if (p.ruta === '/eventos/5' && p.metodo === 'GET') return json(200, actual);
    if (p.ruta === '/eventos/5/reprogramacion' && p.metodo === 'POST') {
      const respuesta = alReprogramar(p);
      if (respuesta.ok) actual = REPROGRAMADO;
      return respuesta;
    }
    if (p.ruta === '/agenda?mes=2030-03') return json(200, MARZO);
    if (p.ruta.startsWith('/agenda?mes=')) return json(200, { ...MARZO, mes: p.ruta.slice(-7), unidades: [] });
    if (p.ruta === '/motivos') return json(200, MOTIVOS);
    return undefined;
  });
}

async function abrirElDia16() {
  const usuario = userEvent.setup();
  montar('/eventos/5');
  await usuario.click(await screen.findByRole('button', { name: 'Reprogramar' }));
  expect(await screen.findByRole('heading', { level: 1, name: 'Reprogramar evento' })).toBeInTheDocument();
  await usuario.click(await screen.findByRole('button', { name: /^sábado 16 de marzo/ }));
  const dia = screen.getByRole('heading', { name: 'sábado 16 de marzo' }).closest('section') as HTMLElement;
  return { usuario, dia };
}

beforeEach(() => {
  _reiniciarCliente();
  vi.useFakeTimers({ toFake: ['Date'], now: new Date('2030-03-01T12:00:00Z') });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('UI-18 · reprogramar evento', () => {
  it('elige una unidad libre y el motivo, y vuelve a la ficha con la fecha original', async () => {
    const fetchMock = backend();
    const { usuario, dia } = await abrirElDia16();

    expect(within(dia).getByText('Señado: no se puede elegir.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmar reprogramación' })).toBeDisabled();
    await usuario.click(within(dia).getByRole('button', { name: 'Elegir Santa Bárbara · Noche' }));
    expect(screen.getByText('Santa Bárbara · sáb 16 mar 2030 · Noche')).toBeInTheDocument();
    await usuario.selectOptions(screen.getByLabelText('Motivo de la reprogramación'), 'Pedido del cliente');
    await usuario.click(screen.getByRole('button', { name: 'Confirmar reprogramación' }));

    expect(await screen.findByText('Evento reprogramado: Santa Bárbara · sáb 16 mar 2030 · Noche.')).toBeInTheDocument();
    expect(screen.getByText(/Reprogramado. Fecha original: Avril · sáb 9\/3 · Noche/)).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toEqual({
      salonId: 3, fecha: '2030-03-16', turnoId: 2, motivoId: 4, detalle: '',
    });
  });

  it('la fecha actual no se puede elegir', async () => {
    backend();
    const usuario = userEvent.setup();
    montar('/eventos/5/reprogramar');

    await usuario.click(await screen.findByRole('button', { name: /^sábado 9 de marzo/ }));
    const dia = screen.getByRole('heading', { name: 'sábado 9 de marzo' }).closest('section') as HTMLElement;
    expect(within(dia).getByText('Es la fecha actual del evento.')).toBeInTheDocument();
    expect(within(dia).queryByRole('button', { name: 'Elegir Avril · Noche' })).not.toBeInTheDocument();
  });

  it('pide el motivo antes de confirmar', async () => {
    const fetchMock = backend();
    const { usuario, dia } = await abrirElDia16();

    await usuario.click(within(dia).getByRole('button', { name: 'Elegir Avril · Mediodía' }));
    await usuario.click(screen.getByRole('button', { name: 'Confirmar reprogramación' }));

    expect(screen.getByLabelText('Motivo de la reprogramación')).toHaveAccessibleDescription('Elegí el motivo de la reprogramación.');
    expect(pedidos(fetchMock).some((p) => p.metodo === 'POST')).toBe(false);
  });

  it('en pre-reserva no pide motivo', async () => {
    const fetchMock = backend({ ...CONTRATADO, estado: 'PRE_RESERVA' }, () => json(200, { ...REPROGRAMADO, estado: 'PRE_RESERVA' }));
    const { usuario, dia } = await abrirElDia16();

    expect(screen.queryByLabelText('Motivo de la reprogramación')).not.toBeInTheDocument();
    await usuario.click(within(dia).getByRole('button', { name: 'Elegir Club de Campo · Mediodía' }));
    await usuario.click(screen.getByRole('button', { name: 'Confirmar reprogramación' }));

    await screen.findByText(/Evento reprogramado/);
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toMatchObject({ motivoId: null });
  });

  it('si alguien tomó la unidad en el medio, avisa y pide elegir otra', async () => {
    const fetchMock = backend(CONTRATADO, () => problema(409, 'FECHA_TOMADA', 'Esa fecha ya está tomada. Elegí otro salón, otra fecha u otro turno.'));
    const { usuario, dia } = await abrirElDia16();

    await usuario.click(within(dia).getByRole('button', { name: 'Elegir Santa Bárbara · Noche' }));
    await usuario.selectOptions(screen.getByLabelText('Motivo de la reprogramación'), 'Pedido del cliente');
    await usuario.click(screen.getByRole('button', { name: 'Confirmar reprogramación' }));

    expect(await screen.findByText('Esa unidad ya no está libre')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmar reprogramación' })).toBeDisabled();
    expect(pedidos(fetchMock).filter((p) => p.ruta === '/agenda?mes=2030-03').length).toBeGreaterThan(1);
  });

  it('el historial muestra la reprogramación', async () => {
    backend(REPROGRAMADO);
    montar('/eventos/5?pestana=historial');

    expect(await screen.findByText('Evento reprogramado')).toBeInTheDocument();
    expect(screen.getByText('Avril · sáb 9/3 · Noche → Santa Bárbara · sáb 16/3 · Noche. Motivo: Pedido del cliente.')).toBeInTheDocument();
  });

  it('no aparece para quien no puede reprogramar', async () => {
    backend({ ...CONTRATADO, acciones: { ...SIN_ACCIONES, modificar: true } });
    montar('/eventos/5');

    await screen.findByRole('heading', { level: 1, name: 'Bruno y Martina' });
    expect(screen.queryByRole('button', { name: 'Reprogramar' })).not.toBeInTheDocument();
  });
});
