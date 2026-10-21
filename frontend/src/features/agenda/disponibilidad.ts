/**
 * Ayuda del botón de cada salón en el detalle del día (UI-07 y UI-18): cuántos turnos le quedan libres.
 * En un día pasado no hay nada que ofrecer: no se cuenta.
 */
export function disponibilidad(activo: boolean, libres: number, pasado: boolean): string | undefined {
  if (!activo) return 'Dado de baja';
  if (pasado) return undefined;
  if (libres === 0) return 'Sin lugar';
  return libres === 1 ? '1 libre' : `${libres} libres`;
}
