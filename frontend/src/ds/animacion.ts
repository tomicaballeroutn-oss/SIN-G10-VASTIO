/** Duración de la animación de la marca (los WebM y GIF de public/brand/animaciones). */
export const DURACION_ANIMACION_MS = 3600;

/**
 * Se anima solo si el navegador puede decir que no se pidió reducir el movimiento. Sin matchMedia (entorno de pruebas,
 * navegadores viejos) no se anima: la pantalla nunca queda esperando a un video.
 */
export function puedeAnimar(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false;
  return !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}
