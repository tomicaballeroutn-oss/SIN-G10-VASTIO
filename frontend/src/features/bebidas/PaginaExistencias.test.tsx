import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import type { Existencias, RenglonStock, UbicacionConsultable } from '../../api/stock';
import { backendFalso, json, montar, pedidos, sesionDe, type Pedido } from '../../test/backendFalso';

const bebida = (id: number, nombre: string, extra: Partial<RenglonStock['bebida']> = {}): RenglonStock['bebida'] => ({
  id, nombre, presentacion: '750 ml', tipo: 'Destilado', unidad: 'Caja', unidadesPorBulto: 6, activo: true, ...extra,
});

const DEPOSITO: UbicacionConsultable = { ubicacion: { id: 1, nombre: 'Depósito Avril', tipo: 'DEPOSITO' } };
const BARRA_AVRIL: UbicacionConsultable = { ubicacion: { id: 2, nombre: 'Barra Avril', tipo: 'BARRA' } };

const EN_DEPOSITO: Existencias = {
  ubicacion: { id: 1, nombre: 'Depósito Avril' },
  renglones: [
    { bebida: bebida(1, 'Fernet Branca'), cantidad: 10, estado: 'BAJO', stockMinimo: 12 },
    { bebida: bebida(2, 'Gin Bombay'), cantidad: -3, estado: 'NEGATIVO', stockMinimo: null },
    { bebida: bebida(3, 'Vodka'), cantidad: 0, estado: 'SIN_STOCK', stockMinimo: null },
  ],
};
const TOTAL: Existencias = { ubicacion: null, renglones: [{ bebida: bebida(1, 'Fernet Branca'), cantidad: 87, estado: 'OK', stockMinimo: 12 }] };
const EN_BARRA: Existencias = { ubicacion: { id: 2, nombre: 'Barra Avril' }, renglones: [{ bebida: bebida(1, 'Fernet Branca'), cantidad: 9 }] };

function backend(ubicaciones: UbicacionConsultable[]) {
  return ({ metodo, ruta }: Pedido) => {
    if (metodo !== 'GET') return undefined;
    if (ruta === '/stock/ubicaciones') return json(200, ubicaciones);
    if (ruta === '/stock?ubicacionId=1') return json(200, EN_DEPOSITO);
    if (ruta === '/stock?ubicacionId=2') return json(200, EN_BARRA);
    if (ruta === '/stock') return json(200, TOTAL);
    return undefined;
  };
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('UI-27 · consultar stock', () => {
  it('Compras ve el depósito con estados en cajas y botellas, y el total del complejo', async () => {
    const fetchMock = backendFalso(sesionDe(['COMPRAS']), backend([DEPOSITO, BARRA_AVRIL]));
    const persona = userEvent.setup();
    montar('/existencias');

    const lista = await screen.findByRole('list', { name: 'Existencias por bebida' });
    expect(within(lista).getByText('1 caja y 4 botellas')).toBeInTheDocument();
    expect(within(lista).getByText('Stock bajo')).toBeInTheDocument();
    expect(within(lista).getByText('Falta registrar un movimiento')).toBeInTheDocument();
    expect(within(lista).getByText('−3 botellas')).toBeInTheDocument();
    expect(within(lista).getByText('Sin stock')).toBeInTheDocument();

    await persona.selectOptions(screen.getByLabelText('Ubicación'), 'todas');
    expect(await screen.findByText('14 cajas y 3 botellas')).toBeInTheDocument();
    expect(pedidos(fetchMock).some((p) => p.ruta === '/stock')).toBe(true);

    await persona.type(screen.getByLabelText('Buscar bebida'), 'gin');
    expect(screen.getByText('Ninguna bebida coincide con «gin».')).toBeInTheDocument();
  });

  it('la encargada de barra elige entre sus barras de la jornada y no ve estados', async () => {
    const conEvento = { ...BARRA_AVRIL, evento: { id: 5, codigo: 'EV-2026-00005', nombre: 'Quince de Sofía' } };
    backendFalso(sesionDe(['BARRA'], 'Rocío Blanco'), backend([conEvento]));
    montar('/existencias');

    expect(await screen.findByText('1 caja y 3 botellas')).toBeInTheDocument();
    const selector = screen.getByLabelText('Ubicación');
    expect(within(selector).getByRole('option', { name: 'Barra Avril · Quince de Sofía' })).toBeInTheDocument();
    expect(within(selector).queryByRole('option', { name: /Todas/ })).not.toBeInTheDocument();
    expect(screen.queryByText('En stock')).not.toBeInTheDocument();
  });

  it('sin eventos en la jornada, la barra lo ve dicho', async () => {
    backendFalso(sesionDe(['BARRA']), backend([]));
    montar('/existencias');

    expect(await screen.findByText('Hoy no hay eventos en tus barras')).toBeInTheDocument();
  });

  it('se actualiza sola cada minuto', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const fetchMock = backendFalso(sesionDe(['ADMINISTRACION']), backend([DEPOSITO]));
    montar('/existencias');

    await screen.findByText('1 caja y 4 botellas');
    const antes = pedidos(fetchMock).filter((p) => p.ruta === '/stock?ubicacionId=1').length;
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });
    expect(pedidos(fetchMock).filter((p) => p.ruta === '/stock?ubicacionId=1').length).toBe(antes + 1);
  });
});
