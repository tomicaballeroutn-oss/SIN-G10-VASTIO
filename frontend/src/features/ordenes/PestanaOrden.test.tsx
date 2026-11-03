import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import type { Orden, OrdenDelEvento, OrdenDeLaLista } from '../../api/ordenes';
import { backendFalso, json, montar, pedidos, sesionDe, type Pedido } from '../../test/backendFalso';
import { fichaDePrueba } from '../../test/fichas';

const EVENTO = {
  id: 5, codigo: 'EV-2026-00005', nombre: 'Bruno y Martina', estado: 'CONFIRMADO', fecha: '2026-11-14',
  salon: { id: 1, codigo: 'avril', nombre: 'Avril' }, asistentes: 180,
};

const ORDEN: Orden = {
  id: 9, estado: 'SUGERIDA', fuente: 'HISTORICO_TIPO', eventosBase: 12, asistentesBase: 180, margenPorcentaje: 10,
  origen: 'Depósito principal', responsable: null, fechaCreacion: '2026-10-20T10:00:00-03:00',
  fechaCalculo: '2026-10-20T10:00:00-03:00', modificacion: null, confirmacion: null, reapertura: null, version: 0,
  renglones: [
    { bebidaId: 1, nombre: 'Fernet Branca', presentacion: '750 ml', unidad: 'Caja', unidadesPorBulto: 6, activa: true,
      sugerida: 48, aRetirar: 48, comprometido: 36 },
    { bebidaId: 2, nombre: 'Vodka Smirnoff', presentacion: '700 ml', unidad: 'Botella', unidadesPorBulto: 1, activa: true,
      sugerida: 5, aRetirar: 5, comprometido: 0 },
  ],
};

const NINGUNA = { generar: false, recalcular: false, ajustar: false, confirmar: false, reabrir: false };
const EDITABLE = { ...NINGUNA, recalcular: true, ajustar: true, confirmar: true };
const CON_ORDEN: OrdenDelEvento = { evento: EVENTO, orden: ORDEN, asistentesCambiaron: false, acciones: EDITABLE };
const SIN_ORDEN: OrdenDelEvento = { evento: EVENTO, orden: null, asistentesCambiaron: false, acciones: { ...NINGUNA, generar: true } };

function backend(consulta: OrdenDelEvento, roles: Rol[] = ['COMPRAS'], otro: (p: Pedido) => Response | undefined = () => undefined) {
  return backendFalso(sesionDe(roles), (p) => {
    if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, fichaDePrueba({ estado: 'CONFIRMADO', tipo: { id: 2, nombre: 'Quince' } }));
    if (p.metodo === 'GET' && p.ruta === '/eventos/5/orden-preparacion') return json(200, consulta);
    return otro(p);
  });
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-35 · orden de preparación', () => {
  it('muestra lo sugerido en cajas y botellas, de dónde salió y lo comprometido', async () => {
    backend(CON_ORDEN);
    montar('/eventos/5?pestana=orden');

    const tabla = await screen.findByRole('table', { name: 'Bebida de la orden de preparación' });
    const fernet = within(tabla).getByRole('row', { name: /Fernet Branca/ });
    expect(within(fernet).getAllByText('8 cajas')).toHaveLength(2);
    expect(within(fernet).getByText('6 cajas')).toBeInTheDocument();
    expect(within(within(tabla).getByRole('row', { name: /Vodka/ })).getAllByText('5 botellas')).toHaveLength(2);
    expect(screen.getByText('Sugerida según el consumo de 12 eventos cerrados de Quince.')).toBeInTheDocument();
    expect(screen.getByText(/Calculada sobre 180 asistentes, con 10 % de margen/)).toBeInTheDocument();
    expect(screen.getByText('Sistema')).toBeInTheDocument();
    expect(screen.getByText('Sugerida')).toBeInTheDocument();
  });

  it('sin orden, Compras la genera', async () => {
    const fetchMock = backend(SIN_ORDEN, ['COMPRAS'], (p) =>
      p.metodo === 'POST' && p.ruta === '/eventos/5/orden-preparacion' ? json(201, CON_ORDEN) : undefined);
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=orden');

    await usuario.click(await screen.findByRole('button', { name: 'Generar orden' }));

    expect(await screen.findByText('Orden generada. Se avisó a Compras.')).toBeInTheDocument();
    expect(pedidos(fetchMock).filter((p) => p.metodo === 'POST')).toHaveLength(1);
  });

  it('avisa si cambiaron los asistentes y recalcula con confirmación', async () => {
    const fetchMock = backend({ ...CON_ORDEN, asistentesCambiaron: true, evento: { ...EVENTO, asistentes: 250 } }, ['COMPRAS'], (p) =>
      p.metodo === 'POST' && p.ruta === '/eventos/5/orden-preparacion/recalculo' ? json(200, CON_ORDEN) : undefined);
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=orden');

    expect(await screen.findByText('Cambiaron los asistentes del evento')).toBeInTheDocument();
    expect(screen.getByText(/se calculó sobre 180 asistentes y ahora son 250/)).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Recalcular sugerencia' }));
    const dialogo = screen.getByRole('dialog', { name: 'Recalcular sugerencia' });
    expect(within(dialogo).getByText(/se pierden los ajustes/)).toBeInTheDocument();
    await usuario.click(within(dialogo).getByRole('button', { name: 'Recalcular sugerencia' }));

    expect(await screen.findByText('Sugerencia recalculada.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toEqual({ version: 0 });
  });

  it('Administración la ve sin acciones', async () => {
    backend({ ...CON_ORDEN, acciones: NINGUNA }, ['ADMINISTRACION']);
    montar('/eventos/5?pestana=orden');

    expect(await screen.findByRole('table', { name: 'Bebida de la orden de preparación' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Recalcular sugerencia' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ajustar cantidades' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Vista para imprimir' })).toBeInTheDocument();
  });

  it('Compras ajusta en cajas y botellas, agrega una bebida y manda solo lo que cambió', async () => {
    const gin = {
      id: 7, nombre: 'Gin Bombay', presentacion: '750 ml', tipo: { id: 3, nombre: 'Destilado' },
      unidad: { id: 1, nombre: 'Caja', esBotella: false }, unidadesPorBulto: 6, stockMinimo: null, codigos: [], activo: true, fechaBaja: null,
    };
    const AJUSTADA: OrdenDelEvento = { ...CON_ORDEN, orden: { ...ORDEN, estado: 'MODIFICADA', version: 1 } };
    const fetchMock = backend(CON_ORDEN, ['COMPRAS'], (p) => {
      if (p.metodo === 'GET' && p.ruta === '/bebidas') return json(200, [gin]);
      if (p.metodo === 'PUT' && p.ruta === '/eventos/5/orden-preparacion/renglones') return json(200, AJUSTADA);
      return undefined;
    });
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=orden');

    await usuario.click(await screen.findByRole('button', { name: 'Ajustar cantidades' }));
    const fernet = screen.getByRole('group', { name: 'A retirar de Fernet Branca' });
    await usuario.click(within(fernet).getAllByRole('button', { name: /Sumar/ })[0]);
    await usuario.type(screen.getByLabelText('Agregar bebida'), 'gin');
    await usuario.click(await screen.findByRole('option', { name: /Gin Bombay 750 ml/ }));
    const nuevo = screen.getByRole('group', { name: 'A retirar de Gin Bombay' });
    await usuario.click(within(nuevo).getAllByRole('button', { name: /Sumar/ })[0]);
    await usuario.click(screen.getByRole('button', { name: 'Guardar ajustes' }));

    expect(await screen.findByText('Ajustes guardados. La orden quedó Modificada.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toEqual({
      version: 0, renglones: [{ bebidaId: 1, cantidad: 54 }, { bebidaId: 7, cantidad: 6 }],
    });
  });

  it('confirma la orden y después la reabre', async () => {
    const CONFIRMADA: OrdenDelEvento = {
      ...CON_ORDEN,
      orden: { ...ORDEN, estado: 'CONFIRMADA', version: 1, confirmacion: { usuario: { id: 4, nombre: 'Tomás Caballero' }, fechaHora: '2026-10-21T09:00:00-03:00' } },
      acciones: { ...NINGUNA, reabrir: true },
    };
    let actual = CON_ORDEN;
    const fetchMock = backendFalso(sesionDe(['COMPRAS']), (p) => {
      if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, fichaDePrueba({ estado: 'CONFIRMADO' }));
      if (p.metodo === 'GET' && p.ruta === '/eventos/5/orden-preparacion') return json(200, actual);
      if (p.ruta === '/eventos/5/orden-preparacion/confirmacion') {
        actual = CONFIRMADA;
        return json(200, CONFIRMADA);
      }
      if (p.ruta === '/eventos/5/orden-preparacion/reapertura') return json(200, { ...CON_ORDEN, orden: { ...ORDEN, estado: 'MODIFICADA', version: 2 } });
      return undefined;
    });
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=orden');

    await usuario.click(await screen.findByRole('button', { name: 'Confirmar orden' }));
    await usuario.click(within(screen.getByRole('dialog', { name: 'Confirmar orden' })).getByRole('button', { name: 'Confirmar orden' }));
    expect(await screen.findByText(/Orden confirmada/)).toBeInTheDocument();
    expect(screen.getByText('la confirmó', { exact: false })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ajustar cantidades' })).not.toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: 'Reabrir orden' }));
    await usuario.click(within(screen.getByRole('dialog', { name: 'Reabrir orden' })).getByRole('button', { name: 'Reabrir orden' }));
    expect(await screen.findByText('Orden reabierta: ya podés ajustarla.')).toBeInTheDocument();
    expect(pedidos(fetchMock).filter((p) => p.metodo === 'POST').map((p) => p.cuerpo)).toEqual([{ version: 0 }, { version: 1 }]);
  });

  it('sin datos explica que se carga a mano', async () => {
    backend({ ...CON_ORDEN, orden: { ...ORDEN, fuente: 'SIN_DATOS', eventosBase: 0, renglones: [] } });
    montar('/eventos/5?pestana=orden');

    expect(await screen.findByText(/la orden sale vacía y las bebidas se cargan a mano/)).toBeInTheDocument();
    expect(screen.getByText('La orden no tiene bebidas.')).toBeInTheDocument();
  });

  it('la vendedora no tiene la pestaña', async () => {
    backend(CON_ORDEN, ['VENDEDORA']);
    montar('/eventos/5?pestana=orden');

    expect(await screen.findByRole('tab', { name: /Datos/ })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /Orden de preparación/ })).not.toBeInTheDocument();
  });
});

describe('órdenes de preparación', () => {
  const LISTA: OrdenDeLaLista[] = [
    { evento: EVENTO, estado: 'SUGERIDA', bebidas: 2, asistentesCambiaron: true },
    { evento: { ...EVENTO, id: 6, nombre: 'Quince de Sofía', fecha: '2026-11-20' }, estado: null, bebidas: null, asistentesCambiaron: false },
  ];

  it('lista los eventos confirmados con el estado de su orden y abre la ficha', async () => {
    backendFalso(sesionDe(['COMPRAS']), (p) => {
      if (p.ruta === '/ordenes-preparacion') return json(200, LISTA);
      if (p.ruta === '/eventos/5') return json(200, fichaDePrueba({ estado: 'CONFIRMADO' }));
      if (p.ruta === '/eventos/5/orden-preparacion') return json(200, CON_ORDEN);
      return undefined;
    });
    const usuario = userEvent.setup();
    montar('/ordenes');

    const tabla = await screen.findByRole('table', { name: 'Eventos confirmados y su orden de preparación' });
    expect(within(tabla).getByText('Sin orden')).toBeInTheDocument();
    expect(within(tabla).getByText('Cambiaron los asistentes')).toBeInTheDocument();
    await usuario.click(within(tabla).getByText('Bruno y Martina'));

    expect(await screen.findByRole('table', { name: 'Bebida de la orden de preparación' })).toBeInTheDocument();
  });
});
