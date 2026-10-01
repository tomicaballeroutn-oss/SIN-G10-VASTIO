import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, vencerSesion, type Rol, type Sesion } from '../../api/cliente';
import { rutas } from '../../rutas';
import { SesionProvider } from './SesionProvider';

const sesionDe = (roles: Rol[], nombre = 'Lucía Ferreyra'): Sesion => ({
  tokenAcceso: 'token',
  tokenAccesoVence: '2026-09-28T20:00:00Z',
  minutosExpiracionSesion: 60,
  minutosAvisoExpiracion: 5,
  usuario: { id: 1, nombreCompleto: nombre, nombreUsuario: 'lucia.ferreyra', roles, debeCambiarContrasena: false },
});

const json = (status: number, cuerpo: unknown) => new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } });
const sinSesion = () => json(401, { status: 401, codigo: 'SESION_VENCIDA', detail: 'Tu sesión expiró.' });

/** Backend falso: `cookie` es la sesión que devuelve el refresh (null = no hay cookie). */
function backend(cookie: Sesion | null, login: (usuario: string, clave: string) => Sesion | null = () => null) {
  const fetchMock = vi.fn(async (url: string, init: RequestInit) => {
    if (url.endsWith('/auth/refresh')) return cookie ? json(200, cookie) : sinSesion();
    if (url.endsWith('/auth/logout')) return new Response(null, { status: 204 });
    if (url.endsWith('/auth/login')) {
      const { nombreUsuario, contrasena } = JSON.parse(String(init.body));
      const sesion = login(nombreUsuario, contrasena);
      return sesion
        ? json(200, sesion)
        : json(401, { status: 401, codigo: 'CREDENCIALES_INVALIDAS', detail: 'Usuario o contraseña incorrectos. Volvé a intentarlo.' });
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

const menuLateral = () => screen.getAllByRole('navigation', { name: 'Navegación principal' })[0];

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('UI-01 · inicio de sesión', () => {
  it('sin sesión, una pantalla protegida lleva al login', async () => {
    backend(null);
    const router = montar('/agenda');

    expect(await screen.findByRole('heading', { name: 'Ingresá a tu cuenta' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
    expect(router.state.location.search).toBe('?volver=%2Fagenda');
  });

  it('con usuario o contraseña incorrectos avisa sin decir cuál de los dos', async () => {
    backend(null);
    const usuario = userEvent.setup();
    montar('/login');

    await usuario.type(await screen.findByLabelText('Usuario'), 'lucia.ferreyra');
    await usuario.type(screen.getByLabelText('Contraseña'), 'otra');
    await usuario.click(screen.getByRole('button', { name: 'Ingresar' }));

    const alerta = await screen.findByRole('alert');
    expect(alerta).toHaveTextContent('Usuario o contraseña incorrectos');
    expect(alerta).toHaveTextContent('Volvé a intentarlo.');
    expect(screen.getByLabelText('Contraseña')).toHaveValue('');
  });

  it('marca los campos vacíos sin llamar al backend', async () => {
    const fetchMock = backend(null);
    const usuario = userEvent.setup();
    montar('/login');

    await usuario.click(await screen.findByRole('button', { name: 'Ingresar' }));

    expect(screen.getByLabelText('Usuario')).toHaveAccessibleDescription('Escribí tu usuario.');
    expect(screen.getByLabelText('Contraseña')).toHaveAccessibleDescription('Escribí tu contraseña.');
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/auth/login'))).toHaveLength(0);
  });

  it('con datos correctos entra a la pantalla de inicio de su perfil', async () => {
    backend(null, (u, c) => (u === 'lucia.ferreyra' && c === 'clave' ? sesionDe(['BARRA'], 'Lucía Ferreyra') : null));
    const usuario = userEvent.setup();
    const router = montar('/login');

    await usuario.type(await screen.findByLabelText('Usuario'), 'lucia.ferreyra');
    await usuario.type(screen.getByLabelText('Contraseña'), 'clave');
    await usuario.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Barra' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/barra');
  });

  it('después del login vuelve a la pantalla que había pedido', async () => {
    backend(null, () => sesionDe(['ADMINISTRACION']));
    const usuario = userEvent.setup();
    const router = montar('/reportes');

    await usuario.type(await screen.findByLabelText('Usuario'), 'gabi');
    await usuario.type(screen.getByLabelText('Contraseña'), 'clave');
    await usuario.click(screen.getByRole('button', { name: 'Ingresar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Reportes' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/reportes');
  });
});

describe('layout · menú según perfil', () => {
  it('la vendedora empieza en la agenda y ve solo sus ítems', async () => {
    backend(sesionDe(['VENDEDORA']));
    const router = montar('/');

    expect(await screen.findByRole('heading', { level: 1, name: 'Agenda' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/agenda');
    const menu = menuLateral();
    expect(within(menu).getByRole('button', { name: 'Mis eventos' })).toBeInTheDocument();
    expect(within(menu).queryByRole('button', { name: 'Usuarios y perfiles' })).not.toBeInTheDocument();
    expect(within(menu).queryByText('Bebidas', { selector: '.v-nav__title' })).not.toBeInTheDocument();
  });

  it('Dirección ve los tres grupos, incluida la administración de usuarios', async () => {
    backend(sesionDe(['DIRECCION']));
    montar('/');

    await screen.findByRole('heading', { level: 1, name: 'Inicio' });
    const menu = menuLateral();
    for (const grupo of ['Agenda', 'Bebidas', 'Gestión']) expect(within(menu).getByText(grupo, { selector: '.v-nav__title' })).toBeInTheDocument();
    expect(within(menu).getByRole('button', { name: 'Usuarios y perfiles' })).toBeInTheDocument();
  });

  it('una pantalla fuera del perfil avisa que no tiene acceso', async () => {
    backend(sesionDe(['COCINA']));
    montar('/usuarios');

    expect(await screen.findByText('Tu perfil no tiene acceso a esta pantalla')).toBeInTheDocument();
  });

  it('cerrar sesión vuelve al login', async () => {
    backend(sesionDe(['COMPRAS']));
    const usuario = userEvent.setup();
    const router = montar('/');

    await usuario.click(await screen.findByRole('button', { name: 'Cerrar sesión' }));

    expect(await screen.findByRole('heading', { name: 'Ingresá a tu cuenta' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/login');
  });
});

describe('UI-02 · sesión vencida', () => {
  it('al vencer muestra el aviso sobre la misma pantalla y deja volver a ingresar ahí', async () => {
    backend(sesionDe(['ADMINISTRACION']), () => sesionDe(['ADMINISTRACION']));
    const usuario = userEvent.setup();
    const router = montar('/reportes');
    await screen.findByRole('heading', { level: 1, name: 'Reportes' });

    await act(() => vencerSesion());

    const dialogo = await screen.findByRole('dialog', { name: 'Tu sesión expiró' });
    expect(dialogo).toHaveTextContent('cerramos tu sesión después de un tiempo sin actividad');
    expect(screen.getByRole('heading', { level: 1, name: 'Reportes' })).toBeInTheDocument();

    await usuario.click(within(dialogo).getByRole('button', { name: 'Volver a ingresar' }));
    expect(within(dialogo).getByLabelText('Usuario')).toHaveValue('lucia.ferreyra');
    await usuario.type(within(dialogo).getByLabelText('Contraseña'), 'clave');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Ingresar' }));

    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(router.state.location.pathname).toBe('/reportes');
  });

  it('avisa antes de vencer y, sin actividad, cierra la sesión', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    const fetchMock = backend(sesionDe(['COMPRAS']));
    montar('/');
    await screen.findByRole('heading', { level: 1, name: 'Inicio' });

    act(() => vi.advanceTimersByTime(55 * 60_000));
    expect(await screen.findByText('Tu sesión va a expirar pronto')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Seguir trabajando' })).toBeInTheDocument();

    act(() => vi.advanceTimersByTime(5 * 60_000));
    expect(await screen.findByRole('dialog', { name: 'Tu sesión expiró' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/auth/logout'))).toBe(true);
  });

  it('«Seguir trabajando» renueva la sesión y saca el aviso', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    const fetchMock = backend(sesionDe(['COMPRAS']));
    montar('/');
    await screen.findByRole('heading', { level: 1, name: 'Inicio' });
    act(() => vi.advanceTimersByTime(56 * 60_000));
    const renovacionesAntes = fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/auth/refresh')).length;

    await act(async () => screen.getByRole('button', { name: 'Seguir trabajando' }).click());

    await vi.waitFor(() => expect(screen.queryByText('Tu sesión va a expirar pronto')).not.toBeInTheDocument());
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/auth/refresh')).length).toBe(renovacionesAntes + 1);
  });
});
