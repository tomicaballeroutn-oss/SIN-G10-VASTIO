import { useEffect, useState } from 'react';
import { AnimacionMarca, puedeAnimar } from '../../ds';

const CLAVE = 'vastio.bienvenida';

/** Una vez por pestaña: abrir el link o una pestaña nueva la muestra; recargar la misma pestaña, no. */
function corresponde(): boolean {
  if (!puedeAnimar()) return false;
  try {
    return sessionStorage.getItem(CLAVE) === null;
  } catch {
    return false;
  }
}

/**
 * La marca animada al entrar a Vastio, sobre la app: la sesión y la primera pantalla cargan por debajo, así que no suma
 * espera. Se va al terminar la vuelta, o antes tocando la pantalla.
 */
export function Bienvenida() {
  const [visible, setVisible] = useState(corresponde);
  const [saliendo, setSaliendo] = useState(false);

  useEffect(() => {
    if (!visible) return;
    try { sessionStorage.setItem(CLAVE, '1'); } catch { /* sin almacenamiento: se verá de nuevo, no pasa nada */ }
  }, [visible]);

  useEffect(() => {
    if (!saliendo) return undefined;
    const id = window.setTimeout(() => setVisible(false), 250);
    return () => window.clearTimeout(id);
  }, [saliendo]);

  if (!visible) return null;
  return (
    <div className={`bienvenida${saliendo ? ' is-saliendo' : ''}`} role="status" aria-label="Bienvenida a Vastio" onClick={() => setSaliendo(true)}>
      <AnimacionMarca tamano={160} alTerminar={() => setSaliendo(true)} />
    </div>
  );
}
