import { act, render, screen } from '@testing-library/react';
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
