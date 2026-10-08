import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Motivo } from '../../api/catalogos';
import { _reiniciarCliente } from '../../api/cliente';
import type { Existencias, UbicacionConsultable } from '../../api/stock';
import { backendFalso, json, montar, pedidos, sesionDe, type Pedido } from '../../test/backendFalso';

const FERNET = { id: 1, nombre: 'Fernet Branca', presentacion: '750 ml', tipo: 'Destilado', unidad: 'Caja', unidadesPorBulto: 6, activo: true };
const UBICACIONES: UbicacionConsultable[] = [{ ubicacion: { id: 4, nombre: 'Barra Santa Bárbara', tipo: 'BARRA' } }];
const EXISTENCIAS: Existencias = { ubicacion: { id: 4, nombre: 'Barra Santa Bárbara' }, renglones: [{ bebida: FERNET, cantidad: 10, estado: 'OK' }] };
const MOTIVOS: Motivo[] = [
  { id: 1, ambito: 'CANCELACION', nombre: 'Desistimiento del cliente', activo: true },
  { id: 11, ambito: 'AJUSTE', nombre: 'Rotura', activo: true },
  { id: 12, ambito: 'AJUSTE', nombre: 'Recuento físico', activo: true },
  { id: 13, ambito: 'AJUSTE', nombre: 'Otro', activo: true },
];

function backend({ metodo, ruta, cuerpo }: Pedido) {
  if (ruta === '/stock/ubicaciones') return json(200, UBICACIONES);
  if (ruta === '/stock?ubicacionId=4') return json(200, EXISTENCIAS);
  if (ruta === '/stock') return json(200, { ubicacion: null, renglones: EXISTENCIAS.renglones });
  if (ruta === '/motivos') return json(200, MOTIVOS);
  if (metodo === 'POST' && ruta === '/ajustes/recuento') {
    const { cantidadContada } = cuerpo as { cantidadContada: number };
    return json(200, { registrado: cantidadContada !== 10, movimientoId: 1, tipo: 'AJUSTE', saldoAnterior: 10, saldo: cantidadContada, diferencia: cantidadContada - 10, avisoSaldoNegativo: false });
  }
  if (metodo === 'POST' && ruta === '/ajustes/rotura') {
    const { cantidad } = cuerpo as { cantidad: number };
    return json(200, { registrado: true, movimientoId: 2, tipo: 'MERMA', saldoAnterior: 10, saldo: 10 - cantidad, diferencia: -cantidad, avisoSaldoNegativo: 10 - cantidad < 0 });
  }
  return undefined;
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

async function abrir(persona: ReturnType<typeof userEvent.setup>, accion: string) {
  const tarjeta = (await screen.findByText('Fernet Branca')).closest('li') as HTMLElement;
  await persona.click(within(tarjeta).getByRole('button', { name: accion }));
  return screen.findByRole('dialog', { name: accion });
}

describe('UI-38 · ajuste de stock', () => {
  it('el recuento muestra la diferencia antes de registrarla', async () => {
    const fetchMock = backendFalso(sesionDe(['COMPRAS']), backend);
    const persona = userEvent.setup();
    montar('/existencias');

    const dialogo = await abrir(persona, 'Registrar recuento');
    expect(within(dialogo).getByText('1 caja y 4 botellas')).toBeInTheDocument();
    expect(within(dialogo).getByText('Coincide con el saldo: no hay nada para registrar.')).toBeInTheDocument();
    expect(within(dialogo).getByLabelText('Motivo')).toHaveValue('12');
    expect(within(within(dialogo).getByLabelText('Motivo')).queryByRole('option', { name: 'Desistimiento del cliente' })).not.toBeInTheDocument();
    await persona.click(within(dialogo).getAllByRole('button', { name: 'Sumar 1' })[0]);
    expect(within(dialogo).getByText('Se registra un ajuste de +1 caja.')).toBeInTheDocument();
    await persona.click(within(dialogo).getByRole('button', { name: 'Registrar recuento' }));

    expect(await screen.findByText('Recuento de Fernet Branca 750 ml registrado: ajuste de +1 caja en Barra Santa Bárbara.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.ruta === '/ajustes/recuento')?.cuerpo).toEqual({
      ubicacionId: 4, bebidaId: 1, cantidadContada: 16, motivoId: 12, detalle: '',
    });
  });

  it('un recuento igual al saldo no registra nada y lo dice', async () => {
    backendFalso(sesionDe(['ADMINISTRACION']), backend);
    const persona = userEvent.setup();
    montar('/existencias');

    const dialogo = await abrir(persona, 'Registrar recuento');
    await persona.click(within(dialogo).getByRole('button', { name: 'Registrar recuento' }));
    expect(await screen.findByText('El recuento de Fernet Branca 750 ml coincide con el saldo: no hay nada para registrar.')).toBeInTheDocument();
  });

  it('una rotura que deja saldo negativo avisa que se le contó a Compras y Administración', async () => {
    const fetchMock = backendFalso(sesionDe(['COMPRAS']), backend);
    const persona = userEvent.setup();
    montar('/existencias');

    const dialogo = await abrir(persona, 'Declarar rotura');
    expect(within(dialogo).getByLabelText('Motivo')).toHaveValue('11');
    await persona.click(within(dialogo).getByRole('button', { name: 'Declarar rotura' }));
    expect(within(dialogo).getByText('Cargá cuántas botellas se rompieron.')).toBeInTheDocument();
    await persona.click(within(dialogo).getAllByRole('button', { name: 'Sumar 1' })[0]);
    await persona.click(within(dialogo).getAllByRole('button', { name: 'Sumar 1' })[0]);
    await persona.selectOptions(within(dialogo).getByLabelText('Motivo'), '13');
    await persona.type(within(dialogo).getByLabelText('Detalle'), 'Se cayó un cajón');
    await persona.click(within(dialogo).getByRole('button', { name: 'Declarar rotura' }));

    expect(await screen.findByText(/El saldo quedó negativo: avisamos a Compras y Administración/)).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.ruta === '/ajustes/rotura')?.cuerpo).toEqual({
      ubicacionId: 4, bebidaId: 1, cantidad: 12, motivoId: 13, detalle: 'Se cayó un cajón',
    });
  });

  it('en el total del complejo no hay ajustes, porque hace falta una ubicación', async () => {
    backendFalso(sesionDe(['COMPRAS']), backend);
    const persona = userEvent.setup();
    montar('/existencias');

    await screen.findByText('Fernet Branca');
    await persona.selectOptions(screen.getByLabelText('Ubicación'), 'todas');
    await screen.findByText('Fernet Branca');
    expect(screen.queryByRole('button', { name: 'Registrar recuento' })).not.toBeInTheDocument();
  });

  it('el aviso de saldo negativo lleva a Existencias', async () => {
    const aviso = { id: 40, tipo: 'ALERTA_STOCK', mensaje: 'Saldo negativo de Gin Bombay 750 ml en Barra Avril: falta registrar un movimiento.', eventoId: null, fechaHora: '2026-10-08T02:15:00-03:00', leida: false };
    backendFalso(sesionDe(['COMPRAS']), (p) => {
      if (p.ruta === '/notificaciones/sin-leer') return json(200, { cantidad: 1 });
      if (p.ruta.startsWith('/notificaciones?')) return json(200, { notificaciones: [aviso], pagina: 0, hayMas: false, sinLeer: 1 });
      if (p.metodo === 'POST' && p.ruta === '/notificaciones/40/lectura') return new Response(null, { status: 204 });
      return backend(p);
    });
    const persona = userEvent.setup();
    const router = montar('/notificaciones');

    await persona.click(await screen.findByRole('button', { name: /Saldo negativo de Gin Bombay/ }));
    expect(router.state.location.pathname).toBe('/existencias');
  });
});
