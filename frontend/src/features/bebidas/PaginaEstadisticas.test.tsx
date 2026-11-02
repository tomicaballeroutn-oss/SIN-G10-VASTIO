import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import type { EstadisticasConsumo, MedidasConsumo } from '../../api/estadisticas';
import { backendFalso, json, montar, pedidos, problema, sesionDe } from '../../test/backendFalso';

const medidas: MedidasConsumo = {
  eventos: 3, eventosConConsumoRegistrado: 2, consumoTotal: 19, promedioPorEvento: 6.3333,
  eventosConInvitadosValidos: 2, invitadosRegistrados: 300, consumoConInvitadosValidos: 10,
  porInvitadoRegistrado: 0.0333, eventosConAsistenciaReal: 0,
};
const resultado: EstadisticasConsumo = {
  unidad: 'BOTELLAS', denominador: 'ASISTENTES', filtros: {}, resumen: medidas,
  porBebida: [{ bebidaId: 1, nombre: 'Fernet', presentacion: '750 ml', activo: false, medidas }],
  porTipoEvento: [{ tipoEventoId: 2, nombre: 'Quince', activo: true, medidas }],
  detalle: [{ tipoEventoId: 2, bebidaId: 1, medidas }],
};

function backend(roles: Rol[] = ['COMPRAS'], responder = () => json(200, resultado)) {
  return backendFalso(sesionDe(roles), ({ ruta }) => {
    if (ruta === '/tipos-evento') return json(200, [{ id: 2, nombre: 'Quince', activo: true }]);
    if (ruta === '/salones') return json(200, [{ id: 1, nombre: 'Avril', activo: true }]);
    if (ruta === '/bebidas') return json(200, [{ id: 1, nombre: 'Fernet', presentacion: '750 ml', activo: false }]);
    if (ruta.startsWith('/estadisticas/consumo')) return responder();
    return undefined;
  });
}
const consultas = (mock: ReturnType<typeof backend>) => pedidos(mock).filter((p) => p.ruta.startsWith('/estadisticas/consumo'));

beforeEach(_reiniciarCliente);
afterEach(() => vi.unstubAllGlobals());

it.each<Rol>(['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'])('permite consultar a %s con solicitudes GET', async (rol) => {
  const mock = backend([rol]);
  const persona = userEvent.setup();
  montar('/bebidas/estadisticas');
  await screen.findByText('Consultá el consumo histórico');
  expect(consultas(mock)).toEqual([]);
  await persona.click(screen.getByRole('button', { name: 'Consultar estadísticas' }));
  const tabla = await screen.findByRole('table', { name: 'Consumo por bebida' });
  expect(within(tabla).getByText('6,3333')).toBeInTheDocument();
  expect(within(tabla).getByText('0,0333')).toBeInTheDocument();
  expect(within(tabla).getByText('Fernet 750 ml · Dado de baja')).toBeInTheDocument();
  expect(screen.getByRole('table', { name: 'Consumo por tipo de evento' })).toBeInTheDocument();
  expect(screen.getByRole('table', { name: 'Detalle por tipo de evento y bebida' })).toBeInTheDocument();
  expect(screen.getByText(/El cálculo por asistente usa 2 de 3 eventos/)).toHaveTextContent('300 asistentes y 10 botellas');
  expect(screen.queryByText(/usan la asistencia real por segmento/)).not.toBeInTheDocument();
  expect(consultas(mock)).toEqual([{ metodo: 'GET', ruta: '/estadisticas/consumo', cuerpo: undefined }]);
  expect(pedidos(mock).every((p) => p.metodo === 'GET')).toBe(true);
});

it('combina filtros, no consulta al editarlos y distingue filtros aplicados', async () => {
  const mock = backend();
  const persona = userEvent.setup();
  montar('/bebidas/estadisticas');
  await screen.findByText('Consultá el consumo histórico');
  await persona.type(screen.getByLabelText('Fecha desde'), '2038-05-01');
  await persona.type(screen.getByLabelText('Fecha hasta'), '2038-05-31');
  await persona.selectOptions(screen.getByLabelText('Tipo de evento'), '2');
  await persona.selectOptions(screen.getByLabelText('Salón'), '1');
  await persona.selectOptions(screen.getByLabelText('Bebida'), '1');
  expect(consultas(mock)).toHaveLength(0);
  await persona.click(screen.getByRole('button', { name: 'Consultar estadísticas' }));
  await screen.findByRole('table', { name: 'Consumo por bebida' });
  expect(consultas(mock)[0].ruta).toBe('/estadisticas/consumo?desde=2038-05-01&hasta=2038-05-31&tipoEventoId=2&salonId=1&bebidaId=1');
  await persona.clear(screen.getByLabelText('Fecha desde'));
  expect(screen.getByText(/Filtros aplicados:/)).toHaveTextContent('Desde: 2038-05-01');
  expect(consultas(mock)).toHaveLength(1);
  await persona.click(screen.getByRole('button', { name: 'Consultar estadísticas' }));
  await screen.findByRole('table', { name: 'Consumo por bebida' });
  expect(consultas(mock)[1].ruta).not.toContain('desde=');
});

it('rechaza período invertido sin HTTP y permite corregirlo', async () => {
  const mock = backend();
  const persona = userEvent.setup();
  montar('/bebidas/estadisticas');
  await screen.findByText('Consultá el consumo histórico');
  await persona.type(screen.getByLabelText('Fecha desde'), '2038-06-01');
  await persona.type(screen.getByLabelText('Fecha hasta'), '2038-05-01');
  await persona.click(screen.getByRole('button', { name: 'Consultar estadísticas' }));
  expect(screen.getByRole('alert')).toHaveTextContent('Revisá el período');
  expect(consultas(mock)).toHaveLength(0);
  await persona.clear(screen.getByLabelText('Fecha hasta'));
  await persona.click(screen.getByRole('button', { name: 'Consultar estadísticas' }));
  await screen.findByRole('table', { name: 'Consumo por bebida' });
});

it('rechaza una fecha vacía con badInput nativo, sin confundirla con un filtro omitido', async () => {
  const mock = backend();
  const persona = userEvent.setup();
  montar('/bebidas/estadisticas');
  await screen.findByText('Consultá el consumo histórico');
  const fecha = screen.getByLabelText('Fecha desde') as HTMLInputElement;
  // jsdom no edita segmentos de fecha: el E2E reproduce este estado con teclado real.
  const validez = vi.spyOn(fecha, 'checkValidity').mockReturnValue(false);
  const formulario = fecha.closest('form')!;
  const validar = vi.spyOn(formulario, 'checkValidity').mockImplementation(() => fecha.checkValidity());
  await persona.click(screen.getByRole('button', { name: 'Consultar estadísticas' }));
  expect(screen.getByRole('alert')).toHaveTextContent('Revisá el período');
  expect(consultas(mock)).toHaveLength(0);
  validez.mockRestore();
  validar.mockRestore();
  await persona.click(screen.getByRole('button', { name: 'Consultar estadísticas' }));
  await screen.findByRole('table', { name: 'Consumo por bebida' });
  expect(consultas(mock)[0].ruta).toBe('/estadisticas/consumo');
});

it.each([
  [0, 0, null, 'No hay eventos cerrados para estos filtros'],
  [3, 0, 0, 'Eventos cerrados sin registros de consumo'],
  [3, 2, 0, 'Consumo registrado igual a cero'],
] as const)('distingue ausencia y consumo cero (%s eventos, %s registros)', async (eventos, registros, promedio, mensaje) => {
  backend(['COMPRAS'], () => json(200, { ...resultado, resumen: { ...medidas, eventos,
    eventosConConsumoRegistrado: registros, consumoTotal: 0, promedioPorEvento: promedio,
    porInvitadoRegistrado: null, eventosConInvitadosValidos: 0, invitadosRegistrados: 0, consumoConInvitadosValidos: 0 } }));
  const persona = userEvent.setup();
  montar('/bebidas/estadisticas');
  await persona.click(await screen.findByRole('button', { name: 'Consultar estadísticas' }));
  expect(await screen.findByText(mensaje)).toBeInTheDocument();
  expect(screen.getAllByText('No disponible').length).toBeGreaterThan(0);
});

it('presenta error y reintenta la misma consulta', async () => {
  let fallar = true;
  const mock = backend(['ADMINISTRACION'], () => fallar ? problema(500, 'ERROR', 'Error estadístico') : json(200, resultado));
  const persona = userEvent.setup();
  montar('/bebidas/estadisticas');
  await persona.click(await screen.findByRole('button', { name: 'Consultar estadísticas' }));
  expect(await screen.findByText('Error estadístico')).toBeInTheDocument();
  fallar = false;
  await persona.click(screen.getByRole('button', { name: 'Reintentar' }));
  await screen.findByRole('table', { name: 'Consumo por bebida' });
  expect(consultas(mock)).toHaveLength(2);
});

it('permite reintentar la carga de catálogos', async () => {
  let fallar = true;
  backendFalso(sesionDe(['COMPRAS']), ({ ruta }) => {
    if (ruta === '/tipos-evento') return fallar ? problema(500, 'ERROR', 'Error de catálogo') : json(200, []);
    if (ruta === '/salones' || ruta === '/bebidas') return json(200, []);
    return undefined;
  });
  const persona = userEvent.setup();
  montar('/bebidas/estadisticas');
  await screen.findByText('Error de catálogo');
  fallar = false;
  await persona.click(screen.getByRole('button', { name: 'Reintentar' }));
  expect(await screen.findByText('Consultá el consumo histórico')).toBeInTheDocument();
});

it.each<Rol>(['VENDEDORA', 'PLANNER', 'BARRA', 'COCINA'])('deniega enlace directo a %s sin consultar datos', async (rol) => {
  const mock = backend([rol]);
  montar('/bebidas/estadisticas');
  await screen.findByText('Tu perfil no tiene acceso a esta pantalla');
  expect(consultas(mock)).toHaveLength(0);
  expect(pedidos(mock).some((p) => ['/bebidas', '/tipos-evento', '/salones'].includes(p.ruta))).toBe(false);
});

it('un usuario con varios perfiles conserva el permiso de Compras', async () => {
  backend(['VENDEDORA', 'COMPRAS']);
  montar('/bebidas/estadisticas');
  expect(await screen.findByText('Consultá el consumo histórico')).toBeInTheDocument();
});

it('informa carga y descarta respuestas obsoletas de una consulta anterior', async () => {
  let primera!: (r: Response) => void;
  let cantidad = 0;
  backendFalso(sesionDe(['COMPRAS']), ({ ruta }) => {
    if (['/bebidas', '/tipos-evento', '/salones'].includes(ruta)) return json(200, []);
    if (ruta.startsWith('/estadisticas/consumo')) {
      cantidad++;
      return cantidad === 1 ? new Promise<Response>((resolver) => { primera = resolver; }) : json(200, resultado);
    }
    return undefined;
  });
  const persona = userEvent.setup();
  montar('/bebidas/estadisticas');
  await persona.click(await screen.findByRole('button', { name: 'Consultar estadísticas' }));
  expect(screen.getByRole('status')).toHaveTextContent('Cargando');
  await persona.click(screen.getByRole('button', { name: 'Consultar estadísticas' }));
  await screen.findByRole('table', { name: 'Consumo por bebida' });
  await act(async () => { primera(problema(500, 'ANTERIOR', 'Respuesta obsoleta')); });
  expect(screen.queryByText('Respuesta obsoleta')).not.toBeInTheDocument();
});
