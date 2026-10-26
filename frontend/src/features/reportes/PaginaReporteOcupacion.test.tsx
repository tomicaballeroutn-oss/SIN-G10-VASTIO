import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import { backendFalso, json, montar, pedidos, problema, sesionDe } from '../../test/backendFalso';

const reporte = {
  desde: '2038-01-01',
  hasta: '2038-12-31',
  regimen: null,
  resumen: { unidadesOcupadas: 2, ocupacionPromedio: 50, eventosRealizados: 1 },
  porSalon: [{ salonId: 2, codigo: 'club', salon: 'Club', unidadesOcupadas: 2, unidadesDisponibles: 4, ocupacion: 50 }],
  porVendedoraYTipo: [{ vendedoraId: 7, vendedora: 'Lucía Ferreyra', tipoEventoId: 2, tipoEvento: 'Quince', eventosIncluidos: 2 }],
  filtros: {
    salones: [{ id: 2, nombre: 'Club' }],
    tiposEvento: [{ id: 2, nombre: 'Quince' }],
    vendedoras: [{ id: 7, nombre: 'Lucía Ferreyra' }],
  },
};

function backend(roles: Rol[] = ['DIRECCION']) {
  return backendFalso(sesionDe(roles), ({ ruta }) =>
    ruta.startsWith('/reportes/ocupacion') ? json(200, reporte) : undefined,
  );
}

const printOriginal = window.print;
beforeEach(_reiniciarCliente);
afterEach(() => {
  vi.unstubAllGlobals();
  window.print = printOriginal;
});

it('muestra indicadores, gráfico, tablas y solicita el período inicial', async () => {
  const mock = backend();
  montar('/reportes');

  expect(await screen.findByRole('heading', { name: 'Reporte de ocupación', level: 1 })).toBeInTheDocument();
  expect(await screen.findByText('Unidades ocupadas')).toBeInTheDocument();
  expect(await screen.findByText('Ocupación promedio')).toBeInTheDocument();
  expect(await screen.findByText('Eventos realizados')).toBeInTheDocument();
  expect(screen.getByRole('img', { name: 'Club: 50 por ciento' })).toBeInTheDocument();
  const tablaSalones = screen.getByRole('table', { name: 'Ocupación por salón' });
  expect(within(tablaSalones).getByText('Club')).toBeInTheDocument();
  expect(screen.getByRole('table', { name: 'Eventos por vendedora y tipo' })).toBeInTheDocument();
  expect(pedidos(mock).length).toBeGreaterThanOrEqual(1);
  const consultas = pedidos(mock).filter((pedido) => pedido.ruta.startsWith('/reportes/ocupacion'));
  expect(consultas.length).toBeGreaterThanOrEqual(1);
  expect(consultas[0].ruta).toMatch(/^\/reportes\/ocupacion\?desde=\d{4}-01-01&hasta=\d{4}-12-31$/);
});

it('vuelve a consultar al cambiar filtros y mantiene la vista de impresión disponible', async () => {
  const mock = backend(['COORDINACION']);
  const persona = userEvent.setup();
  montar('/reportes');
  await screen.findByRole('table', { name: 'Ocupación por salón' });

  const pedidosIniciales = pedidos(mock).length;
  await persona.selectOptions(screen.getByLabelText('Salón'), '2');
  await waitFor(() => expect(pedidos(mock).length).toBeGreaterThan(pedidosIniciales));
  expect(pedidos(mock).at(-1)?.ruta).toContain('&salonId=2');

  const imprimir = vi.fn();
  window.print = imprimir;
  await persona.click(screen.getByRole('button', { name: 'Imprimir' }));
  expect(imprimir).toHaveBeenCalledOnce();
});

it.each<Rol>(['ADMINISTRACION', 'COMPRAS', 'VENDEDORA', 'PLANNER', 'BARRA', 'COCINA'])(
  'no monta el reporte ni consulta el endpoint para %s', async (rol) => {
    const mock = backend([rol]);
    montar('/reportes');

    expect(await screen.findByText('Tu perfil no tiene acceso a esta pantalla')).toBeInTheDocument();
    expect(pedidos(mock).filter((pedido) => pedido.ruta.startsWith('/reportes/ocupacion'))).toEqual([]);
  },
);


it('muestra los estados vacíos sin inventar eventos', async () => {
  backendFalso(sesionDe(['DIRECCION']), ({ ruta }) => ruta.startsWith('/reportes/ocupacion') ? json(200, {
    ...reporte, resumen: { unidadesOcupadas: 0, ocupacionPromedio: 0, eventosRealizados: 0 }, porSalon: [], porVendedoraYTipo: [],
  }) : undefined);
  montar('/reportes');
  expect(await screen.findByText('No hay salones para mostrar')).toBeInTheDocument();
  expect(screen.getByText('No hay eventos para este filtro')).toBeInTheDocument();
});

it('informa un error y permite reintentar la consulta', async () => {
  let falla = true;
  backendFalso(sesionDe(['DIRECCION']), ({ ruta }) => ruta.startsWith('/reportes/ocupacion')
    ? falla ? problema(500, 'ERROR', 'Error de prueba') : json(200, reporte) : undefined);
  montar('/reportes');
  expect(await screen.findByText('Error de prueba')).toBeInTheDocument();
  falla = false;
  await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
  expect(await screen.findByRole('table', { name: 'Ocupación por salón' })).toBeInTheDocument();
});

it('ajusta alta y baja y restaura el rango de ambas temporadas', async () => {
  const mock = backend();
  const persona = userEvent.setup();
  montar('/reportes');
  await screen.findByRole('table', { name: 'Ocupación por salón' });
  const desde = screen.getByLabelText('Desde') as HTMLInputElement;
  const hasta = screen.getByLabelText('Hasta') as HTMLInputElement;
  const anio = desde.value.slice(0, 4);
  await persona.selectOptions(screen.getByLabelText('Régimen de temporada'), 'ALTA');
  expect(desde).toHaveValue(anio + '-10-01');
  expect(hasta).toHaveValue(anio + '-12-31');
  await waitFor(() => expect(pedidos(mock).at(-1)?.ruta).toContain('regimen=ALTA'));
  await persona.selectOptions(screen.getByLabelText('Régimen de temporada'), 'BAJA');
  expect(desde).toHaveValue(anio + '-01-01');
  expect(hasta).toHaveValue(anio + '-09-30');
  await persona.selectOptions(screen.getByLabelText('Régimen de temporada'), '');
  expect(desde).toHaveValue(anio + '-01-01');
  expect(hasta).toHaveValue(anio + '-12-31');
});

it('permite limpiar una fecha sin desmontar la pantalla', async () => {
  backend();
  montar('/reportes');
  await screen.findByRole('table', { name: 'Ocupación por salón' });
  await userEvent.clear(screen.getByLabelText('Desde'));
  expect(screen.getByRole('heading', { name: 'Reporte de ocupación', level: 1 })).toBeInTheDocument();
  expect(screen.getByLabelText('Desde')).toHaveValue('');
});


it('no imprime filtros nuevos con los resultados de la consulta anterior', async () => {
  let responder: ((respuesta: Response) => void) | undefined;
  backendFalso(sesionDe(['DIRECCION']), ({ ruta }) => {
    if (!ruta.startsWith('/reportes/ocupacion')) return undefined;
    return ruta.includes('salonId=2') ? new Promise<Response>((resolver) => { responder = resolver; }) : json(200, reporte);
  });
  montar('/reportes');
  await screen.findByRole('table', { name: 'Ocupación por salón' });
  await userEvent.selectOptions(screen.getByLabelText('Salón'), '2');
  await waitFor(() => expect(responder).toBeDefined());
  expect(screen.getByRole('button', { name: 'Imprimir' })).toBeDisabled();
  expect(document.querySelector('.reporte-ocupacion__impresion')).toHaveTextContent('Salón: Todos');
  responder?.(json(200, reporte));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Imprimir' })).toBeEnabled());
  expect(document.querySelector('.reporte-ocupacion__impresion')).toHaveTextContent('Salón: Club');
});
