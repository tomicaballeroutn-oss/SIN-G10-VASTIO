import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, api, iniciarSesion, suscribirSesion, type EventoSesion, type Sesion } from './cliente';
import { ProblemaApi } from './problema';

const sesion = (token: string): Sesion => ({
  tokenAcceso: token,
  tokenAccesoVence: '2026-09-28T20:00:00Z',
  minutosExpiracionSesion: 60,
  minutosAvisoExpiracion: 5,
  usuario: { id: 1, nombreCompleto: 'Lucía Ferreyra', nombreUsuario: 'lucia', roles: ['VENDEDORA'], debeCambiarContrasena: false },
});

const json = (status: number, cuerpo: unknown) =>
  new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } });

const problema = (status: number, codigo: string, detail: string, extra: Record<string, unknown> = {}) =>
  json(status, { status, title: 'Error', detail, codigo, ...extra });

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  _reiniciarCliente();
  fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function authorizationDe(llamada: number): string | undefined {
  const init = fetchMock.mock.calls[llamada][1] as RequestInit;
  return (init.headers as Record<string, string>).Authorization;
}

describe('cliente de la API', () => {
  it('después del login manda el token de acceso en cada llamada', async () => {
    fetchMock.mockResolvedValueOnce(json(200, sesion('t1'))).mockResolvedValueOnce(json(200, { ok: true }));

    await iniciarSesion('lucia', 'clave');
    await api('/eventos');

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/eventos');
    expect(authorizationDe(1)).toBe('Bearer t1');
  });

  it('si el token venció, renueva la sesión y reintenta una vez', async () => {
    fetchMock
      .mockResolvedValueOnce(json(200, sesion('viejo')))
      .mockResolvedValueOnce(problema(401, 'SESION_VENCIDA', 'Tu sesión expiró.'))
      .mockResolvedValueOnce(json(200, sesion('nuevo')))
      .mockResolvedValueOnce(json(200, { ok: true }));

    await iniciarSesion('lucia', 'clave');
    const resultado = await api<{ ok: boolean }>('/eventos');

    expect(resultado).toEqual({ ok: true });
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/auth/refresh');
    expect(authorizationDe(3)).toBe('Bearer nuevo');
  });

  it('varias llamadas vencidas a la vez comparten una sola renovación', async () => {
    fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
      if (url.endsWith('/auth/refresh')) return json(200, sesion('nuevo'));
      const auth = (init.headers as Record<string, string>).Authorization;
      return auth === 'Bearer nuevo' ? json(200, { url }) : problema(401, 'SESION_VENCIDA', 'Tu sesión expiró.');
    });

    await Promise.all([api('/a'), api('/b'), api('/c')]);

    const renovaciones = fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/auth/refresh'));
    expect(renovaciones).toHaveLength(1);
  });

  it('si la renovación falla, avisa que la sesión venció y rechaza con el problema', async () => {
    const eventos: EventoSesion[] = [];
    suscribirSesion((e) => eventos.push(e));
    fetchMock
      .mockResolvedValueOnce(problema(401, 'SIN_SESION', 'Ingresá con tu usuario para continuar.'))
      .mockResolvedValueOnce(problema(401, 'SESION_VENCIDA', 'Tu sesión expiró. Volvé a ingresar para seguir donde estabas.'));

    const error = await api('/eventos').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ProblemaApi);
    expect((error as ProblemaApi).codigo).toBe('SESION_VENCIDA');
    expect(eventos).toContainEqual({ tipo: 'vencida' });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('un login incorrecto no intenta renovar', async () => {
    fetchMock.mockResolvedValueOnce(problema(401, 'CREDENCIALES_INVALIDAS', 'Usuario o contraseña incorrectos. Volvé a intentarlo.'));

    const error = (await iniciarSesion('lucia', 'mal').catch((e: unknown) => e)) as ProblemaApi;

    expect(error.codigo).toBe('CREDENCIALES_INVALIDAS');
    expect(error.message).toBe('Usuario o contraseña incorrectos. Volvé a intentarlo.');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('expone los errores de validación por campo', async () => {
    fetchMock.mockResolvedValueOnce(
      problema(400, 'DATOS_INVALIDOS', 'Revisá los datos marcados.', { errores: [{ campo: 'nombreUsuario', mensaje: 'Escribí tu usuario.' }] }),
    );

    const error = (await api('/auth/login', { metodo: 'POST', cuerpo: {} }).catch((e: unknown) => e)) as ProblemaApi;

    expect(error.status).toBe(400);
    expect(error.errorDe('nombreUsuario')).toBe('Escribí tu usuario.');
  });

  it('sin conexión devuelve un mensaje legible', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));

    const error = (await api('/eventos').catch((e: unknown) => e)) as ProblemaApi;

    expect(error.codigo).toBe('SIN_CONEXION');
    expect(error.message).toMatch(/Revisá la conexión/);
  });
});
