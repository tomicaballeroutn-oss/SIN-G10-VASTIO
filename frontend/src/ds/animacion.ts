import { useEffect, useRef, useState } from 'react';

/** Duración de la animación de la marca (los WebM y GIF de public/brand/animaciones). */
export const DURACION_ANIMACION_MS = 3600;
/** Las cargas más cortas que esto no muestran la animación: verla un instante molesta más de lo que ayuda. */
export const ESPERA_ANTES_DE_ANIMAR_MS = 400;

/**
 * Se anima solo si el navegador puede decir que no se pidió reducir el movimiento. Sin matchMedia (entorno de pruebas,
 * navegadores viejos) no se anima: la pantalla nunca queda esperando a un video.
 */
export function puedeAnimar(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false;
  return !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Espera con animación: si `cargando` dura más de ESPERA_ANTES_DE_ANIMAR_MS, se muestra la animación y, una vez que
 * apareció, se ve completa aunque los datos lleguen antes. Devuelve si hay que mostrarla y qué hacer al fin de cada vuelta.
 */
export function useEsperaAnimada(cargando: boolean) {
  const [animando, setAnimando] = useState(false);
  const cargandoAhora = useRef(cargando);
  useEffect(() => { cargandoAhora.current = cargando; });

  useEffect(() => {
    if (!cargando || animando || !puedeAnimar()) return undefined;
    const id = window.setTimeout(() => setAnimando(true), ESPERA_ANTES_DE_ANIMAR_MS);
    return () => window.clearTimeout(id);
  }, [cargando, animando]);

  // Al terminar cada vuelta: si ya llegó lo que se esperaba, se suelta; si no, sigue otra vuelta.
  const alTerminarVuelta = () => { if (!cargandoAhora.current) setAnimando(false); };
  return { animando, alTerminarVuelta };
}
