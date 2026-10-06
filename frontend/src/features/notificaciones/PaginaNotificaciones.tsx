import { Card } from '../../ds';
import { Encabezado } from '../layout/paginas';
import { ListaNotificaciones } from './ListaNotificaciones';

/** UI-22 · Notificaciones como pantalla, desde el menú (en el celular, la barra inferior). */
export function PaginaNotificaciones() {
  return (
    <section className="pantalla">
      <Encabezado titulo="Notificaciones" />
      <Card>
        <ListaNotificaciones />
      </Card>
    </section>
  );
}
