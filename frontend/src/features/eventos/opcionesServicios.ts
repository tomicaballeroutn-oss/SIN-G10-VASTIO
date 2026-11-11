/**
 * Opciones de ejemplo para cargar servicios más rápido (demo: no son la carta real del complejo). Se guardan igual que
 * antes, como renglones de texto, así que la cocina, la ficha impresa y el backend no cambian. Van por nombre de
 * categoría: si una categoría se renombra en Parámetros, vuelve a texto libre.
 *
 * - `sugerencias`: muchas opciones o se combinan con cantidades (Menú): se escribe y sugiere, y se puede escribir otra.
 * - `una`: se elige una sola (Bodega, Tipo de barra): botones.
 * - `varias`: pocas y se marcan varias, cada una con su cantidad (Menús especiales).
 */
export interface OpcionServicio {
  nombre: string;
  /** Qué incluye, en una línea. */
  detalle?: string;
}

export type ModoServicio = 'sugerencias' | 'una' | 'varias';

export interface OpcionesDeCategoria {
  modo: ModoServicio;
  opciones: OpcionServicio[];
}

const OPCIONES: Record<string, OpcionesDeCategoria> = {
  menu: {
    modo: 'sugerencias',
    opciones: [
      { nombre: 'Menú Clásico', detalle: 'Recepción, plato principal y postre' },
      { nombre: 'Menú Tradición', detalle: 'Parrilla criolla con guarniciones y postre casero' },
      { nombre: 'Menú Estación', detalle: 'De temporada, con productos de la región' },
      { nombre: 'Menú del Valle', detalle: 'Cordero, verduras asadas y vinos de altura' },
      { nombre: 'Menú de Autor', detalle: 'Pasos del chef, con maridaje' },
      { nombre: 'Menú Infantil', detalle: 'Para menores de 12 años' },
    ],
  },
  'menus especiales': {
    modo: 'varias',
    opciones: [
      { nombre: 'Vegetariano' },
      { nombre: 'Vegano' },
      { nombre: 'Sin TACC' },
      { nombre: 'Sin lactosa' },
      { nombre: 'Kosher' },
      { nombre: 'Bajo en sodio' },
      { nombre: 'Diabético' },
    ],
  },
  bodega: {
    modo: 'una',
    opciones: [
      { nombre: 'Selección' },
      { nombre: 'Reserva' },
      { nombre: 'Gran Reserva' },
      { nombre: 'Bodega de Autor' },
    ],
  },
  'tipo de barra': {
    modo: 'una',
    opciones: [
      { nombre: 'Sin alcohol' },
      { nombre: 'Barra clásica' },
      { nombre: 'Barra de gin' },
      { nombre: 'Coctelería de autor' },
    ],
  },
};

/** Sin tildes ni mayúsculas, para comparar nombres de categoría y lo que se escribe. */
export function normalizarTexto(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
}

export function opcionesDe(categoria: string): OpcionesDeCategoria | null {
  return OPCIONES[normalizarTexto(categoria)] ?? null;
}

/** Las opciones que contienen lo escrito (todas si no hay texto). */
export function sugerencias(opciones: OpcionServicio[], texto: string): OpcionServicio[] {
  const buscado = normalizarTexto(texto);
  return opciones.filter((o) => normalizarTexto(`${o.nombre} ${o.detalle ?? ''}`).includes(buscado));
}
