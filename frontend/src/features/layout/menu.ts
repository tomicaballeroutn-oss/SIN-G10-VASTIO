import type { Rol } from '../../api/cliente';
import type { NavItem } from '../../ds';

/**
 * Menú por perfil. Sale de la matriz de docs/perfiles-y-permisos.md y de la arquitectura de información
 * de design/design-system/guidelines/20-ux-y-arquitectura.md (grupos Agenda, Bebidas y Gestión).
 * Un usuario con varios perfiles ve la unión. Esto solo decide qué se dibuja: la autorización real está en el backend.
 */
export interface ItemDeMenu extends NavItem {
  ruta: string;
  roles: readonly Rol[];
}

const TOTAL: readonly Rol[] = ['DIRECCION', 'COORDINACION'];
const TODOS: readonly Rol[] = ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'VENDEDORA', 'PLANNER', 'COMPRAS', 'BARRA', 'COCINA'];

export const MENU: readonly ItemDeMenu[] = [
  { id: 'inicio', label: 'Inicio', icon: 'house', ruta: '/inicio', roles: [...TOTAL, 'ADMINISTRACION', 'COMPRAS'] },
  { id: 'notificaciones', label: 'Notificaciones', icon: 'bell', ruta: '/notificaciones', roles: TODOS },

  { id: 'agenda', label: 'Agenda', icon: 'calendar', group: 'Agenda', ruta: '/agenda', roles: [...TOTAL, 'ADMINISTRACION', 'VENDEDORA', 'PLANNER', 'COMPRAS'] },
  { id: 'eventos', label: 'Eventos', icon: 'clipboard-list', group: 'Agenda', ruta: '/eventos', roles: [...TOTAL, 'ADMINISTRACION', 'VENDEDORA', 'PLANNER', 'COMPRAS'] },
  { id: 'bloqueos', label: 'Bloqueos', icon: 'lock', group: 'Agenda', ruta: '/bloqueos', roles: [...TOTAL, 'ADMINISTRACION'] },
  { id: 'cocina', label: 'Vista de cocina', icon: 'utensils-crossed', group: 'Agenda', ruta: '/cocina', roles: [...TOTAL, 'PLANNER', 'COMPRAS', 'COCINA'] },

  { id: 'barra', label: 'Barra', icon: 'martini', group: 'Bebidas', ruta: '/barra', roles: [...TOTAL, 'COMPRAS', 'BARRA'] },
  { id: 'existencias', label: 'Existencias', icon: 'package', group: 'Bebidas', ruta: '/existencias', roles: [...TOTAL, 'ADMINISTRACION', 'COMPRAS', 'BARRA'] },
  { id: 'movimientos', label: 'Movimientos', icon: 'history', group: 'Bebidas', ruta: '/movimientos', roles: [...TOTAL, 'ADMINISTRACION', 'COMPRAS'] },
  { id: 'ordenes', label: 'Órdenes de preparación', icon: 'file-text', group: 'Bebidas', ruta: '/ordenes', roles: [...TOTAL, 'ADMINISTRACION', 'COMPRAS'] },
  { id: 'catalogo', label: 'Catálogo', icon: 'wine', group: 'Bebidas', ruta: '/catalogo', roles: [...TOTAL, 'ADMINISTRACION', 'COMPRAS'] },

  { id: 'reportes', label: 'Reportes', icon: 'chart-column', group: 'Gestión', ruta: '/reportes', roles: [...TOTAL, 'ADMINISTRACION', 'COMPRAS'] },
  { id: 'usuarios', label: 'Usuarios y perfiles', icon: 'users', group: 'Gestión', ruta: '/usuarios', roles: TOTAL },
  { id: 'parametros', label: 'Parámetros', icon: 'settings', group: 'Gestión', ruta: '/parametros', roles: [...TOTAL, 'ADMINISTRACION'] },
];

/** Perfiles que ven la lista completa de eventos. Para el resto (la vendedora) «Eventos» es «Mis eventos». */
const VEN_TODOS_LOS_EVENTOS: readonly Rol[] = [...TOTAL, 'ADMINISTRACION', 'PLANNER', 'COMPRAS'];

export function itemsPara(roles: readonly Rol[]): ItemDeMenu[] {
  const soloLosSuyos = !roles.some((r) => VEN_TODOS_LOS_EVENTOS.includes(r));
  return MENU.filter((it) => it.roles.some((r) => roles.includes(r))).map((it) =>
    it.id === 'eventos' && soloLosSuyos ? { ...it, label: 'Mis eventos' } : it,
  );
}

/** Máximo de ítems de la navegación inferior (teléfono y tableta). */
const MAXIMO_INFERIOR = 5;
/** Prioridad para la navegación inferior: primero lo que se usa de pie y en el salón. */
const PRIORIDAD_INFERIOR = ['agenda', 'eventos', 'barra', 'existencias', 'movimientos', 'cocina', 'inicio', 'notificaciones'];

export const ITEM_MAS: ItemDeMenu = { id: 'mas', label: 'Más', icon: 'menu', ruta: '/mas', roles: TODOS };

/** Hasta cinco ítems; si no entran, cuatro y «Más» con el resto. */
export function itemsInferiores(items: ItemDeMenu[]): ItemDeMenu[] {
  if (items.length <= MAXIMO_INFERIOR) return items;
  const orden = (it: ItemDeMenu) => {
    const i = PRIORIDAD_INFERIOR.indexOf(it.id);
    return i < 0 ? PRIORIDAD_INFERIOR.length : i;
  };
  const principales = [...items].sort((a, b) => orden(a) - orden(b)).slice(0, MAXIMO_INFERIOR - 1);
  return [...principales, ITEM_MAS];
}

/**
 * Pantalla de inicio según el perfil: la agenda es el inicio de comercial, Barra el de la encargada de barra.
 * Dirección, Coordinación, Administración y Compras tienen su propio inicio (UI-04).
 */
export function rutaDeInicio(roles: readonly Rol[]): string {
  if (roles.some((r) => (['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'] as Rol[]).includes(r))) return '/inicio';
  if (roles.includes('VENDEDORA')) return '/agenda';
  if (roles.includes('PLANNER')) return '/eventos';
  if (roles.includes('BARRA')) return '/barra';
  if (roles.includes('COCINA')) return '/cocina';
  return '/mi-cuenta';
}
