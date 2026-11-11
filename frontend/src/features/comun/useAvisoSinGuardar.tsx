import { useEffect, useRef } from 'react';
import { useBlocker } from 'react-router';
import { Button, Dialog } from '../../ds';

/**
 * Si hay cambios sin guardar, frena la salida de la pantalla (otra pestaña de la ficha, volver, el menú) y pregunta;
 * al recargar o cerrar, el navegador muestra su propio aviso. `liberar()` lo apaga antes de navegar después de guardar.
 */
export function useAvisoSinGuardar(hayCambios: boolean) {
  const activo = useRef(hayCambios);
  useEffect(() => { activo.current = hayCambios; }, [hayCambios]);

  const bloqueo = useBlocker(({ currentLocation, nextLocation }) =>
    activo.current && currentLocation.pathname + currentLocation.search !== nextLocation.pathname + nextLocation.search);

  useEffect(() => {
    if (!hayCambios) return undefined;
    const alSalir = (e: BeforeUnloadEvent) => { e.preventDefault(); };
    window.addEventListener('beforeunload', alSalir);
    return () => window.removeEventListener('beforeunload', alSalir);
  }, [hayCambios]);

  const aviso = bloqueo.state === 'blocked' ? (
    <Dialog
      open
      title="Tenés cambios sin guardar"
      onClose={() => bloqueo.reset()}
      actions={<>
        <Button variant="outline" onClick={() => bloqueo.proceed()}>Salir sin guardar</Button>
        <Button onClick={() => bloqueo.reset()}>Seguir editando</Button>
      </>}
    >
      Si salís ahora, se pierde lo que cambiaste.
    </Dialog>
  ) : null;

  return { aviso, liberar: () => { activo.current = false; } };
}
