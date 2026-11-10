import { useEffect, useState } from 'react';
import { IconButton } from '../../ds';

/** A partir de cuánto se bajó aparece el botón (más o menos una pantalla y media). */
const DESDE_PX = 600;

/** Botón fijo para volver arriba en las pantallas largas (lista de eventos, servicios, reportes). */
export function BotonSubir() {
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    const revisar = () => setVisible(window.scrollY > DESDE_PX);
    revisar();
    window.addEventListener('scroll', revisar, { passive: true });
    return () => window.removeEventListener('scroll', revisar);
  }, []);
  if (!visible) return null;
  const subir = () => {
    const sinMovimiento = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    window.scrollTo({ top: 0, behavior: sinMovimiento ? 'auto' : 'smooth' });
  };
  return (
    <div className="boton-subir">
      <IconButton icon="chevron-up" label="Subir" variant="solid" onClick={subir} />
    </div>
  );
}
