import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { vi } from 'vitest';
import type { Rol, Sesion } from '../api/cliente';
import { SesionProvider } from '../features/sesion/SesionProvider';
import { rutas } from '../rutas';

/** Sesión de prueba con esos perfiles. */
export function sesionDe(roles: Rol[], nombreCompleto = 'Lucía Ferreyra', id = 1): Sesion {
  return {
    tokenAcceso: 'token',
    tokenAccesoVence: '2099-01-01T00:00:00Z',
    minutosExpiracionSesion: 60,
    minutosAvisoExpiracion: 5,
    usuario: { id, nombreCompleto, nombreUsuario: nombreCompleto.toLowerCase().replace(/\s+/g, '.'), roles, debeCambiarContrasena: false },
  };
}

export function json(status: number, cuerpo: unknown): Response {
  return new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } });
}

/** Respuesta de error como la del backend (ProblemDetail). */
export function problema(status: number, codigo: string, detail: string, errores: { campo: string; mensaje: string }[] = []): Response {
  return json(status, { status, codigo, title: 'Error', detail, errores });
}

export interface Pedido {
  metodo: string;
  ruta: string;
  cuerpo: unknown;
}

type Manejador = (pedido: Pedido) => Response | undefined | Promise<Response | undefined>;

/**
 * Reemplaza fetch: la sesión sale del refresh y el resto de las rutas (sin /api/v1) las responde `manejador`.
 * Lo que no responde, da 404. Devuelve el mock para revisar los pedidos.
 */
export function backendFalso(sesion: Sesion | null, manejador: Manejador = () => undefined) {
  const fetchMock = vi.fn(async (url: string, init: RequestInit = {}) => {
    const ruta = url.replace(/^\/api\/v1/, '');
    const metodo = init.method ?? 'GET';
    if (ruta === '/auth/refresh') {
      return sesion ? json(200, sesion) : problema(401, 'SESION_VENCIDA', 'Tu sesión expiró.');
    }
    if (ruta === '/auth/logout') return new Response(null, { status: 204 });
    const cuerpo = typeof init.body === 'string' ? JSON.parse(init.body) : undefined;
    return (await manejador({ metodo, ruta, cuerpo })) ?? problema(404, 'HTTP_404', 'No encontramos lo que buscás.');
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

/** Pedidos hechos (sin los de sesión), para verificar qué se mandó. */
export function pedidos(fetchMock: ReturnType<typeof backendFalso>): Pedido[] {
  return fetchMock.mock.calls
    .map(([url, init = {}]) => ({
      ruta: String(url).replace(/^\/api\/v1/, ''),
      metodo: init.method ?? 'GET',
      cuerpo: typeof init.body === 'string' ? JSON.parse(init.body) : undefined,
    }))
    .filter((p) => !p.ruta.startsWith('/auth/'));
}

/** La app completa (sesión + rutas) en esa dirección. */
export function montar(ruta: string) {
  const router = createMemoryRouter(rutas, { initialEntries: [ruta] });
  render(
    <SesionProvider>
      <RouterProvider router={router} />
    </SesionProvider>,
  );
  return router;
}
