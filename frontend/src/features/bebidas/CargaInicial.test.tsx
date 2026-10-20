import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Bebida } from '../../api/bebidas';
import { _reiniciarCliente } from '../../api/cliente';
import type { CargaInicial } from '../../api/inventarioInicial';
import type { Ubicacion } from '../../api/ubicaciones';
import { backendFalso, json, montar, pedidos, sesionDe, type Pedido } from '../../test/backendFalso';

const CAJA = { id: 1, nombre: 'Caja', esBotella: false };
const bebida = (id: number, nombre: string, extra: Partial<Bebida> = {}): Bebida => ({
  id, nombre, presentacion: '750 ml', tipo: { id: 3, nombre: 'Destilado' }, unidad: CAJA, unidadesPorBulto: 6, stockMinimo: null,
  codigos: [], activo: true, fechaBaja: null, ...extra,
});
const BEBIDAS: Bebida[] = [
  bebida(1, 'Fernet Branca'),
  bebida(2, 'Gin Bombay'),
  bebida(3, 'Ron discontinuado', { activo: false }),
];
const UBICACIONES: Ubicacion[] = [
  { id: 1, nombre: 'Depósito Avril', tipo: 'DEPOSITO', salon: null, abastecimiento: null, permiteRetiroDirecto: false, activo: true },
  { id: 2, nombre: 'Barra Avril', tipo: 'BARRA', salon: { id: 1, codigo: 'avril', nombre: 'Avril' },
    abastecimiento: { id: 1, nombre: 'Depósito Avril', tipo: 'DEPOSITO' }, permiteRetiroDirecto: false, activo: true },
];
const carga = (ubicacionId: number, extra: Partial<CargaInicial> = {}): CargaInicial => ({
  ubicacion: { id: ubicacionId, nombre: UBICACIONES.find((u) => u.id === ubicacionId)!.nombre }, cerrada: false, renglones: [],
  usuario: null, ultimaModificacion: null, ...extra,
});

function backend({ metodo, ruta, cuerpo }: Pedido) {
  if (metodo === 'GET' && ruta === '/bebidas') return json(200, BEBIDAS);
  if (metodo === 'GET' && ruta === '/ubicaciones') return json(200, UBICACIONES);
  if (metodo === 'GET' && ruta === '/inventario-inicial/1') return json(200, carga(1, { cerrada: true, renglones: [{ bebidaId: 1, cantidad: 87 }] }));
  if (metodo === 'GET' && ruta === '/inventario-inicial/2') return json(200, carga(2, { renglones: [{ bebidaId: 1, cantidad: 9 }] }));
  if (metodo === 'PUT' && ruta === '/inventario-inicial/2') {
    const { renglones } = cuerpo as { renglones: { bebidaId: number; cantidad: number }[] };
    return json(200, carga(2, {
      renglones: [{ bebidaId: 1, cantidad: 9 }, ...renglones].filter((r, i, todos) => todos.findLastIndex((x) => x.bebidaId === r.bebidaId) === i),
      usuario: { id: 4, nombre: 'Gabriela Paz' }, ultimaModificacion: '2026-10-07T09:30:00-03:00',
    }));
  }
  return undefined;
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-28 · carga inicial', () => {
  it('carga en cajas y botellas, guarda solo lo que cambió y deja retomar', async () => {
    const fetchMock = backendFalso(sesionDe(['ADMINISTRACION'], 'Gabriela Paz'), backend);
    const persona = userEvent.setup();
    montar('/catalogo?pestana=carga-inicial');

    await persona.selectOptions(await screen.findByLabelText('Ubicación'), '2');
    const fernet = await screen.findByRole('group', { name: 'Cantidad inicial de Fernet Branca' });
    expect(within(fernet).getByLabelText('Cajas de 6')).toHaveValue('1');
    expect(within(fernet).getByLabelText('Botellas sueltas')).toHaveValue('3');
    // Las dadas de baja sin nada cargado no aparecen.
    expect(screen.queryByRole('group', { name: 'Cantidad inicial de Ron discontinuado' })).not.toBeInTheDocument();

    const gin = screen.getByRole('group', { name: 'Cantidad inicial de Gin Bombay' });
    await persona.click(within(gin).getAllByRole('button', { name: 'Sumar 1' })[0]);
    await persona.click(within(gin).getAllByRole('button', { name: 'Sumar 1' })[0]);
    expect(screen.getByText(/Tenés 1 cambio sin guardar/)).toBeInTheDocument();
    await persona.click(screen.getByRole('button', { name: 'Guardar carga inicial' }));

    expect(await screen.findByText('Carga inicial de Barra Avril guardada.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toEqual({ renglones: [{ bebidaId: 2, cantidad: 12 }] });
    expect(screen.getByText(/guardó la carga/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar carga inicial' })).toBeDisabled();
  });

  it('una ubicación con otros movimientos se ve en solo lectura', async () => {
    backendFalso(sesionDe(['COMPRAS']), backend);
    const persona = userEvent.setup();
    montar('/catalogo?pestana=carga-inicial');

    await persona.selectOptions(await screen.findByLabelText('Ubicación'), '1');
    expect(await screen.findByText(/ya está cerrada porque tiene otros movimientos/)).toBeInTheDocument();
    expect(screen.getByText('14 cajas y 3 botellas')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar carga inicial' })).not.toBeInTheDocument();
  });

});
