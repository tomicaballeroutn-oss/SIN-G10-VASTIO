import { useCallback } from 'react';
import { consultarConsumo } from '../../api/consumos';
import { useDatos } from '../../api/useDatos';
import { Alert, Button, Card, Table, cantidadLegible } from '../../ds';
import { Cargando } from '../comun/Cargando';

const NUMERO = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 4 });
const botellas = (n: number | null) => n === null ? 'No disponible' : `${NUMERO.format(n)} ${n === 1 ? 'botella' : 'botellas'}`;

/** B4-05: consulta de solo lectura del consumo congelado de todas las barras del evento. */
export function PestanaConsumo({ eventoId }: { eventoId: number }) {
  const cargar = useCallback(() => consultarConsumo(eventoId), [eventoId]);
  const datos = useDatos(cargar);

  if (datos.error?.codigo === 'EVENTO_NO_CERRADO') {
    return (
      <Alert tone="info" title="Consumo pendiente de cierre"
        action={<Button variant="outline" size="sm" onClick={datos.recargar}>Actualizar consumo</Button>}>
        El consumo consolidado estará disponible cuando se cierre el evento.
      </Alert>
    );
  }

  return (
    <Cargando datos={datos}>
      {(consumo) => (
        <div className="pantalla">
          <Alert tone="info" title="Consumo consolidado al cierre">
            Incluye todas las barras del evento. Las cantidades están expresadas en botellas; la tabla también muestra cajas.
          </Alert>
          {consumo.consumoTotal === 0 && <Alert tone="info" title="Evento cerrado sin consumo">El consumo total registrado es cero.</Alert>}
          <Card title="Consumo por bebida" subtitle="Cantidades registradas al cierre, sin recalcular desde el stock actual">
            <Table
              caption="Consumo por bebida"
              dense
              rows={consumo.renglones.map((r) => ({ ...r, id: r.bebidaId }))}
              empty="No hay registros de consumo para este evento cerrado."
              columns={[
                { key: 'bebida', header: 'Bebida', render: (r) => `${r.nombre} ${r.presentacion}` },
                { key: 'entregado', header: 'Entregado', numeric: true, render: (r) => cantidadLegible(r.entregado, r.unidadesPorBulto, r.unidad) },
                { key: 'devuelto', header: 'Devuelto', numeric: true, render: (r) => cantidadLegible(r.devuelto, r.unidadesPorBulto, r.unidad) },
                { key: 'remanente', header: 'Remanente', numeric: true, render: (r) => cantidadLegible(r.remanente, r.unidadesPorBulto, r.unidad) },
                { key: 'consumo', header: 'Consumido', numeric: true, render: (r) => cantidadLegible(r.consumo, r.unidadesPorBulto, r.unidad) },
                { key: 'porInvitado', header: 'Por invitado', numeric: true, render: (r) => botellas(r.porInvitado) },
              ]}
            />
          </Card>
          <Card title="Consumo total">
            <p className="numeral">{botellas(consumo.consumoTotal)}</p>
          </Card>
          <Card title="Consumo por invitado">
            <p className="numeral">{botellas(consumo.porInvitadoTotal)}</p>
            <p className="body-sm">
              {consumo.invitados !== null && consumo.invitados > 0
                ? `Calculado sobre ${consumo.invitados} invitados, no sobre asistencia real.`
                : 'La cantidad de invitados es desconocida o cero: no se puede calcular el consumo por invitado.'}
            </p>
            {consumo.usaSegmentos && <p className="body-sm v-muted">Este evento usa segmentos; el denominador disponible sigue siendo la cantidad de invitados.</p>}
          </Card>
        </div>
      )}
    </Cargando>
  );
}
