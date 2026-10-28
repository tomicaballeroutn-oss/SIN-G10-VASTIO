import { useCallback, useRef } from 'react';
import { calendar, etiquetaCalendar, type EstadoCalendar } from '../../api/calendar';
import { useDatos, useEnvio } from '../../api/useDatos';
import { Alert, Button, Card } from '../../ds';
import './calendar.css';

/** Se monta solo para perfiles autorizados. Cada cambio de ficha vuelve a consultar el backend. */
export function EstadoGoogleCalendar({ eventoId, version }: { eventoId: number; version: number }) {
  const cargar = useCallback(() => { void version; return calendar.estado(eventoId); }, [eventoId, version]);
  const datos = useDatos(cargar);
  const { guardando, error, enviar } = useEnvio();
  const enVuelo = useRef(false);

  async function operar(operacion: EstadoCalendar['pendiente']) {
    if (enVuelo.current || operacion === 'NINGUNA') return;
    enVuelo.current = true;
    try {
      const resultado = await enviar(() => calendar.operar(eventoId, operacion));
      if (resultado) datos.fijar(() => resultado);
      else datos.recargar(); // Un timeout no prueba que Google no haya escrito.
    } finally { enVuelo.current = false; }
  }

  const e = datos.datos;
  const vigente = !!e && e.estado === 'EXPORTADO' && e.pendiente === 'NINGUNA' && !e.error;
  const accion = e?.pendiente ?? 'NINGUNA';
  const puede = e && (accion === 'RETIRAR' ? e.puedeRetirar : e.puedeExportar);
  return (
    <Card title="Google Calendar">
      {datos.cargando && <p role="status">Consultando publicación…</p>}
      {datos.error && <Alert tone="danger">{datos.error.message}<Button variant="text" onClick={datos.recargar}>Volver a consultar</Button></Alert>}
      {e && <div className="calendar-evento" aria-busy={guardando}>
        <p role="status" className={vigente ? 'calendar-evento__vigente' : undefined}>
          {vigente ? '✓ ' : ''}{etiquetaCalendar(e)}
        </p>
        {!e.configurado && <Alert tone="warning">La integración está desactivada o no está configurada. El pendiente sigue registrado en Vastio.</Alert>}
        {e.error && <Alert tone="warning">No confirmamos la operación ({e.error}). Volvé a verificar el resultado antes de intentar otra acción.</Alert>}
        {error && <Alert tone="danger">{error.message}</Alert>}
        {puede && accion !== 'NINGUNA' && <Button
          tone={accion === 'EXPORTAR' ? 'danger' : undefined}
          variant={accion === 'EXPORTAR' ? 'solid' : 'outline'} icon="calendar"
          loading={guardando} disabled={!e.configurado || datos.cargando}
          onClick={() => void operar(accion)}>
          {accion === 'EXPORTAR' ? 'Exportar a Google Calendar' : accion === 'ACTUALIZAR' ? 'Actualizar en Google Calendar' : 'Retirar de Google Calendar'}
        </Button>}
        {(e.estado === 'EN_PROCESO' || e.estado === 'INCIERTO' || e.error) && accion === 'NINGUNA' &&
          <Button variant="outline" disabled={!e.configurado} loading={guardando} onClick={() => void operar(e.puedeRetirar && !e.puedeExportar ? 'RETIRAR' : 'ACTUALIZAR')}>Verificar y reintentar</Button>}
        {e.puedeRetirar && accion !== 'RETIRAR' && !guardando &&
          <Button variant="text" tone="danger" disabled={!e.configurado || datos.cargando} onClick={() => void operar('RETIRAR')}>Retirar de Google Calendar</Button>}
        <p className="body-sm v-muted">Las publicaciones, actualizaciones y retiradas se realizan solo cuando las solicitás.</p>
      </div>}
    </Card>
  );
}
