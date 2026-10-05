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
  const respuesta = await pedir(ruta, opciones, 'application/json, application/problem+json');
  if (respuesta.status === 204) {
    return undefined as T;
  }
  return (await respuesta.json()) as T;
}

/** Un archivo descargado con la sesión (p. ej. el contrato del legajo), con el nombre que manda el servidor. */
export async function descargarArchivo(ruta: string): Promise<{ contenido: Blob; nombre: string | null }> {
  const respuesta = await pedir(ruta, {}, '*/*');
  return { contenido: await respuesta.blob(), nombre: nombreDeArchivo(respuesta.headers.get('Content-Disposition')) };
}

/** `filename*=UTF-8''…` (RFC 5987) o, si no viene, `filename="…"`. */
export function nombreDeArchivo(disposicion: string | null): string | null {
  if (!disposicion) return null;
  const extendido = /filename\*=UTF-8''([^;]+)/i.exec(disposicion);
  if (extendido) return decodeURIComponent(extendido[1]);
  return /filename="([^"]+)"/i.exec(disposicion)?.[1] ?? null;
}

/**
 * Hace el pedido con el token, renueva la sesión una vez si responde 401 y convierte los errores en ProblemaApi.
 * Un cuerpo FormData (archivos) viaja tal cual; el resto, como JSON.
 */
async function pedir(ruta: string, opciones: Opciones, aceptar: string): Promise<Response> {
  const { metodo = 'GET', cuerpo, renovarSiVence = true } = opciones;
  const formulario = cuerpo instanceof FormData;
  const headers: Record<string, string> = { Accept: aceptar };
  if (cuerpo !== undefined && !formulario) headers['Content-Type'] = 'application/json';
  if (tokenAcceso) headers.Authorization = `Bearer ${tokenAcceso}`;

  let respuesta: Response;
  try {
    respuesta = await fetch(BASE + ruta, {
      method: metodo,
      headers,
      body: cuerpo === undefined ? undefined : formulario ? cuerpo : JSON.stringify(cuerpo),
      credentials: 'same-origin',
    });
  } catch {
    throw ProblemaApi.sinConexion();
  }

  if (respuesta.status === 401 && renovarSiVence && !ruta.startsWith('/auth/')) {
    await renovarSesion();
    return pedir(ruta, { ...opciones, renovarSiVence: false }, aceptar);
  }
  if (!respuesta.ok) {
    throw await ProblemaApi.desde(respuesta);
  }
  return respuesta;
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
