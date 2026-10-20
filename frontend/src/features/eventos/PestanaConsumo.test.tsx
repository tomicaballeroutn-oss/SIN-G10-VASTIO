import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import type { ConsumoEvento } from '../../api/consumos';
import { backendFalso, json, montar, pedidos, problema, sesionDe } from '../../test/backendFalso';
import { fichaDePrueba } from '../../test/fichas';

const CONSUMO: ConsumoEvento = {
  invitados: 150, denominador: 'INVITADOS', usaSegmentos: false,
  renglones: [{ bebidaId: 1, nombre: 'Fernet', presentacion: '750 ml', unidad: 'Caja', unidadesPorBulto: 6,
    entregado: 12, devuelto: 6, remanente: 2, consumo: 4, porInvitado: 0.0267 }],
  consumoTotal: 4, porInvitadoTotal: 0.0267,
};

function backend(roles: Rol[], respuesta: () => Response = () => json(200, CONSUMO)) {
  return backendFalso(sesionDe(roles), ({ ruta }) => {
    if (ruta === '/eventos/5') return json(200, fichaDePrueba({ estado: 'CERRADO' }));
    if (ruta === '/eventos/5/consumo') return respuesta();
    return undefined;
  });
}

beforeEach(_reiniciarCliente);
afterEach(() => vi.unstubAllGlobals());

it.each<Rol>(['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'])('permite consultar desde la ficha a %s y solo hace GET', async (rol) => {
  const fetchMock = backend([rol]);
  const persona = userEvent.setup();
  montar('/eventos/5');
  await persona.click(await screen.findByRole('tab', { name: 'Consumo de bebida' }));
  const tabla = await screen.findByRole('table', { name: 'Consumo por bebida' });
  const fila = within(tabla).getByRole('row', { name: /Fernet/ });
  for (const texto of ['2 cajas', '1 caja', '2 botellas', '4 botellas', '0,0267 botellas']) {
    expect(within(fila).getByText(texto)).toBeInTheDocument();
  }
  expect(screen.getByText('Calculado sobre 150 invitados, no sobre asistencia real.')).toBeInTheDocument();
  expect(pedidos(fetchMock).filter((p) => p.ruta.endsWith('/consumo'))).toEqual([
    { metodo: 'GET', ruta: '/eventos/5/consumo', cuerpo: undefined },
  ]);
  expect(screen.queryByRole('button', { name: /Cerrar evento|Guardar/ })).not.toBeInTheDocument();
});

it.each<Rol>(['VENDEDORA', 'PLANNER', 'BARRA', 'COCINA'])('no consulta consumo con %s ni mediante enlace directo', async (rol) => {
  const fetchMock = backend([rol]);
  montar('/eventos/5?pestana=consumo');
  await screen.findByRole(rol === 'BARRA' || rol === 'COCINA' ? 'heading' : 'tab', {
    name: rol === 'BARRA' || rol === 'COCINA' ? 'Tu perfil no tiene acceso a esta pantalla' : 'Datos',
  });
  expect(screen.queryByRole('tab', { name: 'Consumo de bebida' })).not.toBeInTheDocument();
  expect(pedidos(fetchMock).some((p) => p.ruta.endsWith('/consumo'))).toBe(false);
});

it('muestra evento cerrado sin registros', async () => {
  backend(['ADMINISTRACION'], () => json(200, { ...CONSUMO, renglones: [], consumoTotal: 0, porInvitadoTotal: 0 }));
  montar('/eventos/5?pestana=consumo');
  expect(await screen.findByText('Evento cerrado sin consumo')).toBeInTheDocument();
  expect(screen.getByText('No hay registros de consumo para este evento cerrado.')).toBeInTheDocument();
});

it('mantiene el detalle si hay registros con consumo cero', async () => {
  backend(['COMPRAS'], () => json(200, { ...CONSUMO, consumoTotal: 0, porInvitadoTotal: 0,
    renglones: [{ ...CONSUMO.renglones[0], consumo: 0, devuelto: 10, porInvitado: 0 }] }));
  montar('/eventos/5?pestana=consumo');
  expect(await screen.findByText('Evento cerrado sin consumo')).toBeInTheDocument();
  expect(screen.getByRole('row', { name: /Fernet/ })).toBeInTheDocument();
});

it.each([null, 0])('sin invitados válidos (%s) no presenta un cociente', async (invitados) => {
  backend(['COMPRAS'], () => json(200, { ...CONSUMO, invitados, porInvitadoTotal: null,
    renglones: [{ ...CONSUMO.renglones[0], porInvitado: null }], usaSegmentos: true }));
  montar('/eventos/5?pestana=consumo');
  expect(await screen.findByText(/La cantidad de invitados es desconocida o cero/)).toBeInTheDocument();
  expect(screen.getAllByText('No disponible')).toHaveLength(2);
  expect(screen.getByText(/Este evento usa segmentos/)).toBeInTheDocument();
});

it('distingue falta de cierre de consumo cero y permite actualizar', async () => {
  let cerrado = false;
  backend(['COMPRAS'], () => cerrado ? json(200, CONSUMO) : problema(409, 'EVENTO_NO_CERRADO', 'Pendiente de cierre'));
  const persona = userEvent.setup();
  montar('/eventos/5?pestana=consumo');
  expect(await screen.findByText('Consumo pendiente de cierre')).toBeInTheDocument();
  expect(screen.queryByText('Evento cerrado sin consumo')).not.toBeInTheDocument();
  cerrado = true;
  await persona.click(screen.getByRole('button', { name: 'Actualizar consumo' }));
  expect(await screen.findByRole('table', { name: 'Consumo por bebida' })).toBeInTheDocument();
});

it('muestra errores y permite reintentar', async () => {
  let fallar = true;
  backend(['ADMINISTRACION'], () => fallar ? problema(500, 'ERROR', 'Error de consulta') : json(200, CONSUMO));
  const persona = userEvent.setup();
  montar('/eventos/5?pestana=consumo');
  expect(await screen.findByText('Error de consulta')).toBeInTheDocument();
  fallar = false;
  await persona.click(screen.getByRole('button', { name: 'Reintentar' }));
  expect(await screen.findByRole('table', { name: 'Consumo por bebida' })).toBeInTheDocument();
});

it('informa que está cargando mientras espera el consumo', async () => {
  let responder!: (respuesta: Response) => void;
  backendFalso(sesionDe(['ADMINISTRACION']), ({ ruta }) => {
    if (ruta === '/eventos/5') return json(200, fichaDePrueba({ estado: 'CERRADO' }));
    if (ruta === '/eventos/5/consumo') return new Promise<Response>((resolver) => { responder = resolver; });
    return undefined;
  });
  montar('/eventos/5?pestana=consumo');
  await screen.findByRole('tab', { name: 'Consumo de bebida' });
  expect(screen.getByRole('status')).toHaveTextContent('Cargando…');
  responder(json(200, CONSUMO));
  expect(await screen.findByRole('table', { name: 'Consumo por bebida' })).toBeInTheDocument();
});
