import { useEffect, useLayoutEffect, useRef, type RefObject } from 'react';

/** Controles, diálogos y menú: tocarlos hace lo suyo y no cuenta como tocar afuera. */
const NO_ES_AFUERA = 'button, a, input, select, textarea, label, [role="dialog"], [role="listbox"], [role="menu"], nav';

/**
 * Llama a `alTocarAfuera` cuando se toca un espacio vacío fuera de las zonas dadas (por ejemplo, el calendario y el
 * detalle del día), mientras `activo` sea verdadero.
 */
export function useTocarAfuera(zonas: RefObject<HTMLElement | null>[], activo: boolean, alTocarAfuera: () => void) {
  const ultimo = useRef({ zonas, alTocarAfuera });
  useLayoutEffect(() => {
    ultimo.current = { zonas, alTocarAfuera };
  });

  useEffect(() => {
    if (!activo) return;
    function alTocar(evento: PointerEvent) {
      const objetivo = evento.target;
      if (!(objetivo instanceof Element) || !objetivo.isConnected) return;
      if (objetivo.closest(NO_ES_AFUERA)) return;
      if (ultimo.current.zonas.some((zona) => zona.current?.contains(objetivo))) return;
      ultimo.current.alTocarAfuera();
    }
    document.addEventListener('pointerdown', alTocar);
    return () => document.removeEventListener('pointerdown', alTocar);
  }, [activo]);
}
