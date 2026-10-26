import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import type { Asistencia, Ficha } from '../../api/eventos';
import { backendFalso, json, montar, pedidos, sesionDe, type Pedido } from '../../test/backendFalso';
import { fichaDePrueba, SIN_ACCIONES } from '../../test/fichas';

const ASISTENCIA: Asistencia = {
  segmentos: [
    { id: 1, nombre: 'Comensales cena', activo: true, equivaleInvitados: true, prevista: 140, real: null },
    { id: 2, nombre: 'Entradas anticipadas', activo: true, equivaleInvitados: false, prevista: null, real: null },
    { id: 3, nombre: 'Venta en puerta', activo: true, equivaleInvitados: false, prevista: null, real: null },
  ],
  totalPrevisto: 140,
  totalReal: null,
};

const EGRESADOS = fichaDePrueba({
  estado: 'CONTRATADO',
  version: 6,
  tipo: { id: 4, nombre: 'Egresados' },
  nombre: 'Egresados 5to B',
  cantidadInvitados: 140,
  asistencia: ASISTENCIA,
  acciones: { ...SIN_ACCIONES, registrarAsistenciaPrevista: true },
});

const GUARDADA: Ficha = {
  ...EGRESADOS,
  version: 7,
  asistencia: {
    segmentos: ASISTENCIA.segmentos.map((s) => (s.id === 2 ? { ...s, prevista: 60 } : s)),
    totalPrevisto: 200,
  },
  historial: [
    ...EGRESADOS.historial,
    {
      tipo: 'MODIFICACION', campo: 'asistencia_prevista.Entradas anticipadas', valorNuevo: '60',
      usuario: { id: 3, nombre: 'Ana Sosa' }, fechaHora: '2026-10-26T18:00:00-03:00',
    },
  ],
};

function backend(ficha: Ficha = EGRESADOS, alGuardar: (p: Pedido) => Response = () => json(200, GUARDADA)) {
  return backendFalso(sesionDe(['PLANNER']), (p) => {
    if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, ficha);
    if (p.metodo === 'PUT' && p.ruta === '/eventos/5/asistencia/prevista') return alGuardar(p);
    return undefined;
  });
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-19 · asistencia prevista por segmento', () => {
  it('muestra los segmentos con los comensales tomados de los invitados', async () => {
    backend();
    montar('/eventos/5?pestana=asistencia');

    const tabla = await screen.findByRole('table', { name: 'Asistencia por segmento' });
    expect(within(tabla).getByText('Comensales cena (los invitados)')).toBeInTheDocument();
    expect(within(tabla).getByText('140')).toBeInTheDocument();
  });

  it('en los datos, los invitados son los comensales de la cena', async () => {
    backend();
    montar('/eventos/5');

    expect(await screen.findByText('· comensales de la cena', { exact: false })).toBeInTheDocument();
  });

  it('registra la prevista de la fiesta y suma el total', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=asistencia');

    await usuario.click(await screen.findByRole('button', { name: 'Registrar asistencia prevista' }));
    const dialogo = screen.getByRole('dialog', { name: 'Asistencia prevista' });
    expect(within(dialogo).getByText(/Es la cantidad de invitados/)).toBeInTheDocument();
    expect(within(dialogo).queryByLabelText(/Comensales cena/)).not.toBeInTheDocument();
    await usuario.type(within(dialogo).getByLabelText(/Entradas anticipadas/), '60');
    expect(within(dialogo).getByText('200')).toBeInTheDocument();
    await usuario.click(within(dialogo).getByRole('button', { name: 'Guardar asistencia' }));

    expect(await screen.findByText('Asistencia prevista guardada.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toEqual({
      version: 6,
      segmentos: [{ segmentoId: 2, cantidad: 60 }, { segmentoId: 3, cantidad: null }],
    });
  });

  it('rechaza una cantidad que no es un entero', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=asistencia');

    await usuario.click(await screen.findByRole('button', { name: 'Registrar asistencia prevista' }));
    const dialogo = screen.getByRole('dialog', { name: 'Asistencia prevista' });
    await usuario.type(within(dialogo).getByLabelText(/Venta en puerta/), '1,5');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Guardar asistencia' }));

    expect(within(dialogo).getByText('Escribí un número entero de 0 a 5000, o dejalo vacío.')).toBeInTheDocument();
    expect(pedidos(fetchMock).some((p) => p.metodo === 'PUT')).toBe(false);
  });

  it('el historial nombra el segmento que cambió', async () => {
    backend(GUARDADA);
    montar('/eventos/5?pestana=historial');

    expect(await screen.findByText('Asistencia prevista de Entradas anticipadas: sin dato → 60.')).toBeInTheDocument();
  });

  it('sin segmentos no hay pestaña', async () => {
    backend(fichaDePrueba());
    montar('/eventos/5?pestana=asistencia');

    expect(await screen.findByRole('tab', { name: /Datos/ })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /Asistencia/ })).not.toBeInTheDocument();
  });
});

describe('UI-20 · asistencia real de cierre', () => {
  const REALIZADO = fichaDePrueba({
    ...EGRESADOS,
    estado: 'REALIZADO',
    asistencia: {
      segmentos: ASISTENCIA.segmentos.map((s) => (s.id === 2 ? { ...s, prevista: 60 } : s)),
      totalPrevisto: 200,
      totalReal: null,
    },
    acciones: { ...SIN_ACCIONES, registrarAsistenciaReal: true },
  });
  const CARGADA: Ficha = {
    ...REALIZADO,
    version: 7,
    asistencia: {
      segmentos: REALIZADO.asistencia!.segmentos.map((s) => ({ ...s, real: { 1: 132, 2: 58, 3: 14 }[s.id] ?? null })),
      totalPrevisto: 200,
      totalReal: 204,
    },
  };

  function backendReal() {
    return backendFalso(sesionDe(['PLANNER']), (p) => {
      if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, REALIZADO);
      if (p.metodo === 'PUT' && p.ruta === '/eventos/5/asistencia/real') return json(200, CARGADA);
      return undefined;
    });
  }

  it('avisa que falta y carga la real de todos los segmentos', async () => {
    const fetchMock = backendReal();
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=asistencia');

    expect(await screen.findByText('Falta la asistencia real')).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Registrar asistencia real' }));
    const dialogo = screen.getByRole('dialog', { name: 'Asistencia real de cierre' });
    expect(within(dialogo).getByText('Previsto: 60')).toBeInTheDocument();
    await usuario.type(within(dialogo).getByLabelText(/Comensales cena/), '132');
    await usuario.type(within(dialogo).getByLabelText(/Entradas anticipadas/), '58');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Guardar asistencia real' }));
    // Falta la venta en puerta: no se manda nada.
    expect(within(dialogo).getByText('Escribí un número entero de 0 a 5000.')).toBeInTheDocument();
    expect(pedidos(fetchMock).some((p) => p.metodo === 'PUT')).toBe(false);

    await usuario.type(within(dialogo).getByLabelText(/Venta en puerta/), '14');
    expect(within(dialogo).getByText('204')).toBeInTheDocument();
    await usuario.click(within(dialogo).getByRole('button', { name: 'Guardar asistencia real' }));

    expect(await screen.findByText(/Asistencia real guardada/)).toBeInTheDocument();
    expect(screen.queryByText('Falta la asistencia real')).not.toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toEqual({
      version: 6,
      segmentos: [{ segmentoId: 1, cantidad: 132 }, { segmentoId: 2, cantidad: 58 }, { segmentoId: 3, cantidad: 14 }],
    });
  });
});
