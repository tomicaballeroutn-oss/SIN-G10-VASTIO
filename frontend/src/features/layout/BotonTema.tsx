import { useSyncExternalStore } from 'react';
import { IconButton, aplicarTema } from '../../ds';
import { suscribirTema, temaOscuroActivo } from '../../ds/tema';

/** Cambio manual de tema, sincronizado con Mi cuenta y la preferencia del dispositivo. */
export function BotonTema() {
  const oscuro = useSyncExternalStore(suscribirTema, temaOscuroActivo);
  return (
    <IconButton
      icon={oscuro ? 'sun' : 'moon'}
      label={oscuro ? 'Tema claro' : 'Tema oscuro'}
      variant="text"
      onClick={() => aplicarTema(oscuro ? 'claro' : 'oscuro')}
    />
  );
}
