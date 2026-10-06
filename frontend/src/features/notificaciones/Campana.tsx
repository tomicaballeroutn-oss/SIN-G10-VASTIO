import { useCallback, useEffect, useState } from 'react';
import { INTERVALO_CONTADOR_MS, notificaciones, suscribirSinLeer } from '../../api/notificaciones';
import { IconButton } from '../../ds';
import { PanelNotificaciones } from './PanelNotificaciones';
import './notificaciones.css';

/**
 * UI-22 · Campana del encabezado con el contador de avisos sin leer. El contador se actualiza cada minuto, al cerrar el
 * panel y cuando la lista lo cambia (abrir un aviso, marcar todas). Si no hay conexión, queda el último número: no
 * interrumpe a nadie con un error.
 */
export function Campana() {
  const [sinLeer, setSinLeer] = useState(0);
  const [abierto, setAbierto] = useState(false);

  const actualizar = useCallback(() => {
    notificaciones.sinLeer().then((r) => setSinLeer(r.cantidad), () => undefined);
  }, []);

  useEffect(() => {
    actualizar();
    const intervalo = setInterval(actualizar, INTERVALO_CONTADOR_MS);
    const dejar = suscribirSinLeer(setSinLeer);
    return () => {
      clearInterval(intervalo);
      dejar();
    };
  }, [actualizar]);

  const etiqueta = sinLeer > 0 ? `Notificaciones, ${sinLeer} sin leer` : 'Notificaciones';

  return (
    <>
      <IconButton icon="bell" label={etiqueta} variant="text" count={sinLeer} onClick={() => setAbierto(true)} />
      {abierto && (
        <PanelNotificaciones
          alCerrar={() => {
            setAbierto(false);
            actualizar();
          }}
        />
      )}
    </>
  );
}
