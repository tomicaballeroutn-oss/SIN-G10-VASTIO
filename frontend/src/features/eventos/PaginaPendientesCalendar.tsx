import { useNavigate } from 'react-router';
import { calendar, etiquetaCalendar, type EstadoCalendar } from '../../api/calendar';
import { useDatos } from '../../api/useDatos';
import { Alert, Button, Card, EmptyState, Table } from '../../ds';
import { Encabezado } from '../layout/paginas';
import { fechaCorta } from '../comun/formato';

export function PaginaPendientesCalendar() {
  const datos = useDatos(calendar.pendientes);
  const navegar = useNavigate();
  return <section className="pantalla">
    <Encabezado titulo="Pendientes de Google Calendar" antetitulo="Agenda" />
    <p>Los pendientes se mantienen hasta confirmar la operación. Leer una notificación no los resuelve.</p>
    <Button variant="outline" onClick={datos.recargar} loading={datos.cargando}>Actualizar pendientes</Button>
    {datos.error && <Alert tone="danger">{datos.error.message}</Alert>}
    {datos.cargando && <p role="status">Consultando pendientes…</p>}
    {datos.datos && (datos.datos.length === 0
      ? <Card><EmptyState icon="calendar" title="No hay pendientes de Google Calendar">Las publicaciones registradas no necesitan atención.</EmptyState></Card>
      : <Card flush><Table<EstadoCalendar> caption="Pendientes de Google Calendar" rows={datos.datos.map(e => ({ ...e, id: e.eventoId }))} columns={[
        { key: 'codigo', header: 'Código' },
        { key: 'salon', header: 'Salón' },
        { key: 'fecha', header: 'Fecha', render: e => fechaCorta(e.fecha, true) },
        { key: 'turno', header: 'Turno' },
        { key: 'estado', header: 'Publicación', render: etiquetaCalendar },
        { key: 'pendiente', header: 'Acción pendiente', render: e => ({ EXPORTAR: 'Exportar', ACTUALIZAR: 'Actualizar', RETIRAR: 'Retirar', NINGUNA: 'Revisar resultado' })[e.pendiente] },
        { key: 'abrir', header: 'Evento', render: e => <Button variant="text" onClick={() => navegar(`/eventos/${e.eventoId}`)}>Abrir {e.codigo}</Button> },
      ]} /></Card>)}
  </section>;
}
