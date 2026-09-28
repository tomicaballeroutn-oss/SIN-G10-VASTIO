import { ProblemaApi } from './problema';

/**
 * Cliente HTTP de la API.
 *
 * - El token de acceso vive solo en memoria (nunca en localStorage). El de refresco es una cookie HttpOnly
 *   que el navegador manda solo a /api/v1/auth.
 * - Si una llamada responde 401, se renueva la sesión una vez y se reintenta. Si varias llamadas fallan a la vez,
 *   comparten una única renovación.
 * - Si la renovación falla, se avisa a los suscriptores con `vencida` (la pantalla muestra UI-02).
 */

const BASE = '/api/v1';

export interface UsuarioSesion {
  id: number;
  nombreCompleto: string;
  nombreUsuario: string;
  roles: Rol[];
  debeCambiarContrasena: boolean;
}

export type Rol = 'DIRECCION' | 'COORDINACION' | 'ADMINISTRACION' | 'VENDEDORA' | 'PLANNER' | 'COMPRAS' | 'BARRA' | 'COCINA';

export interface Sesion {
  tokenAcceso: string;
  tokenAccesoVence: string;
  minutosExpiracionSesion: number;
  minutosAvisoExpiracion: number;
  usuario: UsuarioSesion;
}

export type EventoSesion = { tipo: 'iniciada'; sesion: Sesion } | { tipo: 'renovada'; sesion: Sesion } | { tipo: 'vencida' } | { tipo: 'cerrada' };

let tokenAcceso: string | null = null;
let renovacionEnCurso: Promise<Sesion> | null = null;
const suscriptores = new Set<(evento: EventoSesion) => void>();

export function suscribirSesion(fn: (evento: EventoSesion) => void): () => void {
  suscriptores.add(fn);
  return () => {
    suscriptores.delete(fn);
  };
}

function avisar(evento: EventoSesion) {
  suscriptores.forEach((fn) => fn(evento));
}

export interface Opciones {
  metodo?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  cuerpo?: unknown;
  /** false en el reintento, para no entrar en bucle. */
  renovarSiVence?: boolean;
}

export async function api<T>(ruta: string, opciones: Opciones = {}): Promise<T> {
  const { metodo = 'GET', cuerpo, renovarSiVence = true } = opciones;
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' };
  if (cuerpo !== undefined) headers['Content-Type'] = 'application/json';
  if (tokenAcceso) headers.Authorization = `Bearer ${tokenAcceso}`;

  let respuesta: Response;
  try {
    respuesta = await fetch(BASE + ruta, {
      method: metodo,
      headers,
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      credentials: 'same-origin',
    });
  } catch {
    throw ProblemaApi.sinConexion();
  }

  if (respuesta.status === 401 && renovarSiVence && !ruta.startsWith('/auth/')) {
    await renovarSesion();
    return api<T>(ruta, { ...opciones, renovarSiVence: false });
  }
  if (!respuesta.ok) {
    throw await ProblemaApi.desde(respuesta);
  }
  if (respuesta.status === 204) {
    return undefined as T;
  }
  return (await respuesta.json()) as T;
}

/** Renueva con la cookie de refresco. Si falla, deja la sesión como vencida y relanza el error. */
export function renovarSesion(): Promise<Sesion> {
  if (!renovacionEnCurso) {
    renovacionEnCurso = api<Sesion>('/auth/refresh', { metodo: 'POST' })
      .then((sesion) => {
        tokenAcceso = sesion.tokenAcceso;
        avisar({ tipo: 'renovada', sesion });
        return sesion;
      })
      .catch((error: unknown) => {
        if (error instanceof ProblemaApi && error.status === 401) {
          tokenAcceso = null;
          avisar({ tipo: 'vencida' });
        }
        throw error;
      })
      .finally(() => {
        renovacionEnCurso = null;
      });
  }
  return renovacionEnCurso;
}

export async function iniciarSesion(nombreUsuario: string, contrasena: string): Promise<Sesion> {
  const sesion = await api<Sesion>('/auth/login', { metodo: 'POST', cuerpo: { nombreUsuario, contrasena } });
  tokenAcceso = sesion.tokenAcceso;
  avisar({ tipo: 'iniciada', sesion });
  return sesion;
}

export async function cerrarSesion(): Promise<void> {
  tokenAcceso = null;
  try {
    await api<void>('/auth/logout', { metodo: 'POST' });
  } finally {
    avisar({ tipo: 'cerrada' });
  }
}

/**
 * Cierre por inactividad (UI-02): borra la cookie en el servidor pero avisa `vencida`, no `cerrada`,
 * para que la pantalla quede montada y la persona vuelva a ingresar sin perder lo que estaba cargando.
 */
export async function vencerSesion(): Promise<void> {
  tokenAcceso = null;
  try {
    await api<void>('/auth/logout', { metodo: 'POST' });
  } catch {
    // si no hay conexión, la cookie vence sola
  } finally {
    avisar({ tipo: 'vencida' });
  }
}

/** Solo para tests. */
export function _reiniciarCliente() {
  tokenAcceso = null;
  renovacionEnCurso = null;
  suscriptores.clear();
}
