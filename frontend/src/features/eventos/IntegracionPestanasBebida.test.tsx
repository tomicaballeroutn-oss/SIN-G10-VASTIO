import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import { backendFalso, json, montar, pedidos, sesionDe } from '../../test/backendFalso';
import { fichaDePrueba } from '../../test/fichas';

// Hoy los perfiles coinciden; variar una política demuestra que la ficha no acopla ambos permisos.
const permisos = vi.hoisted(() => ({ consumo: [] as Rol[], preparacion: [] as Rol[] }));
vi.mock('../../api/consumos', async (original) => ({
  ...await original<typeof import('../../api/consumos')>(), ROLES_CONSUMO: permisos.consumo,
}));
vi.mock('../../api/preparaciones', async (original) => ({
  ...await original<typeof import('../../api/preparaciones')>(), ROLES_PREPARACION: permisos.preparacion,
}));

beforeEach(() => {
  _reiniciarCliente();
  const roles: Rol[] = ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'];
  permisos.consumo.splice(0, permisos.consumo.length, ...roles);
  permisos.preparacion.splice(0, permisos.preparacion.length, ...roles);
});
afterEach(() => vi.unstubAllGlobals());

function backend(rol: Rol) {
  return backendFalso(sesionDe([rol]), ({ ruta }) => {
    if (ruta === '/eventos/5') return json(200, fichaDePrueba({ estado: 'CERRADO' }));
    if (ruta === '/eventos/5/consumo') return json(200, {
      invitados: 180, asistenciaReal: null, denominador: 'INVITADOS', usaSegmentos: false, renglones: [], consumoTotal: 0, porInvitadoTotal: 0,
    });
    if (ruta === '/eventos/5/preparaciones') return json(200, {
      evento: { id: 5, nombre: 'Bruno y Martina', estado: 'CERRADO' }, madre: null, circuitos: [], historial: [],
    });
    if (ruta === '/bebidas') return json(200, []);
    return undefined;
  });
}

it.each<Rol>(['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'])(
  'conserva ambas pestañas una sola vez y navega sin escrituras con %s', async (rol) => {
    const f = backend(rol); const router = montar('/eventos/5'); const u = userEvent.setup();
    await screen.findByRole('tab', { name: 'Consumo de bebida' });
    expect(screen.getAllByRole('tab', { name: 'Consumo de bebida' })).toHaveLength(1);
    expect(screen.getAllByRole('tab', { name: 'Preparación' })).toHaveLength(1);
    expect(screen.getByRole('tab', { name: 'Datos' })).toBeVisible();
    expect(screen.getByRole('tab', { name: /Servicios/ })).toBeVisible();
    expect(screen.getByRole('tab', { name: /Historial/ })).toBeVisible();
    await u.click(screen.getByRole('tab', { name: 'Consumo de bebida' }));
    await screen.findByText('Consumo consolidado al cierre');
    expect(router.state.location.search).toBe('?pestana=consumo');
    await u.click(screen.getByRole('tab', { name: 'Preparación' }));
    await screen.findByRole('heading', { name: 'Preparaciones del evento' });
    expect(router.state.location.search).toBe('?pestana=preparacion');
    await u.click(screen.getByRole('tab', { name: /Historial/ }));
    expect(router.state.location.search).toBe('?pestana=historial');
    expect(pedidos(f).every((p) => p.metodo === 'GET')).toBe(true);
  });

it.each(['consumo', 'preparacion'])('abre directamente ?pestana=%s', async (pestana) => {
  backend('COMPRAS'); montar(`/eventos/5?pestana=${pestana}`);
  if (pestana === 'consumo') await screen.findByText('Consumo consolidado al cierre');
  else await screen.findByRole('heading', { name: 'Preparaciones del evento' });
  expect(screen.getByRole('tab', { name: pestana === 'consumo' ? 'Consumo de bebida' : 'Preparación' }))
    .toHaveAttribute('aria-selected', 'true');
});

it.each(['consumo', 'preparacion'])('respeta el permiso de %s aunque el otro esté habilitado', async (denegada) => {
  permisos[denegada as keyof typeof permisos].splice(0);
  const f = backend('COMPRAS'); montar(`/eventos/5?pestana=${denegada}`);
  await screen.findByRole('heading', { name: 'Bruno y Martina' });
  expect(screen.queryByRole('tab', { name: denegada === 'consumo' ? 'Consumo de bebida' : 'Preparación' })).not.toBeInTheDocument();
  expect(screen.getByRole('tab', { name: denegada === 'consumo' ? 'Preparación' : 'Consumo de bebida' })).toBeVisible();
  expect(screen.getByRole('tab', { name: 'Datos' })).toHaveAttribute('aria-selected', 'true');
  expect(pedidos(f).some((p) => p.ruta.endsWith(denegada === 'consumo' ? '/consumo' : '/preparaciones'))).toBe(false);
});
