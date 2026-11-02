import { useNavigate } from 'react-router';
import { ordenes, ESTADO_ORDEN, type OrdenDeLaLista } from '../../api/ordenes';
import { useDatos } from '../../api/useDatos';
import { Badge, Card, EmptyState, SalonTag, Table, type SalonId } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta } from '../comun/formato';
import './ordenes.css';

/**
 * Órdenes de preparación (docs/sprint-4.md, decisión 21): los eventos Confirmados y En curso, del más próximo al más
 * lejano, con el estado de su orden. Cada fila abre la orden en la ficha del evento.
 */
export function PaginaOrdenes() {
  const datos = useDatos(ordenes.listado);
  const navegar = useNavigate();
  return (
    <section className="pantalla">
      <div className="pantalla__titulos">
        <h1>Órdenes de preparación</h1>
        <p className="body v-muted">La bebida sugerida para cada evento confirmado. Tocá un evento para ver y ajustar su orden.</p>
      </div>
      <Cargando datos={datos}>
        {(lista) => lista.length === 0 ? (
          <Card>
            <EmptyState icon="clipboard-list" title="Sin eventos confirmados">
              Cuando se confirme un evento, su orden de preparación aparece acá.
            </EmptyState>
          </Card>
        ) : (
          <Card flush>
            <Table<OrdenDeLaLista & { id: number }>
              caption="Eventos confirmados y su orden de preparación"
              rows={lista.map((o) => ({ ...o, id: o.evento.id }))}
              onRowClick={(o) => navegar(`/eventos/${o.evento.id}?pestana=orden`)}
              columns={[
                { key: 'fecha', header: 'Fecha', render: (o) => fechaCorta(o.evento.fecha) },
                { key: 'evento', header: 'Evento', render: (o) => o.evento.nombre },
                { key: 'salon', header: 'Salón', render: (o) => <SalonTag salon={o.evento.salon.codigo as SalonId} label={o.evento.salon.nombre} variant="dot" /> },
                { key: 'asistentes', header: 'Asistentes', numeric: true, render: (o) => o.evento.asistentes ?? '—' },
                { key: 'estado', header: 'Orden', render: (o) => <EstadoDeLaOrden orden={o} /> },
              ]}
            />
          </Card>
        )}
      </Cargando>
    </section>
  );
}

function EstadoDeLaOrden({ orden }: { orden: OrdenDeLaLista }) {
  if (!orden.estado) return <Badge tone="neutral">Sin orden</Badge>;
  const estado = ESTADO_ORDEN[orden.estado];
  return (
    <span className="orden__estado">
      <Badge tone={estado.tone}>{estado.label}</Badge>
      {orden.asistentesCambiaron && <Badge tone="warning" icon="triangle-alert">Cambiaron los asistentes</Badge>}
    </span>
  );
}
