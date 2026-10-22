import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import type { EventoResumen } from '../../api/eventos';
import { backendFalso, json, montar, pedidos, sesionDe } from '../../test/backendFalso';
import { OCTUBRE, PROXIMOS } from './fixtures';

const BEBIDA = { presentacion: '750 ml', tipo: 'Aperitivo', unidad: 'Caja', unidadesPorBulto: 6, activo: true };

function backend(roles: Rol[], nombre: string, id: number, eventos: EventoResumen[] = PROXIMOS) {
  return backendFalso(sesionDe(roles, nombre, id), ({ ruta }) => {
    if (ruta === '/agenda?mes=2026-10') return json(200, OCTUBRE);
    if (ruta.startsWith('/agenda?mes=')) return json(200, { ...OCTUBRE, mes: ruta.slice(-7), unidades: [] });
    if (ruta === '/eventos') return json(200, eventos);
    if (ruta.startsWith('/notificaciones?')) {
      return json(200, {
        notificaciones: [{ id: 1, tipo: 'SENA', mensaje: 'Seña registrada de Cumpleaños de Marta', eventoId: 12, fechaHora: '2026-10-20T18:42:00-03:00', leida: false }],
        pagina: 0, hayMas: false, sinLeer: 1,
      });
    }
    if (ruta === '/notificaciones/sin-leer') return json(200, { cantidad: 1 });
    if (ruta === '/stock/ubicaciones') return json(200, [{ ubicacion: { id: 1, nombre: 'Depósito madre', tipo: 'DEPOSITO' } }, { ubicacion: { id: 2, nombre: 'Barra Avril', tipo: 'BARRA' } }]);
    if (ruta === '/stock?ubicacionId=1') {
      return json(200, {
        ubicacion: { id: 1, nombre: 'Depósito madre' },
        renglones: [
          { bebida: { id: 1, nombre: 'Coca-Cola', ...BEBIDA }, cantidad: 108, estado: 'OK', stockMinimo: 24 },
          { bebida: { id: 2, nombre: 'Fernet Branca', ...BEBIDA }, cantidad: 9, estado: 'BAJO', stockMinimo: 12 },
        ],
      });
    }
    return undefined;
  });
}

beforeEach(() => {
  _reiniciarCliente();
  vi.useFakeTimers({ shouldAdvanceTime: true, now: new Date('2026-10-21T15:00:00Z') });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('UI-04 · Inicio', () => {
  it('Coordinación ve la semana del complejo, sus tareas, las notificaciones y lo que falta en el depósito madre', async () => {
    backend(['COORDINACION'], 'Sofía Paz', 2);
    montar('/inicio');

    expect(await screen.findByRole('heading', { level: 1, name: 'Hola, Sofía' })).toBeInTheDocument();
    expect(screen.getByText('Inicio · miércoles 21 de octubre')).toBeInTheDocument();
    const semana = (await screen.findByText('Esta semana')).closest('section')!;
    expect(within(semana).getByText('5')).toBeInTheDocument();
    expect(within(semana).getByRole('img', { name: /miércoles 0, jueves 1, viernes 0, sábado 2, domingo 1/ })).toBeInTheDocument();

    const tareas = screen.getByRole('heading', { name: 'Te toca a vos' }).closest('section')!;
    expect(await within(tareas).findByRole('button', { name: /Asignar planner.*Casamiento Paz/ })).toBeInTheDocument();
    expect(within(tareas).getByRole('button', { name: /Registrar firma de contrato.*Cumpleaños de Marta/ })).toBeInTheDocument();

    expect(await screen.findByText('Seña registrada de Cumpleaños de Marta')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Marcar todas como leídas' })).not.toBeInTheDocument();

    expect(await screen.findByText('Fernet Branca')).toBeInTheDocument();
    expect(screen.queryByText('Coca-Cola')).not.toBeInTheDocument();
  });

  it('Administración no tiene «Te toca a vos»', async () => {
    backend(['ADMINISTRACION'], 'Gabriela Luna', 4);
    montar('/inicio');

    expect(await screen.findByText('Fernet Branca')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Te toca a vos' })).not.toBeInTheDocument();
  });

  it('la vendedora entra al Inicio y ve sus eventos y sus señas pendientes, sin bebidas', async () => {
    const fetchMock = backend(['VENDEDORA'], 'Lucía Ferreyra', 1, PROXIMOS.filter((e) => e.vendedora.id === 1));
    const router = montar('/');

    expect(await screen.findByRole('heading', { level: 1, name: 'Hola, Lucía' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/inicio');
    const semana = (await screen.findByText('Tus eventos esta semana')).closest('section')!;
    expect(within(semana).getByText('3')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /Registrar seña.*Bautismo Ledesma/ })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Depósito madre' })).not.toBeInTheDocument();
    expect(pedidos(fetchMock).some((p) => p.ruta.startsWith('/stock'))).toBe(false);
  });

  it('la planner ve los contratados que tiene que confirmar', async () => {
    backend(['PLANNER'], 'Ana Sosa', 3);
    montar('/inicio');

    expect(await screen.findByRole('button', { name: /Confirmar evento.*Quince de Valentina/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Asignar planner/ })).not.toBeInTheDocument();
  });

  it('las solapas recorren hoy y los tres días siguientes, y cada evento abre su ficha', async () => {
    backend(['DIRECCION'], 'Marcela Ríos', 2);
    const usuario = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const router = montar('/inicio');

    const solapas = await screen.findByRole('tablist', { name: 'Próximos días' });
    expect(within(solapas).getAllByRole('tab').map((t) => t.textContent)).toEqual(['Hoy', 'Mañana', 'Vie 23', 'Sáb 24']);
    expect(await screen.findByText('No hay eventos este día.')).toBeInTheDocument();

    await usuario.click(within(solapas).getByRole('tab', { name: 'Sáb 24' }));
    expect(screen.getByRole('heading', { name: 'Sábado 24 de octubre' })).toBeInTheDocument();
    expect(screen.getByText('3 de 6 libres')).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: /Cumpleaños de Marta.*80 invitados · Planner: Ana Sosa/ }));
    expect(router.state.location.pathname).toBe('/eventos/12');
  });

  it('tocar un día del calendario abre la agenda en ese día', async () => {
    backend(['COMPRAS'], 'Pablo Díaz', 6);
    const usuario = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const router = montar('/inicio');

    await usuario.click(await screen.findByRole('button', { name: '30 de octubre: con reserva' }));
    expect(router.state.location.pathname).toBe('/agenda');
    expect(router.state.location.search).toBe('?mes=2026-10&dia=2026-10-30');
  });
});
