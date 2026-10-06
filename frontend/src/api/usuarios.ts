import { api, type Rol } from './cliente';

/** Perfiles en el orden de la matriz de permisos, con el nombre de la tabla `rol` (V2). */
export const ROLES: readonly Rol[] = ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'VENDEDORA', 'PLANNER', 'COMPRAS', 'BARRA', 'COCINA'];

export const NOMBRE_DE_ROL: Record<Rol, string> = {
  DIRECCION: 'Dirección',
  COORDINACION: 'Coordinación comercial',
  ADMINISTRACION: 'Administración',
  VENDEDORA: 'Vendedora',
  PLANNER: 'Planner',
  COMPRAS: 'Encargado de compras',
  BARRA: 'Encargada de barra',
  COCINA: 'Cocina',
};

export interface Usuario {
  id: number;
  nombreCompleto: string;
  nombreUsuario: string;
  roles: Rol[];
  email: string | null;
  telefono: string | null;
  activo: boolean;
  debeCambiarContrasena: boolean;
  fechaAlta: string;
  fechaBaja: string | null;
}

/** Para elegir a alguien (p. ej. la vendedora de un evento). */
export interface Persona {
  id: number;
  nombreCompleto: string;
}

export interface AltaDeUsuario {
  nombreCompleto: string;
  nombreUsuario: string;
  roles: Rol[];
  email: string;
  telefono: string;
  contrasenaInicial: string;
}

/** Modificar: nombre, perfiles y contacto. El nombre de usuario no se edita. */
export type ModificacionDeUsuario = Pick<AltaDeUsuario, 'nombreCompleto' | 'roles' | 'email' | 'telefono'>;

export const usuarios = {
  listar: () => api<Usuario[]>('/usuarios'),
  personas: (perfil: Rol) => api<Persona[]>(`/usuarios/personas?perfil=${perfil}`),
  crear: (datos: AltaDeUsuario) => api<Usuario>('/usuarios', { metodo: 'POST', cuerpo: datos }),
  modificar: (id: number, datos: ModificacionDeUsuario) => api<Usuario>(`/usuarios/${id}`, { metodo: 'PUT', cuerpo: datos }),
  darDeBaja: (id: number) => api<Usuario>(`/usuarios/${id}/baja`, { metodo: 'POST' }),
  reactivar: (id: number) => api<Usuario>(`/usuarios/${id}/reactivacion`, { metodo: 'POST' }),
};
