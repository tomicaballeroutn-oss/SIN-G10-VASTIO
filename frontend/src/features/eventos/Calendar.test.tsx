import { screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import { backendFalso, json, montar, pedidos, sesionDe } from '../../test/backendFalso';
import { fichaDePrueba } from '../../test/fichas';
import { itemsPara } from '../layout/menu';

beforeEach(_reiniciarCliente);
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe('retiro de Google Calendar', () => {
  it('Dirección y Coordinación conservan Agenda en el menú sin la opción de Calendar', () => {
    for (const rol of ['DIRECCION', 'COORDINACION'] as const) {
      const items = itemsPara([rol]);
      expect(items.some((item) => item.ruta === '/agenda')).toBe(true);
      expect(items.some((item) => item.id === 'calendar-pendientes' || item.ruta === '/calendar/pendientes')).toBe(false);
    }
  });

  it('la ficha carga el evento sin controles ni solicitudes de Calendar', async () => {
    const fetch = backendFalso(sesionDe(['DIRECCION']), ({ ruta }) =>
      ruta === '/eventos/5' ? json(200, fichaDePrueba()) : undefined);
    montar('/eventos/5');

    expect(await screen.findByRole('heading', { name: 'Bruno y Martina' })).toBeInTheDocument();
    expect(screen.queryByText(/Google Calendar/i)).not.toBeInTheDocument();
    expect(pedidos(fetch).filter(({ ruta }) => /\/calendar(?:\/|$)/.test(ruta))).toEqual([]);
  });

  it('un enlace a la antigua ruta lleva al Inicio sin consultar Calendar', async () => {
    const fetch = backendFalso(sesionDe(['DIRECCION']));
    const router = montar('/calendar/pendientes');

    await vi.waitFor(() => expect(router.state.location.pathname).toBe('/inicio'));
    expect(screen.queryByText('Pendientes de Google Calendar')).not.toBeInTheDocument();
    expect(pedidos(fetch).filter(({ ruta }) => /\/calendar(?:\/|$)/.test(ruta))).toEqual([]);
  });
});
