import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import type { Cocina, TarjetaCocina } from '../../api/cocina';
import { backendFalso, json, montar, pedidos, problema, sesionDe } from '../../test/backendFalso';

const BRUNO: TarjetaCocina = {
  id: 5, nombre: 'Bruno y Martina', tipo: 'Casamiento', fecha: '2030-03-10', salon: { codigo: 'avril', nombre: 'Avril' },
  turno: 'noche', turnoNombre: 'Noche', hora: '21:30:00', invitados: 180,
  servicios: [
    { categoria: 'Menú', renglones: [], detalle: 'Lomo braseado con papas rústicas' },
    { categoria: 'Menús especiales', renglones: [{ texto: 'Celíaco', cantidad: 2 }, { texto: 'Sin lactosa', cantidad: 3 }], detalle: null },
  ],
};

const ARCOR: TarjetaCocina = {
  id: 7, nombre: 'Corporativo Arcor', tipo: 'Corporativo', fecha: '2030-03-10', salon: { codigo: 'club', nombre: 'Club de Campo' },
  turno: 'mediodia', turnoNombre: 'Mediodía', hora: '12:00:00', invitados: null, servicios: [],
};

const MARZO: Cocina = {
  mes: '2030-03', hoy: '2030-03-10', hasta: '2030-03-25',
  salones: [{ codigo: 'avril', nombre: 'Avril' }, { codigo: 'club', nombre: 'Club de Campo' }, { codigo: 'santa-barbara', nombre: 'Santa Bárbara' }],
  jornadasAnteriores: [],
  eventos: [BRUNO, ARCOR, { ...ARCOR, id: 9, nombre: 'Egresados', fecha: '2030-03-20' }],
};

function backend(roles: Rol[] = ['COCINA']) {
  return backendFalso(sesionDe(roles, 'Julián Torres'), ({ ruta }) => {
    if (ruta === '/cocina?mes=2030-03') return json(200, MARZO);
    if (ruta.startsWith('/cocina?mes=')) return json(200, { ...MARZO, mes: ruta.slice(-7), eventos: [] });
    if (ruta === '/cocina/eventos/5') return json(200, BRUNO);
    if (ruta === '/cocina/eventos/8') return problema(404, 'FUERA_DE_COCINA', 'Ese evento ya no figura en la vista de cocina: se canceló, se reprogramó o todavía no está confirmado.');
    return undefined;
  });
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('A7-02 · vista de cocina', () => {
  it('abre en hoy con una tarjeta por evento, en orden de hora, con lo que hay que preparar', async () => {
    backend();
    montar('/cocina?mes=2030-03');

    const tarjetas = await screen.findAllByRole('article');
    expect(tarjetas.map((t) => within(t).getByRole('heading').textContent)).toEqual(['Corporativo Arcor', 'Bruno y Martina']);

    const bruno = tarjetas[1];
    expect(within(bruno).getByText('21:30')).toBeInTheDocument();
    expect(within(bruno).getByText('180 invitados')).toBeInTheDocument();
    expect(within(bruno).getByText('Avril')).toBeInTheDocument();
    expect(within(bruno).getByText('Lomo braseado con papas rústicas')).toBeInTheDocument();
    expect(within(bruno).getByText('Celíaco').closest('dd')).toHaveTextContent('2Celíaco');
    expect(within(tarjetas[0]).getByText('Todavía no hay servicios de cocina cargados.')).toBeInTheDocument();
    expect(screen.getByText(/Ves los eventos confirmados desde hoy hasta el/)).toBeInTheDocument();
  });

  it('no muestra el estado de los eventos: la agenda solo dice que hay evento', async () => {
    backend();
    montar('/cocina?mes=2030-03');

    await screen.findAllByRole('article');
    expect(screen.queryByText('Confirmado')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^domingo 10 de marzo/ })).toHaveAccessibleName(
      'domingo 10 de marzo. Club de Campo mediodía: con evento; Avril noche: con evento',
    );
  });

  it('elegir otro día muestra sus eventos o explica por qué no hay', async () => {
    backend();
    const usuario = userEvent.setup();
    montar('/cocina?mes=2030-03');
    await screen.findAllByRole('article');

    await usuario.click(screen.getByRole('button', { name: /^miércoles 20 de marzo/ }));
    expect(await screen.findByRole('heading', { name: 'Egresados' })).toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: /^sábado 30 de marzo/ }));
    expect(await screen.findByText(/los eventos de ese día aparecen cuando falten menos días/)).toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: /^jueves 14 de marzo/ }));
    expect(await screen.findByText('No hay eventos para cocinar este día.')).toBeInTheDocument();
  });

  it('desde un aviso abre el día del evento y resalta su tarjeta', async () => {
    const fetchMock = backend();
    const router = montar('/cocina?evento=5');

    const bruno = await screen.findByRole('article', { current: true });
    expect(within(bruno).getByRole('heading', { name: 'Bruno y Martina' })).toBeInTheDocument();
    expect(router.state.location.search).toContain('mes=2030-03');
    expect(router.state.location.search).toContain('dia=2030-03-10');
    expect(pedidos(fetchMock).some((p) => p.ruta === '/cocina/eventos/5')).toBe(true);
  });

  it('si el evento del aviso ya no está en la vista, lo dice', async () => {
    backend();
    montar('/cocina?mes=2030-03&evento=8');

    expect(await screen.findByText(/Ese evento ya no figura en la vista de cocina/)).toBeInTheDocument();
    expect(screen.getAllByRole('article')).toHaveLength(2);
  });

  it('sin límite de días no lo anuncia', async () => {
    backendFalso(sesionDe(['PLANNER']), ({ ruta }) => (ruta === '/cocina?mes=2030-03' ? json(200, { ...MARZO, hasta: null }) : undefined));
    montar('/cocina?mes=2030-03');

    await screen.findAllByRole('article');
    expect(screen.queryByText(/Ves los eventos confirmados desde hoy/)).not.toBeInTheDocument();
  });
});


describe('jornadas anteriores y resolución de avisos', () => {
  it.each([
    ['2032-06-01', '2032-05-31'],
    ['2033-01-01', '2032-12-31'],
    ['2032-05-10', '2032-05-09'],
  ])('al entrar el %s muestra la jornada %s una sola vez', async (hoy, fecha) => {
    const anterior = { ...BRUNO, fecha };
    backendFalso(sesionDe(['COCINA']), ({ ruta }) => ruta.startsWith('/cocina?mes=')
      ? json(200, { ...MARZO, mes: hoy.slice(0, 7), hoy, eventos: [], jornadasAnteriores: [anterior] }) : undefined);
    montar('/cocina?mes=' + hoy.slice(0, 7));
    const tarjetas = await screen.findAllByRole('article');
    expect(tarjetas).toHaveLength(1);
    expect(within(tarjetas[0]).getByRole('heading', { name: BRUNO.nombre })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Jornadas anteriores' })).toBeInTheDocument();
    expect(screen.queryByText('En curso')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Mes anterior' }));
    expect(await screen.findAllByRole('article')).toHaveLength(1);
  });

  it('el enlace a una jornada anterior mantiene su fecha y no duplica la tarjeta', async () => {
    const anterior = { ...BRUNO, fecha: '2030-02-28' };
    backendFalso(sesionDe(['COCINA']), ({ ruta }) => {
      if (ruta.startsWith('/cocina?mes=')) return json(200, { ...MARZO, eventos: [], jornadasAnteriores: [anterior] });
      if (ruta === '/cocina/eventos/5') return json(200, anterior);
    });
    const router = montar('/cocina?evento=5');
    await screen.findByRole('article', { current: true });
    expect(router.state.location.search).toContain('dia=2030-02-28');
    expect(screen.getAllByRole('article')).toHaveLength(1);
  });

  it.each([false, true])('ignora una respuesta antigua aunque sea error=%s', async (fallo) => {
    let terminar!: (r: Response) => void;
    backendFalso(sesionDe(['COCINA']), ({ ruta }) => {
      if (ruta.startsWith('/cocina?mes=')) return json(200, MARZO);
      if (ruta === '/cocina/eventos/5') return new Promise<Response>((resolve) => { terminar = resolve; });
      if (ruta === '/cocina/eventos/7') return json(200, ARCOR);
    });
    const router = montar('/cocina?mes=2030-03&evento=5');
    await waitFor(() => expect(terminar).toBeTypeOf('function'));
    await act(async () => { await router.navigate('/cocina?mes=2030-03&evento=7'); });
    expect(within(await screen.findByRole('article', { current: true })).getByRole('heading')).toHaveTextContent(ARCOR.nombre);
    await act(async () => { terminar(fallo ? problema(503, 'TEMPORAL', 'Respuesta vieja') : json(200, { ...BRUNO, fecha: '2030-02-28' })); });
    expect(router.state.location.search).toContain('evento=7');
    expect(router.state.location.search).toContain('dia=2030-03-10');
    expect(screen.queryByText('Respuesta vieja')).not.toBeInTheDocument();
  });

  it('permite reintentar el mismo aviso tras un error transitorio', async () => {
    let intentos = 0;
    backendFalso(sesionDe(['COCINA']), ({ ruta }) => {
      if (ruta.startsWith('/cocina?mes=')) return json(200, MARZO);
      if (ruta === '/cocina/eventos/5') return ++intentos === 1
        ? problema(503, 'TEMPORAL', 'Fallo transitorio') : json(200, BRUNO);
    });
    montar('/cocina?mes=2030-03&evento=5');
    await screen.findByText('Fallo transitorio');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    await waitFor(() => expect(screen.queryByText('Fallo transitorio')).not.toBeInTheDocument());
    expect(intentos).toBe(2);
    expect(within(screen.getByRole('article', { current: true })).getByRole('heading')).toHaveTextContent(BRUNO.nombre);
  });
});
