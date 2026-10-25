import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import type { EventoResumen } from '../../api/eventos';
import { INTERVALO_CONTADOR_MS, type Notificacion } from '../../api/notificaciones';
import { backendFalso, json, montar, pedidos, sesionDe } from '../../test/backendFalso';
import { fichaDePrueba } from '../../test/fichas';

const AVISOS: Notificacion[] = [
  { id: 30, tipo: 'CONFIRMACION', mensaje: 'Evento confirmado: Bruno y Martina · Avril · sáb 14/11 · Noche', eventoId: 5, fechaHora: '2026-10-05T14:32:00-03:00', leida: false },
  { id: 29, tipo: 'SENA', mensaje: 'Seña registrada: Quince de Delfina · Club de Campo · sáb 21/11 · Noche', eventoId: 6, fechaHora: '2026-10-05T11:05:00-03:00', leida: false },
  { id: 20, tipo: 'REPROGRAMACION', mensaje: 'Evento reprogramado: Grupo Arcor', eventoId: 7, fechaHora: '2026-10-04T18:20:00-03:00', leida: true },
];

function backend(roles: Rol[] = ['ADMINISTRACION'], { sinLeer = 2, hayMas = false } = {}) {
  let pendientes = sinLeer;
  return backendFalso(sesionDe(roles, 'Gabriela Paz', 2), ({ metodo, ruta }) => {
    if (ruta === '/notificaciones/sin-leer') return json(200, { cantidad: pendientes });
    if (ruta === '/notificaciones?soloSinLeer=true&pagina=0') {
      return json(200, { notificaciones: pendientes ? AVISOS.filter((a) => !a.leida) : [], pagina: 0, hayMas: false, sinLeer: pendientes });
    }
    if (ruta === '/notificaciones?soloSinLeer=false&pagina=0') return json(200, { notificaciones: AVISOS, pagina: 0, hayMas, sinLeer: pendientes });
    if (ruta === '/notificaciones?soloSinLeer=false&pagina=1') {
      return json(200, { notificaciones: [{ ...AVISOS[2], id: 10, mensaje: 'Aviso viejo' }], pagina: 1, hayMas: false, sinLeer: pendientes });
    }
    if (metodo === 'POST' && /^\/notificaciones\/\d+\/lectura$/.test(ruta)) {
      pendientes -= 1;
      return new Response(null, { status: 204 });
    }
    if (metodo === 'POST' && ruta === '/notificaciones/lectura') {
      pendientes = 0;
      return new Response(null, { status: 204 });
    }
    if (ruta === '/eventos') return json(200, [] as EventoResumen[]);
    if (ruta === '/eventos/5') return json(200, fichaDePrueba());
    return undefined;
  });
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('UI-22 · notificaciones', () => {
  it('la campana muestra cuántas hay sin leer', async () => {
    backend();
    montar('/eventos');

    expect(await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' })).toHaveTextContent('2');
  });

  it('el panel lista las sin leer y abrir una la marca y lleva a la ficha', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    const router = montar('/eventos');

    await usuario.click(await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' }));
    const panel = screen.getByRole('dialog', { name: 'Notificaciones' });
    expect(await within(panel).findByText('2 nuevas')).toBeInTheDocument();
    expect(within(panel).getAllByRole('listitem')).toHaveLength(2);
    await usuario.click(within(panel).getByRole('button', { name: /Evento confirmado: Bruno y Martina/ }));

    expect(router.state.location.pathname).toBe('/eventos/5');
    expect(screen.queryByRole('dialog', { name: 'Notificaciones' })).not.toBeInTheDocument();
    expect(pedidos(fetchMock).some((p) => p.metodo === 'POST' && p.ruta === '/notificaciones/30/lectura')).toBe(true);
    expect(await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' })).toBeInTheDocument();
  });

  it('en «Todas» se ven también las leídas y se cargan más de a 20', async () => {
    backend(['ADMINISTRACION'], { hayMas: true });
    const usuario = userEvent.setup();
    montar('/eventos');

    await usuario.click(await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' }));
    const panel = screen.getByRole('dialog', { name: 'Notificaciones' });
    await usuario.click(within(panel).getByRole('tab', { name: 'Todas' }));

    expect(await within(panel).findByText('Evento reprogramado: Grupo Arcor')).toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: /Seña registrada/ })).toHaveTextContent(/^Sin leer:/);
    expect(within(panel).getByRole('button', { name: /Evento reprogramado/ })).not.toHaveTextContent(/Sin leer/);
    await usuario.click(within(panel).getByRole('button', { name: 'Ver más' }));
    expect(await within(panel).findByText('Aviso viejo')).toBeInTheDocument();
    expect(within(panel).getAllByRole('listitem')).toHaveLength(4);
  });

  it('«Marcar todas como leídas» deja el contador en cero', async () => {
    backend();
    const usuario = userEvent.setup();
    montar('/eventos');

    await usuario.click(await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' }));
    const panel = screen.getByRole('dialog', { name: 'Notificaciones' });
    await within(panel).findByText('2 nuevas');
    await usuario.click(within(panel).getByRole('button', { name: 'Marcar todas como leídas' }));

    expect(await within(panel).findByText('No tenés avisos sin leer')).toBeInTheDocument();
    await usuario.click(within(panel).getByRole('button', { name: 'Cerrar' }));
    // Por el título: el menú también tiene un ítem «Notificaciones».
    expect(await screen.findByTitle('Notificaciones')).not.toHaveTextContent(/\d/);
  });

  it('abrir un aviso de modificación lleva al historial con ese guardado resaltado', async () => {
    const aviso: Notificacion = {
      id: 31, tipo: 'MODIFICACION', mensaje: 'Lucía Ferreyra cambió la cantidad de invitados de tu evento: Bruno y Martina',
      eventoId: 5, fechaHora: '2026-10-06T09:41:00.250-03:00', leida: false,
    };
    const lucia = { id: 1, nombre: 'Lucía Ferreyra' };
    backendFalso(sesionDe(['ADMINISTRACION'], 'Gabriela Paz', 2), ({ metodo, ruta }) => {
      if (ruta === '/notificaciones/sin-leer') return json(200, { cantidad: 1 });
      if (ruta === '/notificaciones?soloSinLeer=true&pagina=0') {
        return json(200, { notificaciones: [aviso], pagina: 0, hayMas: false, sinLeer: 1 });
      }
      if (metodo === 'POST' && ruta === '/notificaciones/31/lectura') return new Response(null, { status: 204 });
      if (ruta === '/eventos') return json(200, [] as EventoResumen[]);
      if (ruta === '/eventos/5') {
        return json(200, fichaDePrueba({
          historial: [
            { tipo: 'ESTADO', estadoNuevo: 'PRE_RESERVA', usuario: lucia, fechaHora: '2026-09-21T10:15:00-03:00' },
            { tipo: 'MODIFICACION', campo: 'nombre', valorAnterior: 'B y M', valorNuevo: 'Bruno y Martina', usuario: lucia, fechaHora: '2026-10-01T12:00:00-03:00' },
            { tipo: 'MODIFICACION', campo: 'cantidad_invitados', valorAnterior: '120', valorNuevo: '150', usuario: lucia, fechaHora: '2026-10-06T09:41:00.100-03:00' },
            { tipo: 'MODIFICACION', campo: 'hora_inicio', valorAnterior: '21:00', valorNuevo: '21:30', usuario: lucia, fechaHora: '2026-10-07T18:00:00-03:00' },
          ],
        }));
      }
      return undefined;
    });
    const usuario = userEvent.setup();
    const router = montar('/eventos');

    await usuario.click(await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' }));
    const panel = screen.getByRole('dialog', { name: 'Notificaciones' });
    await usuario.click(await within(panel).findByRole('button', { name: /cambió la cantidad de invitados/ }));

    expect(router.state.location.pathname).toBe('/eventos/5');
    expect(new URLSearchParams(router.state.location.search).get('pestana')).toBe('historial');
    const avisado = (await screen.findByText('Invitados: 120 → 150.')).closest('li');
    expect(avisado).toHaveAttribute('aria-current', 'true');
    expect(screen.getByText('Hora de inicio: 21:00 → 21:30.').closest('li')).not.toHaveAttribute('aria-current');
    expect(screen.getByText('Nombre del evento: B y M → Bruno y Martina.').closest('li')).not.toHaveAttribute('aria-current');
  });

  it('a Cocina el aviso la lleva a la tarjeta del evento en la vista de cocina', async () => {

    const fetchMock = backend(['COCINA']);
    const usuario = userEvent.setup();
    const router = montar('/mi-cuenta');

    await usuario.click(await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' }));
    const panel = screen.getByRole('dialog', { name: 'Notificaciones' });
    await usuario.click(await within(panel).findByRole('button', { name: /Evento confirmado/ }));

    expect(router.state.location.pathname).toBe('/cocina');
    expect(router.state.location.search).toBe('?evento=5');
    expect(screen.queryByRole('dialog', { name: 'Notificaciones' })).not.toBeInTheDocument();
    expect(pedidos(fetchMock).some((p) => p.ruta === '/notificaciones/30/lectura')).toBe(true);
  });

  it('el contador se actualiza cada minuto', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const fetchMock = backend();
    montar('/eventos');
    await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' });
    const contar = () => pedidos(fetchMock).filter((p) => p.ruta === '/notificaciones/sin-leer').length;
    const antes = contar();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(INTERVALO_CONTADOR_MS);
    });

    expect(contar()).toBe(antes + 1);
  });

  it('también se consultan como página desde el menú', async () => {
    backend(['VENDEDORA']);
    montar('/notificaciones');

    expect(await screen.findByRole('heading', { level: 1, name: 'Notificaciones' })).toBeInTheDocument();
    expect(await screen.findByText(/Evento confirmado: Bruno y Martina/)).toBeInTheDocument();
  });
});


describe('notificaciones con Cocina y otros perfiles', () => {
  it.each([
    ['COCINA'], ['COCINA', 'VENDEDORA'], ['COCINA', 'BARRA'],
  ] satisfies Rol[][])('con %j abre cocina', async (...roles: Rol[]) => {
    backend(roles);
    const router = montar('/mi-cuenta');
    await userEvent.click(await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' }));
    await userEvent.click(await within(screen.getByRole('dialog', { name: 'Notificaciones' })).findByRole('button', { name: /Evento confirmado/ }));
    expect(router.state.location.pathname).toBe('/cocina');
    expect(router.state.location.search).toBe('?evento=5');
  });

  it.each(['COMPRAS', 'PLANNER', 'ADMINISTRACION', 'DIRECCION', 'COORDINACION'] satisfies Rol[])('Cocina + %s conserva la ficha', async (rol) => {
    backend(['COCINA', rol]);
    const router = montar('/mi-cuenta');
    await userEvent.click(await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' }));
    await userEvent.click(await within(screen.getByRole('dialog', { name: 'Notificaciones' })).findByRole('button', { name: /Evento confirmado/ }));
    expect(router.state.location.pathname).toBe('/eventos/5');
  });
});


describe('Cocina + Vendedora en avisos propios y ajenos', () => {
  it.each([true, false])('evento propio=%s abre cocina sin intentar la ficha', async (propio) => {
    const fetchMock = backendFalso(sesionDe(['COCINA', 'VENDEDORA'], 'Cocina vendedora', propio ? 1 : 2), ({ ruta }) => {
      if (ruta === '/notificaciones/sin-leer') return json(200, { cantidad: 1 });
      if (ruta.startsWith('/notificaciones?')) return json(200, { notificaciones: [AVISOS[0]], pagina: 0, hayMas: false, sinLeer: 1 });
      if (ruta === '/notificaciones/30/lectura') return new Response(null, { status: 204 });
    });
    const router = montar('/mi-cuenta');
    await userEvent.click(await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' }));
    await userEvent.click(await within(screen.getByRole('dialog', { name: 'Notificaciones' })).findByRole('button', { name: /Evento confirmado/ }));
    expect(router.state.location.pathname).toBe('/cocina');
    expect(pedidos(fetchMock).some((p) => p.ruta === '/eventos/5')).toBe(false);
  });
});
