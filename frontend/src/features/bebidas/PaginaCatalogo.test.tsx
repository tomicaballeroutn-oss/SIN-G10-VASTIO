import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Bebida } from '../../api/bebidas';
import { _reiniciarCliente } from '../../api/cliente';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';

const TIPOS = [{ id: 1, nombre: 'Vino' }, { id: 3, nombre: 'Destilado' }];
const UNIDADES = [{ id: 1, nombre: 'Caja', esBotella: false }, { id: 2, nombre: 'Pack', esBotella: false }, { id: 3, nombre: 'Botella', esBotella: true }];

const bebida = (id: number, nombre: string, extra: Partial<Bebida> = {}): Bebida => ({
  id, nombre, presentacion: '750 ml', tipo: TIPOS[1], unidad: UNIDADES[0], unidadesPorBulto: 6, stockMinimo: null,
  codigos: [], activo: true, fechaBaja: null, ...extra,
});

const BEBIDAS: Bebida[] = [
  bebida(1, 'Fernet Branca', { stockMinimo: 24, codigos: [{ codigo: '17790000000016', unidades: 6 }, { codigo: '7790000000019', unidades: 1 }] }),
  bebida(2, 'Malbec Luigi Bosca', { tipo: TIPOS[0] }),
  bebida(3, 'Gin discontinuado', { activo: false, fechaBaja: '2026-09-01T10:00:00-03:00' }),
];

function backend({ metodo, ruta, cuerpo }: Pedido) {
  if (metodo === 'GET' && ruta === '/bebidas') return json(200, BEBIDAS);
  if (metodo === 'GET' && ruta === '/tipos-bebida') return json(200, TIPOS);
  if (metodo === 'GET' && ruta === '/unidades-manipulacion') return json(200, UNIDADES);
  if (metodo === 'POST' && ruta === '/bebidas') {
    const datos = cuerpo as { nombre: string; codigos: { codigo: string }[] };
    if (datos.codigos.some((c) => c.codigo === '7790000000019')) {
      return problema(409, 'CODIGO_DE_OTRA_BEBIDA', 'El código 7790000000019 ya es de Fernet Branca 750 ml. Quitáselo a esa bebida antes de usarlo acá.');
    }
    return json(201, bebida(9, datos.nombre, { codigos: datos.codigos as Bebida['codigos'] }));
  }
  if (metodo === 'GET' && ruta === '/bebidas/1/saldos') return json(200, [{ ubicacionId: 1, ubicacion: 'Depósito Avril', cantidad: 51 }]);
  if (metodo === 'GET' && ruta === '/bebidas/2/saldos') return json(200, []);
  if (metodo === 'POST' && ruta === '/bebidas/1/baja') return json(200, { ...BEBIDAS[0], activo: false });
  if (metodo === 'POST' && ruta === '/bebidas/3/reactivacion') return json(200, { ...BEBIDAS[2], activo: true, fechaBaja: null });
  return undefined;
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-25 · catálogo de bebidas', () => {
  it('lista las activas sin precios y busca por nombre o código de barras', async () => {
    backendFalso(sesionDe(['COMPRAS']), backend);
    const persona = userEvent.setup();
    montar('/catalogo');

    const fernet = (await screen.findByText('Fernet Branca')).closest('tr') as HTMLElement;
    expect(within(fernet).getByText('Caja de 6')).toBeInTheDocument();
    expect(within(fernet).getByText('4 cajas')).toBeInTheDocument();
    expect(screen.queryByText('Gin discontinuado')).not.toBeInTheDocument();
    expect(screen.queryByText(/precio/i)).not.toBeInTheDocument();

    await persona.type(screen.getByLabelText('Buscar'), '00000019');
    expect(screen.getByText('Fernet Branca')).toBeInTheDocument();
    expect(screen.queryByText('Malbec Luigi Bosca')).not.toBeInTheDocument();
  });

  it('muestra las dadas de baja si se pide y las reactiva', async () => {
    backendFalso(sesionDe(['ADMINISTRACION']), backend);
    const persona = userEvent.setup();
    montar('/catalogo');

    await persona.click(await screen.findByLabelText('Mostrar las dadas de baja'));
    const gin = screen.getByText('Gin discontinuado').closest('tr') as HTMLElement;
    expect(within(gin).getByText('De baja')).toBeInTheDocument();
    await persona.click(within(gin).getByRole('button', { name: 'Reactivar' }));
    expect(await screen.findByText('Gin discontinuado 750 ml volvió al catálogo.')).toBeInTheDocument();
  });

  it('da de alta una bebida con sus códigos y el stock mínimo en botellas', async () => {
    const fetchMock = backendFalso(sesionDe(['COMPRAS']), backend);
    const persona = userEvent.setup();
    montar('/catalogo');

    await persona.click(await screen.findByRole('button', { name: 'Nueva bebida' }));
    const dialogo = screen.getByRole('dialog', { name: 'Nueva bebida' });
    await persona.type(within(dialogo).getByLabelText('Nombre'), 'Ron Havana Club');
    await persona.type(within(dialogo).getByLabelText('Presentación'), '700 ml');
    await persona.selectOptions(within(dialogo).getByLabelText('Tipo'), '3');
    await persona.selectOptions(within(dialogo).getByLabelText('Se mueve en'), '1');
    await persona.type(within(dialogo).getByLabelText('Botellas por bulto'), '6');
    await persona.click(within(dialogo).getAllByRole('button', { name: 'Sumar 1' })[0]);
    await persona.click(within(dialogo).getAllByRole('button', { name: 'Sumar 1' })[1]);
    await persona.type(within(dialogo).getByLabelText('Agregar código'), '17791234567890');
    await persona.click(within(dialogo).getByRole('button', { name: 'Agregar código' }));
    await persona.type(within(dialogo).getByLabelText('Agregar código'), '123');
    await persona.click(within(dialogo).getByRole('button', { name: 'Agregar código' }));
    expect(within(dialogo).getByText('El código tiene que tener de 8 a 14 números.')).toBeInTheDocument();
    await persona.click(within(dialogo).getByRole('button', { name: 'Cargar bebida' }));

    expect(await screen.findByText('Bebida cargada: Ron Havana Club 750 ml.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toEqual({
      nombre: 'Ron Havana Club',
      presentacion: '700 ml',
      tipoId: 3,
      unidadId: 1,
      unidadesPorBulto: 6,
      stockMinimo: 7,
      codigos: [{ codigo: '17791234567890', unidades: 6 }],
    });
  });

  it('con unidad Botella el bulto queda en 1 y muestra el error del backend', async () => {
    const fetchMock = backendFalso(sesionDe(['DIRECCION']), backend);
    const persona = userEvent.setup();
    montar('/catalogo');

    await persona.click(await screen.findByRole('button', { name: 'Nueva bebida' }));
    const dialogo = screen.getByRole('dialog', { name: 'Nueva bebida' });
    await persona.type(within(dialogo).getByLabelText('Nombre'), 'Vodka');
    await persona.type(within(dialogo).getByLabelText('Presentación'), '700 ml');
    await persona.selectOptions(within(dialogo).getByLabelText('Tipo'), '3');
    await persona.selectOptions(within(dialogo).getByLabelText('Se mueve en'), '3');
    expect(within(dialogo).getByLabelText('Botellas por bulto')).toBeDisabled();
    await persona.type(within(dialogo).getByLabelText('Agregar código'), '7790000000019');
    await persona.click(within(dialogo).getByRole('button', { name: 'Agregar código' }));
    await persona.click(within(dialogo).getByRole('button', { name: 'Cargar bebida' }));

    expect(await within(dialogo).findByText(/ya es de Fernet Branca 750 ml/)).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toMatchObject({ unidadesPorBulto: 1, stockMinimo: null });
  });

  it('antes de dar de baja muestra dónde tiene saldo', async () => {
    backendFalso(sesionDe(['ADMINISTRACION']), backend);
    const persona = userEvent.setup();
    montar('/catalogo');

    const fernet = (await screen.findByText('Fernet Branca')).closest('tr') as HTMLElement;
    await persona.click(within(fernet).getByRole('button', { name: 'Dar de baja' }));
    const dialogo = screen.getByRole('dialog', { name: 'Dar de baja bebida' });
    expect(await within(dialogo).findByText('Tiene saldo en 1 ubicación')).toBeInTheDocument();
    expect(within(dialogo).getByText('Depósito Avril: 8 cajas y 3 botellas')).toBeInTheDocument();
    await persona.click(within(dialogo).getByRole('button', { name: 'Dar de baja bebida' }));

    expect(await screen.findByText('Fernet Branca 750 ml quedó dada de baja: ya no se ofrece para cargar.')).toBeInTheDocument();
    expect(screen.queryByRole('cell', { name: 'Fernet Branca' })).not.toBeInTheDocument();
  });

  it('Vendedora no tiene la pantalla', async () => {
    backendFalso(sesionDe(['VENDEDORA']), backend);
    montar('/catalogo');

    expect(await screen.findByText('Tu perfil no tiene acceso a esta pantalla')).toBeInTheDocument();
  });
});
