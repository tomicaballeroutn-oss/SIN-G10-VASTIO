import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Bebida } from '../../api/bebidas';
import { _reiniciarCliente } from '../../api/cliente';
import type { CargaInicial } from '../../api/inventarioInicial';
import type { Proveedor } from '../../api/proveedores';
import type { Ubicacion } from '../../api/ubicaciones';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';

const CAJA = { id: 1, nombre: 'Caja', esBotella: false };
const CENTRO = { id: 1, razonSocial: 'Distribuidora del Centro', activo: true };
const bebida = (id: number, nombre: string, extra: Partial<Bebida> = {}): Bebida => ({
  id, nombre, presentacion: '750 ml', tipo: { id: 3, nombre: 'Destilado' }, unidad: CAJA, unidadesPorBulto: 6, stockMinimo: null,
  codigos: [], proveedorHabitual: null, activo: true, fechaBaja: null, ...extra,
});
const BEBIDAS: Bebida[] = [
  bebida(1, 'Fernet Branca', { proveedorHabitual: CENTRO }),
  bebida(2, 'Gin Bombay'),
  bebida(3, 'Ron discontinuado', { activo: false }),
];
const PROVEEDORES: Proveedor[] = [
  { id: 1, razonSocial: 'Distribuidora del Centro', cuit: '30711222339', telefono: '351 555-0101', email: null, activo: true,
    bebidas: [{ id: 1, nombre: 'Fernet Branca', presentacion: '750 ml' }] },
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
  if (metodo === 'GET' && ruta === '/proveedores') return json(200, PROVEEDORES);
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
  if (metodo === 'POST' && ruta === '/proveedores') {
    const datos = cuerpo as { razonSocial: string; cuit: string };
    if (datos.cuit === '30-71122233-0') return problema(422, 'CUIT_INVALIDO', 'Revisá el CUIT: tiene que tener 11 números y el último no coincide con el dígito verificador.');
    return json(201, { ...PROVEEDORES[0], id: 2, razonSocial: datos.razonSocial, cuit: null, bebidas: [] });
  }
  if (metodo === 'PUT' && ruta === '/proveedores/2/bebidas') return json(200, { ...PROVEEDORES[0], id: 2, razonSocial: 'Bodegas del Norte', bebidas: [] });
  if (metodo === 'POST' && ruta === '/proveedores/1/baja') return json(200, { ...PROVEEDORES[0], activo: false });
  return undefined;
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-28 · carga inicial (paso 1)', () => {
  it('carga en cajas y botellas, guarda solo lo que cambió y deja retomar', async () => {
    const fetchMock = backendFalso(sesionDe(['ADMINISTRACION'], 'Gabriela Paz'), backend);
    const persona = userEvent.setup();
    montar('/catalogo?pestana=carga-inicial');

    expect(await screen.findByText('Paso 1 de 2')).toBeInTheDocument();
    await persona.selectOptions(screen.getByLabelText('Ubicación'), '2');
    const fernet = await screen.findByRole('group', { name: 'Cantidad inicial de Fernet Branca' });
    expect(within(fernet).getByLabelText('Cajas de 6')).toHaveValue('1');
    expect(within(fernet).getByLabelText('Botellas sueltas')).toHaveValue('3');
    // Las dadas de baja sin nada cargado no aparecen.
    expect(screen.queryByRole('group', { name: 'Cantidad inicial de Ron discontinuado' })).not.toBeInTheDocument();

    const gin = screen.getByRole('group', { name: 'Cantidad inicial de Gin Bombay' });
    await persona.click(within(gin).getAllByRole('button', { name: 'Sumar 1' })[0]);
    await persona.click(within(gin).getAllByRole('button', { name: 'Sumar 1' })[0]);
    expect(screen.getByText(/Tenés 1 cambio sin guardar/)).toBeInTheDocument();
    await persona.click(screen.getByRole('button', { name: 'Guardar y continuar después' }));

    expect(await screen.findByText('Carga inicial de Barra Avril guardada.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toEqual({ renglones: [{ bebidaId: 2, cantidad: 12 }] });
    expect(screen.getByText(/guardó la carga/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Guardar y continuar después' })).toBeDisabled();
  });

  it('una ubicación con otros movimientos se ve en solo lectura', async () => {
    backendFalso(sesionDe(['COMPRAS']), backend);
    const persona = userEvent.setup();
    montar('/catalogo?pestana=carga-inicial');

    await persona.selectOptions(await screen.findByLabelText('Ubicación'), '1');
    expect(await screen.findByText(/ya está cerrada porque tiene otros movimientos/)).toBeInTheDocument();
    expect(screen.getByText('14 cajas y 3 botellas')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar y continuar después' })).not.toBeInTheDocument();
  });

  it('«Siguiente: proveedores» pasa al paso 2', async () => {
    backendFalso(sesionDe(['COMPRAS']), backend);
    const persona = userEvent.setup();
    montar('/catalogo?pestana=carga-inicial');

    await persona.selectOptions(await screen.findByLabelText('Ubicación'), '2');
    await persona.click(await screen.findByRole('button', { name: /Siguiente: proveedores/ }));
    expect(await screen.findByRole('button', { name: 'Nuevo proveedor' })).toBeInTheDocument();
  });
});

describe('UI-28 · proveedores (paso 2)', () => {
  it('lista los proveedores con su CUIT y las bebidas que proveen', async () => {
    backendFalso(sesionDe(['COMPRAS']), backend);
    montar('/catalogo?pestana=proveedores');

    const fila = (await screen.findByText('Distribuidora del Centro')).closest('tr') as HTMLElement;
    expect(within(fila).getByText('30-71122233-9')).toBeInTheDocument();
    expect(within(fila).getByText('Fernet Branca')).toBeInTheDocument();
  });

  it('da de alta un proveedor, avisa qué bebidas cambian de proveedor y las fija', async () => {
    const fetchMock = backendFalso(sesionDe(['ADMINISTRACION']), backend);
    const persona = userEvent.setup();
    montar('/catalogo?pestana=proveedores');

    await persona.click(await screen.findByRole('button', { name: 'Nuevo proveedor' }));
    const dialogo = screen.getByRole('dialog', { name: 'Nuevo proveedor' });
    await persona.type(within(dialogo).getByLabelText('Razón social'), 'Bodegas del Norte');
    expect(within(dialogo).queryByLabelText(/Ron discontinuado/)).not.toBeInTheDocument();
    await persona.click(within(dialogo).getByLabelText(/Fernet Branca 750 ml/));
    await persona.click(within(dialogo).getByLabelText(/Gin Bombay 750 ml/));
    expect(within(dialogo).getByText('Fernet Branca pasa de Distribuidora del Centro a este proveedor.')).toBeInTheDocument();
    await persona.click(within(dialogo).getByRole('button', { name: 'Cargar proveedor' }));

    expect(await screen.findByText('Proveedor cargado: Bodegas del Norte.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT' && p.ruta === '/proveedores/2/bebidas')?.cuerpo).toEqual({ bebidaIds: [1, 2] });
  });

  it('muestra el error del CUIT', async () => {
    backendFalso(sesionDe(['COMPRAS']), backend);
    const persona = userEvent.setup();
    montar('/catalogo?pestana=proveedores');

    await persona.click(await screen.findByRole('button', { name: 'Nuevo proveedor' }));
    const dialogo = screen.getByRole('dialog', { name: 'Nuevo proveedor' });
    await persona.type(within(dialogo).getByLabelText('Razón social'), 'Distribuidora mal cargada');
    await persona.type(within(dialogo).getByLabelText(/CUIT/), '30-71122233-0');
    await persona.click(within(dialogo).getByRole('button', { name: 'Cargar proveedor' }));

    expect(await within(dialogo).findByText(/el último no coincide con el dígito verificador/)).toBeInTheDocument();
  });

  it('da de baja un proveedor', async () => {
    backendFalso(sesionDe(['COMPRAS']), backend);
    const persona = userEvent.setup();
    montar('/catalogo?pestana=proveedores');

    const fila = (await screen.findByText('Distribuidora del Centro')).closest('tr') as HTMLElement;
    await persona.click(within(fila).getByRole('button', { name: 'Dar de baja' }));
    expect(await screen.findByText(/quedó dado de baja. Sus bebidas lo conservan como habitual/)).toBeInTheDocument();
  });
});
