import { useState } from 'react';
import { IconButton, aplicarTema } from '../../ds';

/** Cambio de tema a mano, en la barra de arriba de todas las pantallas (antes solo estaba en Mi cuenta). */
export function BotonTema() {
  const [oscuro, setOscuro] = useState(() => document.documentElement.dataset.theme === 'dark');
  return (
    <IconButton
      icon={oscuro ? 'sun' : 'moon'}
      label={oscuro ? 'Tema claro' : 'Tema oscuro'}
      variant="text"
      onClick={() => {
        aplicarTema(oscuro ? 'claro' : 'oscuro');
        setOscuro(!oscuro);
      }}
    />
  );
}
