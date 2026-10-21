import { useState } from 'react';
import { AgendaLeyenda, Card } from '../../ds';

/** Ancho desde el que la agenda pone el detalle del día al costado del mes (ver agenda.css). */
const DOS_COLUMNAS = '(min-width: 1200px)';

/**
 * Qué significa cada punto de la agenda, en una ficha debajo del detalle del día.
 * Arranca abierto cuando hay lugar al costado del mes y cerrado en el teléfono, donde va debajo de todo.
 */
export function MacheteAgenda() {
  const [abierto] = useState(() => typeof window !== 'undefined' && !!window.matchMedia?.(DOS_COLUMNAS).matches);
  return (
    <Card className="agenda__machete">
      <AgendaLeyenda defaultOpen={abierto} />
    </Card>
  );
}
