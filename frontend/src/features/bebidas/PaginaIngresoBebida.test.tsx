import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Bebida } from '../../api/bebidas';
import { _reiniciarCliente } from '../../api/cliente';
import type { Ingreso } from '../../api/ingresos';
import type { Ubicacion } from '../../api/ubicaciones';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';

/** La cámara: el test decide qué código se lee. */
const camara = vi.hoisted(() => ({ leer: (() => undefined) as (codigo: string) => void }));
vi.mock('@zxing/browser', () => ({
  BrowserMultiFormatReader: class {
    decodeFromConstraints(_c: unknown, _v: unknown, alLeer: (r: { getText: () => string }) => void) {
      camara.leer = (codigo) => alLeer({ getText: () => codigo });
      return Promise.resolve({ stop: () => undefined });
    }
  },
}));

const CAJA = { id: 1, nombre: 'Caja', esBotella: false };
const bebida = (id: number, nombre: string, extra: Partial<Bebida> = {}): Bebida => ({
  id, nombre, presentacion: '750 ml', tipo: { id: 3, nombre: 'Destilado' }, unidad: CAJA, unidadesPorBulto: 6, stockMinimo: null,
  codigos: [], proveedorHabitual: null, activo: true, fechaBaja: null, ...extra,
});
const BEBIDAS: Bebida[] = [
  bebida(1, 'Fernet Branca', { codigos: [{ codigo: '17790000000016', unidades: 6 }] }),
  bebida(2, 'Cerveza Quilmes', { presentacion: '1 l', unidadesPorBulto: 12 }),
  bebida(3, 'Gin discontinuado', { activo: false, codigos: [{ codigo: '7790000000064', unidades: 1 }] }),
];
const DEPOSITO: Ubicacion = { id: 1, nombre: 'Depósito Avril', tipo: 'DEPOSITO', salon: null, abastecimiento: null, permiteRetiroDirecto: false, activo: true };
const ANTERIOR: Ingreso = {
  id: 7, fechaIngreso: '2026-10-01', numeroRemito: 'R-0001', destino: { id: 1, nombre: 'Depósito Avril' },
  renglones: [{ bebidaId: 2, nombre: 'Cerveza Quilmes', presentacion: '1 l', unidad: 'Caja', unidadesPorBulto: 12, cantidad: 48 }],
  usuario: { id: 5, nombre: 'Nicolás Herrera' }, fechaRegistro: '2026-10-01T10:15:00-03:00',
};

function backend(conDeposito = true) {
  return ({ metodo, ruta, cuerpo }: Pedido) => {
    if (metodo === 'GET' && ruta === '/bebidas') return json(200, BEBIDAS);
    if (metodo === 'GET' && ruta === '/ubicaciones') return json(200, conDeposito ? [DEPOSITO] : []);
    if (metodo === 'GET' && ruta === '/ingresos?limite=10') return json(200, [ANTERIOR]);
    if (metodo === 'POST' && ruta === '/ingresos') {
      const datos = cuerpo as { numeroRemito: string; renglones: { bebidaId: number; cantidad: number }[] };
      if (datos.numeroRemito === 'falla') return problema(422, 'BEBIDA_DADA_DE_BAJA', 'Fernet Branca 750 ml está dada de baja. Sacala del ingreso o reactivala en el catálogo.');
      return json(201, {
        ...ANTERIOR, id: 8, numeroRemito: datos.numeroRemito, fechaRegistro: '2026-10-07T09:00:00-03:00', usuario: { id: 1, nombre: 'Gabriela Paz' },
        renglones: datos.renglones.map((r) => ({ ...ANTERIOR.renglones[0], bebidaId: r.bebidaId, nombre: BEBIDAS.find((b) => b.id === r.bebidaId)!.nombre, cantidad: r.cantidad })),
      });
    }
    return undefined;
  };
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
  Reflect.deleteProperty(navigator, 'mediaDevices');
});

async function agregarPorNombre(persona: ReturnType<typeof userEvent.setup>, texto: string, opcion: string) {
  await persona.type(screen.getByLabelText('Agregar bebida'), texto);
  await persona.click(await screen.findByRole('option', { name: new RegExp(opcion) }));
}

describe('UI-29 · ingreso de mercadería', () => {
  it('entra al depósito madre: se carga en cajas, se revisa y se registra en botellas', async () => {
    const fetchMock = backendFalso(sesionDe(['COMPRAS'], 'Gabriela Paz'), backend());
    const persona = userEvent.setup();
    montar('/ingreso');

    expect(await screen.findByDisplayValue('Depósito Avril')).toBeInTheDocument();
    await persona.clear(screen.getByLabelText('Fecha de ingreso'));
    await persona.type(screen.getByLabelText('Fecha de ingreso'), '2026-10-06');
    await persona.type(screen.getByLabelText(/Número de remito/), 'R-0002');
    await agregarPorNombre(persona, 'fer', 'Fernet Branca');
    // Las dadas de baja no se ofrecen.
    await persona.type(screen.getByLabelText('Agregar bebida'), 'gin');
    expect(screen.queryByRole('option', { name: /Gin discontinuado/ })).not.toBeInTheDocument();
    await persona.clear(screen.getByLabelText('Agregar bebida'));

    const tarjeta = screen.getByRole('group', { name: 'Cantidad de Fernet Branca' });
    await persona.click(within(tarjeta).getAllByRole('button', { name: 'Sumar 1' })[0]);
    await persona.click(within(tarjeta).getAllByRole('button', { name: 'Sumar 1' })[0]);
    await persona.click(within(tarjeta).getAllByRole('button', { name: 'Sumar 1' })[1]);
    expect(screen.getByText('Ingresan 1 bebida al Depósito Avril.')).toBeInTheDocument();

    await persona.click(screen.getByRole('button', { name: 'Revisar ingreso' }));
    const revision = screen.getByRole('dialog', { name: 'Revisá el ingreso' });
    expect(within(revision).getByText('2 cajas y 1 botella')).toBeInTheDocument();
    expect(within(revision).getByText(/remito R-0002/)).toBeInTheDocument();
    await persona.click(within(revision).getByRole('button', { name: 'Registrar ingreso' }));

    expect(await screen.findByText('Ingreso registrado: 1 bebida al Depósito Avril.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toEqual({
      fecha: '2026-10-06', numeroRemito: 'R-0002', renglones: [{ bebidaId: 1, cantidad: 13 }],
    });
    expect(screen.queryByRole('group', { name: 'Cantidad de Fernet Branca' })).not.toBeInTheDocument();
    const ultimos = screen.getByText('Últimos ingresos').closest('.v-card') as HTMLElement;
    expect(within(ultimos).getByText('Gabriela Paz')).toBeInTheDocument();
    expect(within(ultimos).getByText(/remito R-0002/)).toBeInTheDocument();
  });

  it('no deja revisar con una bebida sin cantidad', async () => {
    backendFalso(sesionDe(['ADMINISTRACION']), backend());
    const persona = userEvent.setup();
    montar('/ingreso');

    await screen.findByDisplayValue('Depósito Avril');
    await agregarPorNombre(persona, 'quil', 'Cerveza Quilmes');
    await persona.click(screen.getByRole('button', { name: 'Revisar ingreso' }));

    expect(screen.getByText('Cargá la cantidad de Cerveza Quilmes o sacala del ingreso.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: 'Revisá el ingreso' })).not.toBeInTheDocument();
  });

  it('el lector agrega la bebida del código y avisa los códigos que no sirven', async () => {
    vi.stubGlobal('isSecureContext', true);
    Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia: vi.fn() }, configurable: true });
    backendFalso(sesionDe(['COMPRAS']), backend());
    const persona = userEvent.setup();
    montar('/ingreso');

    await persona.click(await screen.findByRole('button', { name: 'Leer código' }));
    const lector = await screen.findByRole('dialog', { name: 'Leer código de barras' });
    await act(async () => undefined);
    act(() => camara.leer('17790000000016'));
    expect(within(lector).getByText('Fernet Branca · Caja de 6 agregada.')).toBeInTheDocument();
    act(() => camara.leer('7790000000064'));
    expect(within(lector).getByText(/Gin discontinuado 750 ml está dada de baja/)).toBeInTheDocument();
    act(() => camara.leer('1234567890123'));
    expect(within(lector).getByText('El código 1234567890123 no está en el catálogo. Elegí la bebida a mano.')).toBeInTheDocument();
    await persona.click(within(lector).getByRole('button', { name: 'Elegir a mano' }));

    expect(screen.getByRole('group', { name: 'Cantidad de Fernet Branca' })).toBeInTheDocument();
  });

  it('muestra el error del backend sin perder lo cargado', async () => {
    backendFalso(sesionDe(['COMPRAS']), backend());
    const persona = userEvent.setup();
    montar('/ingreso');

    await screen.findByDisplayValue('Depósito Avril');
    await persona.type(screen.getByLabelText(/Número de remito/), 'falla');
    await agregarPorNombre(persona, 'fer', 'Fernet Branca');
    await persona.click(within(screen.getByRole('group', { name: 'Cantidad de Fernet Branca' })).getAllByRole('button', { name: 'Sumar 1' })[0]);
    await persona.click(screen.getByRole('button', { name: 'Revisar ingreso' }));
    await persona.click(within(screen.getByRole('dialog', { name: 'Revisá el ingreso' })).getByRole('button', { name: 'Registrar ingreso' }));

    expect(await screen.findByText(/está dada de baja. Sacala del ingreso/)).toBeInTheDocument();
    await persona.click(screen.getByRole('button', { name: 'Volver a editar' }));
    expect(screen.getByRole('group', { name: 'Cantidad de Fernet Branca' })).toBeInTheDocument();
  });

  it('lista los últimos ingresos con quién y cuándo', async () => {
    backendFalso(sesionDe(['COMPRAS']), backend());
    montar('/ingreso');

    expect(await screen.findByText('Últimos ingresos')).toBeInTheDocument();
    expect(screen.getByText('4 cajas')).toBeInTheDocument();
    expect(screen.getByText('Nicolás Herrera')).toBeInTheDocument();
  });

  it('sin depósito madre lo dice y no deja cargar', async () => {
    backendFalso(sesionDe(['COMPRAS']), backend(false));
    montar('/ingreso');

    expect(await screen.findByText('No hay un depósito madre activo')).toBeInTheDocument();
    expect(screen.queryByLabelText('Agregar bebida')).not.toBeInTheDocument();
  });
});
