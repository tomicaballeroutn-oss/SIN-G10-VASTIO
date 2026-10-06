import { Dialog } from '../../ds';
import { ListaNotificaciones } from './ListaNotificaciones';

/** UI-22 · Panel que abre la campana del encabezado. Se cierra con el botón propio del diálogo o con Escape. */
export function PanelNotificaciones({ alCerrar }: { alCerrar: () => void }) {
  return (
    <Dialog open title="Notificaciones" onClose={alCerrar}>
      <ListaNotificaciones alIrAlEvento={alCerrar} />
    </Dialog>
  );
}
