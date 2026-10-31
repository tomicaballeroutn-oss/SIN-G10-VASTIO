import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import { backendFalso, json, montar, pedidos, sesionDe } from '../../test/backendFalso';
import { EVENTOS_FILTROS } from '../../test/filtrosFixtures';

beforeEach(() => _reiniciarCliente());
afterEach(() => vi.unstubAllGlobals());

function backend(lista = EVENTOS_FILTROS, roles: Rol[] = ['DIRECCION'], id = 1) {
  return backendFalso(sesionDe(roles, 'Prueba de filtros', id), ({ ruta }) => ruta === '/eventos' ? json(200, lista) : undefined);
}

async function elegir(usuario: ReturnType<typeof userEvent.setup>, nombre: string, opciones: string[]) {
  await usuario.click(screen.getByRole('button', { name: new RegExp(`^${nombre}`) }));
  const grupo = screen.getByRole('group', { name: nombre });
  for (const opcion of opciones) await usuario.click(within(grupo).getByRole('checkbox', { name: opcion }));
  await usuario.click(screen.getByRole('button', { name: 'Listo' }));
}

const nombres = () => screen.queryAllByRole('heading', { level: 3 })
  .filter((e) => e.closest('.eventos__lista')).map((e) => e.textContent);

describe('búsqueda y filtros de Eventos', () => {
  it('busca todas las palabras sin depender de tildes o mayúsculas y navega a la ficha', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    const router = montar('/eventos');
    await screen.findByRole('heading', { name: 'Quince de Delfina' });
    expect(screen.queryByLabelText('Buscar eventos')).not.toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Mostrar filtros' }));
    await usuario.type(screen.getByLabelText('Buscar eventos'), '  RIOS delfina AVRIL  ');
    expect(nombres()).toEqual(['Quince de Delfina']);
    expect(screen.getByText('1 resultado')).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Ocultar filtros' }));
    expect(nombres()).toEqual(['Quince de Delfina']);
    await usuario.click(screen.getByRole('heading', { name: 'Quince de Delfina' }));
    expect(router.state.location.pathname).toBe('/eventos/1');
    expect(pedidos(fetchMock).filter((p) => p.ruta.startsWith('/eventos?'))).toHaveLength(0);
  });

  it.each([
    ['Estados', ['Señado', 'Contratado'], ['Quince de Delfina', 'Quince de Sofía']],
    ['Salones', ['Club de Campo'], ['Convención Sur']],
    ['Turnos', ['Noche'], ['Quince de Delfina', 'Quince de Sofía']],
    ['Tipos de evento', ['Corporativo'], ['Convención Sur']],
    ['Vendedoras', ['Sofía Méndez'], ['Convención Sur']],
    ['Planners', ['Sin planner'], ['Convención Sur']],
  ] as const)('aplica %s con opciones derivadas de la respuesta', async (grupo, valores, esperados) => {
    backend();
    const usuario = userEvent.setup();
    montar('/eventos');
    await screen.findByRole('heading', { name: 'Quince de Delfina' });
    await usuario.click(screen.getByRole('button', { name: 'Mostrar filtros' }));
    await elegir(usuario, grupo, [...valores]);
    expect(nombres()).toEqual([...esperados]);
  });

  it('combina varios valores por grupo con búsqueda y otros grupos', async () => {
    backend();
    const usuario = userEvent.setup();
    montar('/eventos');
    await screen.findByRole('heading', { name: 'Quince de Delfina' });
    await usuario.click(screen.getByRole('button', { name: 'Mostrar filtros' }));
    await elegir(usuario, 'Estados', ['Señado', 'Contratado']);
    await elegir(usuario, 'Salones', ['Avril', 'Club de Campo']);
    await elegir(usuario, 'Planners', ['Ana Sosa']);
    await usuario.type(screen.getByLabelText('Buscar eventos'), 'rios');
    expect(nombres()).toEqual(['Quince de Delfina']);
  });

  it('incluye ambos extremos de fecha, avisa un rango inválido y limpia sin perder datos', async () => {
    backend();
    const usuario = userEvent.setup();
    montar('/eventos');
    await screen.findByRole('heading', { name: 'Quince de Delfina' });
    await usuario.click(screen.getByRole('button', { name: 'Mostrar filtros' }));
    // Los controles date también reciben una fecha completa desde el calendario nativo.
    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2030-03-09' } });
    fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2030-03-09' } });
    expect(nombres()).toEqual(['Quince de Delfina']);
    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2030-03-10' } });
    expect(screen.getByRole('alert')).toHaveTextContent('La fecha Desde no puede ser posterior a Hasta.');
    expect(screen.getByText('No hay eventos que coincidan')).toBeInTheDocument();
    expect(nombres()).toEqual([]);
    await usuario.click(screen.getAllByRole('button', { name: 'Limpiar filtros' })[0]);
    expect(nombres()).toHaveLength(3);
    expect(screen.getByLabelText('Desde')).toHaveValue('');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('mantiene el alcance asignado de planner al combinar y limpiar filtros', async () => {
    backend(EVENTOS_FILTROS, ['PLANNER'], 7);
    const usuario = userEvent.setup();
    montar('/eventos');
    await screen.findByRole('heading', { name: 'Quince de Delfina' });
    await usuario.click(screen.getByRole('tab', { name: 'Asignados a mí' }));
    expect(nombres()).toEqual(['Quince de Delfina']);
    await usuario.click(screen.getByRole('button', { name: 'Mostrar filtros' }));
    await usuario.type(screen.getByLabelText('Buscar eventos'), 'inexistente');
    expect(screen.getByText('No hay eventos que coincidan')).toBeInTheDocument();
    await usuario.click(screen.getAllByRole('button', { name: 'Limpiar filtros' })[0]);
    expect(nombres()).toEqual(['Quince de Delfina']);
    expect(screen.getByRole('tab', { name: 'Asignados a mí' })).toHaveAttribute('aria-selected', 'true');
  });

  it('no obtiene catálogos ni opciones de personas ajenas a la respuesta autorizada', async () => {
    const fetchMock = backend([EVENTOS_FILTROS[0]], ['VENDEDORA']);
    const usuario = userEvent.setup();
    montar('/eventos');
    await screen.findByRole('heading', { name: 'Quince de Delfina' });
    await usuario.click(screen.getByRole('button', { name: 'Mostrar filtros' }));
    await usuario.click(screen.getByRole('button', { name: 'Vendedoras' }));
    const grupo = screen.getByRole('group', { name: 'Vendedoras' });
    expect(within(grupo).getAllByRole('checkbox')).toHaveLength(1);
    expect(within(grupo).queryByLabelText('Sofía Méndez')).not.toBeInTheDocument();
    expect(pedidos(fetchMock).every((p) => p.ruta === '/eventos' || p.ruta.startsWith('/notificaciones'))).toBe(true);
  });

  it.each([false, true])('distingue lista vacía de falta de asignaciones (planner=%s)', async (planner) => {
    backend([], planner ? ['PLANNER'] : ['DIRECCION'], 7);
    const usuario = userEvent.setup();
    montar('/eventos');
    await screen.findByText('No hay eventos próximos');
    if (planner) {
      await usuario.click(screen.getByRole('tab', { name: 'Asignados a mí' }));
      expect(screen.getByText('No tenés eventos asignados')).toBeInTheDocument();
    }
    await usuario.click(screen.getByRole('button', { name: 'Mostrar filtros' }));
    await usuario.click(screen.getByRole('button', { name: 'Estados' }));
    expect(screen.getByText('No hay opciones para mostrar.')).toBeInTheDocument();
  });
});
