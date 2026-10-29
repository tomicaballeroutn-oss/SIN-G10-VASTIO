import { act, render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Sesion } from '../../api/cliente';
import { rutas } from '../../rutas';
import { SesionProvider } from '../sesion/SesionProvider';

const sesion = (debeCambiarContrasena: boolean): Sesion => ({
  tokenAcceso: 'token',
  tokenAccesoVence: '2026-10-26T20:00:00Z',
  minutosExpiracionSesion: 60,
  minutosAvisoExpiracion: 5,
  usuario: { id: 7, nombreCompleto: 'Lucía Ferreyra', nombreUsuario: 'lucia.ferreyra', roles: ['VENDEDORA'], debeCambiarContrasena },
});

const json = (status: number, cuerpo: unknown) => new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } });

/**
 * Backend falso con una sola sesión (la cookie de refresco). `cambio` decide qué responde el cambio de contraseña;
 * si lo acepta, la sesión pasa a «ya no debe cambiar la contraseña», como en el servidor.
 */
function backend(inicial: Sesion, cambio: (cuerpo: { contrasenaActual: string; contrasenaNueva: string }) => Response = () => new Response(null, { status: 204 })) {
  let actual = inicial;
  const fetchMock = vi.fn(async (url: string, init: RequestInit) => {
    if (url.endsWith('/auth/refresh')) return json(200, actual);
    if (url.endsWith('/auth/logout')) return new Response(null, { status: 204 });
    if (url.endsWith('/cuenta/contrasena')) {
      const respuesta = cambio(JSON.parse(String(init.body)));
      if (respuesta.status === 204) actual = sesion(false);
      return respuesta;
    }
    return json(404, { status: 404, detail: 'No encontramos lo que buscás.' });
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function montar(ruta: string) {
  const router = createMemoryRouter(rutas, { initialEntries: [ruta] });
  render(
    <SesionProvider>
      <RouterProvider router={router} />
    </SesionProvider>,
  );
  return router;
}

const escribir = async (campo: string, valor: string) => userEvent.type(screen.getByLabelText(campo), valor);
const actualizar = () => userEvent.click(screen.getByRole('button', { name: 'Actualizar contraseña' }));

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-03 · cambio obligatorio en el primer ingreso', () => {
  it('con la contraseña inicial, cualquier pantalla lleva a Mi cuenta y avisa por qué', async () => {
    backend(sesion(true));
    const router = montar('/agenda');

    expect(await screen.findByText('Elegí tu contraseña')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/mi-cuenta');
    await act(() => router.navigate('/mas'));
    expect(router.state.location.pathname).toBe('/mi-cuenta');
  });

  it('ya con contraseña propia, navega sin obstáculos y Mi cuenta no avisa nada', async () => {
    backend(sesion(false));
    const router = montar('/mas');

    expect(await screen.findByRole('heading', { name: 'Más' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/mas');
    await act(() => router.navigate('/mi-cuenta'));
    expect(await screen.findByRole('heading', { name: 'Mi cuenta' })).toBeInTheDocument();
    expect(screen.queryByText('Elegí tu contraseña')).not.toBeInTheDocument();
  });
});

describe('UI-03 · cambiar la contraseña', () => {
  it('al cambiarla, avisa, libera la navegación y manda las dos contraseñas', async () => {
    const fetchMock = backend(sesion(true));
    const router = montar('/mi-cuenta');
    await screen.findByText('Elegí tu contraseña');

    await escribir('Contraseña actual', 'la-inicial-123');
    await escribir('Contraseña nueva', 'una-clave-mejor');
    await escribir('Repetí la contraseña nueva', 'una-clave-mejor');
    await actualizar();

    expect(await screen.findByText('Contraseña actualizada')).toBeInTheDocument();
    expect(screen.getAllByRole('navigation').length).toBeGreaterThan(0);
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => url.includes('/notificaciones'))).toBe(true));
    const llamada = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/cuenta/contrasena'));
    expect(JSON.parse(String(llamada?.[1].body))).toEqual({ contrasenaActual: 'la-inicial-123', contrasenaNueva: 'una-clave-mejor' });
    expect(screen.queryByText('Elegí tu contraseña')).not.toBeInTheDocument();
    await act(() => router.navigate('/mas'));
    expect(router.state.location.pathname).toBe('/mas');
  });

  it('valida antes de enviar: faltantes, largo y repetición', async () => {
    const fetchMock = backend(sesion(false));
    montar('/mi-cuenta');
    await screen.findByRole('heading', { name: 'Mi cuenta' });

    await actualizar();
    expect(screen.getByText('Escribí tu contraseña actual.')).toBeInTheDocument();
    expect(screen.getByText('Escribí la contraseña nueva.')).toBeInTheDocument();

    await escribir('Contraseña actual', 'la-inicial-123');
    await escribir('Contraseña nueva', 'corta');
    await escribir('Repetí la contraseña nueva', 'otra');
    await actualizar();
    expect(screen.getByText('La contraseña nueva tiene que tener entre 8 y 72 caracteres.')).toBeInTheDocument();
    expect(screen.getByText('Las dos contraseñas nuevas tienen que ser iguales.')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/cuenta/contrasena'))).toBe(false);
  });

  it('no deja una nueva igual a la actual', async () => {
    backend(sesion(false));
    montar('/mi-cuenta');
    await screen.findByRole('heading', { name: 'Mi cuenta' });

    await escribir('Contraseña actual', 'la-misma-clave');
    await escribir('Contraseña nueva', 'la-misma-clave');
    await escribir('Repetí la contraseña nueva', 'la-misma-clave');
    await actualizar();

    expect(screen.getByText('La contraseña nueva tiene que ser distinta de la actual. Elegí otra.')).toBeInTheDocument();
  });

  it('si la actual no es correcta, lo dice en ese campo y no libera la navegación', async () => {
    backend(sesion(true), () =>
      json(422, { status: 422, codigo: 'CONTRASENA_ACTUAL_INCORRECTA', detail: 'La contraseña actual no es correcta. Volvé a escribirla.' }),
    );
    const router = montar('/mi-cuenta');
    await screen.findByText('Elegí tu contraseña');

    await escribir('Contraseña actual', 'me-equivoque');
    await escribir('Contraseña nueva', 'una-clave-mejor');
    await escribir('Repetí la contraseña nueva', 'una-clave-mejor');
    await actualizar();

    expect(await screen.findByText('La contraseña actual no es correcta. Volvé a escribirla.')).toBeInTheDocument();
    expect(screen.getByText('Elegí tu contraseña')).toBeInTheDocument();
    await act(() => router.navigate('/mas'));
    expect(router.state.location.pathname).toBe('/mi-cuenta');
  });
});

const completarCambio = async () => {
  await escribir('Contraseña actual', 'la-inicial-123');
  await escribir('Contraseña nueva', 'una-clave-mejor');
  await escribir('Repetí la contraseña nueva', 'una-clave-mejor');
};

describe('UI-03 · recuperación de la sesión', () => {
  it.each(['red', 'servidor'] as const)('explica el cambio guardado ante fallo de %s y permite reintentar sin reenviarlo', async (fallo) => {
    const fetchMock = backend(sesion(true));
    const responder = fetchMock.getMockImplementation()!;
    let fallar = false;
    fetchMock.mockImplementation(async (url, init) => {
      if (url.endsWith('/auth/refresh') && fallar) {
        if (fallo === 'red') throw new TypeError('Failed to fetch');
        return json(500, { status: 500, detail: 'Error interno' });
      }
      return responder(url, init);
    });
    const router = montar('/mi-cuenta');
    await screen.findByText('Elegí tu contraseña');
    fallar = true;
    await completarCambio();
    await actualizar();

    expect(await screen.findByText('Contraseña guardada; sesión sin actualizar')).toBeInTheDocument();
    expect(screen.queryByText('Contraseña actualizada')).not.toBeInTheDocument();
    expect(screen.getByText('Elegí tu contraseña')).toBeInTheDocument();
    expect(screen.getByLabelText('Contraseña actual')).toHaveValue('');
    expect(router.state.location.pathname).toBe('/mi-cuenta');

    fallar = false;
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar actualizar sesión' }));
    expect(await screen.findByText('Contraseña actualizada')).toBeInTheDocument();
    expect(screen.queryByText('Elegí tu contraseña')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/cuenta/contrasena'))).toHaveLength(1);
    await act(() => router.navigate('/mas'));
    expect(router.state.location.pathname).toBe('/mas');
  });

  it('con refresco vencido permite reingresar con la contraseña nueva y libera la navegación', async () => {
    const fetchMock = backend(sesion(true));
    const responder = fetchMock.getMockImplementation()!;
    let vencido = false;
    fetchMock.mockImplementation(async (url, init) => {
      if (url.endsWith('/auth/refresh') && vencido) return json(401, { status: 401, codigo: 'SESION_VENCIDA' });
      if (url.endsWith('/auth/login')) return json(200, sesion(false));
      return responder(url, init);
    });
    const router = montar('/mi-cuenta');
    await screen.findByText('Elegí tu contraseña');
    vencido = true;
    await completarCambio();
    await actualizar();
    expect(await screen.findByRole('dialog', { name: 'Tu sesión expiró' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Volver a ingresar' }));
    await escribir('Contraseña', 'una-clave-mejor');
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByText('Contraseña guardada; sesión sin actualizar')).not.toBeInTheDocument();
    expect(screen.queryByText('Elegí tu contraseña')).not.toBeInTheDocument();
    const login = fetchMock.mock.calls.find(([url]) => url.endsWith('/auth/login'));
    expect(JSON.parse(String(login?.[1].body)).contrasena).toBe('una-clave-mejor');
    await act(() => router.navigate('/mas'));
    expect(router.state.location.pathname).toBe('/mas');
  });

  it('al recargar después del cambio recupera el estado nuevo desde la cookie', async () => {
    const fetchMock = backend(sesion(true));
    const responder = fetchMock.getMockImplementation()!;
    let fallar = false;
    fetchMock.mockImplementation(async (url, init) => {
      if (url.endsWith('/auth/refresh') && fallar) throw new TypeError('Failed to fetch');
      return responder(url, init);
    });
    const vista = render(<SesionProvider><RouterProvider router={createMemoryRouter(rutas, { initialEntries: ['/mi-cuenta'] })} /></SesionProvider>);
    await screen.findByText('Elegí tu contraseña');
    fallar = true;
    await completarCambio();
    await actualizar();
    await screen.findByText('Contraseña guardada; sesión sin actualizar');
    vista.unmount();
    _reiniciarCliente();
    fallar = false;
    const router = montar('/mas');
    expect(await screen.findByRole('heading', { name: 'Más' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/mas');
  });

  it('mantiene el botón ocupado y evita doble envío mientras espera la API', async () => {
    const fetchMock = backend(sesion(false));
    const responder = fetchMock.getMockImplementation()!;
    let resolver!: (respuesta: Response) => void;
    fetchMock.mockImplementation(async (url, init) => url.endsWith('/cuenta/contrasena')
      ? new Promise<Response>((resolve) => { resolver = resolve; }) : responder(url, init));
    montar('/mi-cuenta');
    await screen.findByRole('heading', { name: 'Mi cuenta' });
    await completarCambio();
    await actualizar();
    const boton = screen.getByRole('button', { name: 'Actualizar contraseña' });
    expect(boton).toBeDisabled();
    expect(boton).toHaveAttribute('aria-busy', 'true');
    await userEvent.click(boton);
    expect(fetchMock.mock.calls.filter(([url]) => url.endsWith('/cuenta/contrasena'))).toHaveLength(1);
    await act(async () => resolver(new Response(null, { status: 204 })));
    expect(await screen.findByText('Contraseña actualizada')).toBeInTheDocument();
    expect(boton).toBeEnabled();
  });

  it('muestra un error de API sin anunciar un cambio guardado', async () => {
    backend(sesion(false), () => json(500, { status: 500, detail: 'Volvé a intentarlo.' }));
    montar('/mi-cuenta');
    await screen.findByRole('heading', { name: 'Mi cuenta' });
    await completarCambio();
    await actualizar();
    expect(await screen.findByText('No pudimos cambiar la contraseña')).toBeInTheDocument();
    expect(screen.queryByText('Contraseña actualizada')).not.toBeInTheDocument();
  });
});

describe('UI-03 · preferencias y cierre de sesión', () => {
  it('aplica claro, oscuro y sistema y conserva la selección al volver', async () => {
    backend(sesion(false));
    const router = montar('/mi-cuenta');
    await screen.findByRole('heading', { name: 'Mi cuenta' });
    await userEvent.click(screen.getByRole('tab', { name: 'Oscuro' }));
    expect(localStorage.getItem('vastio.tema')).toBe('oscuro');
    expect(document.documentElement.dataset.theme).toBe('dark');
    await userEvent.click(screen.getByRole('tab', { name: 'Claro' }));
    expect(document.documentElement.dataset.theme).toBe('light');
    await userEvent.click(screen.getByRole('tab', { name: 'Como el dispositivo' }));
    expect(localStorage.getItem('vastio.tema')).toBe('sistema');
    await act(() => router.navigate('/mas'));
    await act(() => router.navigate('/mi-cuenta'));
    expect(screen.getByRole('tab', { name: 'Como el dispositivo' })).toHaveAttribute('aria-selected', 'true');
  });

  it('cierra sesión desde Mi cuenta y lleva al ingreso', async () => {
    const fetchMock = backend(sesion(true));
    const router = montar('/mi-cuenta');
    await screen.findByText('Elegí tu contraseña');
    await userEvent.click(within(screen.getByRole('heading', { name: 'Mi cuenta' }).closest('section')!).getByRole('button', { name: 'Cerrar sesión' }));
    expect(await screen.findByRole('button', { name: 'Ingresar' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/auth/logout'))).toBe(true);
  });
});

describe('UI-03 · límites y renovación compartida', () => {
  it('valida el límite UTF-8 de BCrypt antes de enviar', async () => {
    const fetchMock = backend(sesion(false));
    montar('/mi-cuenta');
    await screen.findByRole('heading', { name: 'Mi cuenta' });
    await escribir('Contraseña actual', 'la-inicial-123');
    await escribir('Contraseña nueva', 'á'.repeat(37));
    await escribir('Repetí la contraseña nueva', 'á'.repeat(37));
    await actualizar();
    expect(screen.getByText('La contraseña nueva es demasiado larga. Usá menos caracteres.')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => url.endsWith('/cuenta/contrasena'))).toBe(false);
  });

  it('si la renovación todavía trae la marca inicial, ofrece reintentar sin anunciar sesión actualizada', async () => {
    const fetchMock = backend(sesion(true));
    const responder = fetchMock.getMockImplementation()!;
    let anterior = true;
    fetchMock.mockImplementation(async (url, init) => url.endsWith('/auth/refresh') && anterior
      ? json(200, sesion(true)) : responder(url, init));
    montar('/mi-cuenta');
    await screen.findByText('Elegí tu contraseña');
    await completarCambio();
    await actualizar();
    expect(await screen.findByText('Contraseña guardada; sesión sin actualizar')).toBeInTheDocument();
    expect(screen.queryByText('Contraseña actualizada')).not.toBeInTheDocument();
    anterior = false;
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar actualizar sesión' }));
    expect(await screen.findByText('Contraseña actualizada')).toBeInTheDocument();
    expect(screen.queryByText('Elegí tu contraseña')).not.toBeInTheDocument();
  });
});

describe('UI-03 · marco restringido', () => {
  it('no monta menús comerciales ni campana, pero conserva el cierre de sesión', async () => {
    backend(sesion(true));
    montar('/mi-cuenta');
    await screen.findByText('Elegí tu contraseña');
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Notificaciones/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeEnabled();
  });

  it('no pide notificaciones ni instala su polling mientras está restringido', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    try {
      const fetchMock = backend(sesion(true));
      montar('/mi-cuenta');
      await screen.findByText('Elegí tu contraseña');
      await act(async () => { vi.advanceTimersByTime(125_000); });
      expect(fetchMock.mock.calls.filter(([url]) => url.includes('/notificaciones'))).toHaveLength(0);
    } finally { vi.useRealTimers(); }
  });
});
