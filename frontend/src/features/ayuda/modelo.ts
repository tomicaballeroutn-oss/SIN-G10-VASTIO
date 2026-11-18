import type { Rol } from '../../api/cliente';
import { itemsPara } from '../layout/menu';

export interface SeccionAyuda {
  titulo: string;
  pasos?: readonly string[];
  notas?: readonly string[];
  roles?: readonly Rol[];
}

export interface ContenidoAyuda {
  descripcion: string;
  secciones: readonly SeccionAyuda[];
}

export interface ArticuloAyuda {
  id: string;
  categoria: string;
  titulo: string;
  /** Id de una pantalla del menú existente. Sin pantalla, es ayuda compartida. */
  pantalla?: string;
  roles?: readonly Rol[];
  /** Archivo de video HTTPS (mp4, webm u ogv), configurado cuando esté disponible. */
  videoUrl?: string;
}

export function puedeLeer(articulo: ArticuloAyuda, roles: readonly Rol[]): boolean {
  const pantallaPermitida = !articulo.pantalla || itemsPara(roles).some((item) => item.id === articulo.pantalla);
  return pantallaPermitida && (!articulo.roles || articulo.roles.some((rol) => roles.includes(rol)));
}

export function rutaArticulo(articulo: ArticuloAyuda): string {
  return `/ayuda/${articulo.categoria}/${articulo.id}`;
}
