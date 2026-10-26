import { useCallback, useState } from 'react';
import { ordenes, ESTADO_ORDEN, type Orden, type OrdenDelEvento, type RenglonOrden } from '../../api/ordenes';
import { useDatos, useEnvio } from '../../api/useDatos';
import { Actor, Alert, Badge, Button, Card, Dialog, EmptyState, Table, cantidadLegible } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta, fechaHora } from '../comun/formato';
import './ordenes.css';

/**
 * UI-35 · Orden de preparación del evento (docs/sprint-4.md, decisiones 11 a 22): lo sugerido por el consumo histórico,
 * lo que se va a retirar y lo comprometido por otros eventos próximos, en cajas y botellas. Se genera sola al confirmar
 * el evento; Compras, Coordinación y Dirección la generan a mano o la recalculan.
 */
export function PestanaOrden({ eventoId, tipoEvento }: { eventoId: number; tipoEvento: string }) {
  const cargar = useCallback(() => ordenes.consultar(eventoId), [eventoId]);
  const datos = useDatos(cargar);
  return (
    <Cargando datos={datos}>
      {(consulta) => <ContenidoOrden consulta={consulta} tipoEvento={tipoEvento} alCambiar={(nueva) => datos.fijar(() => nueva)} />}
    </Cargando>
  );
}

/** «Sugerida según 12 eventos cerrados de Quince». */
function origenDeLaSugerencia(orden: Orden, tipoEvento: string): string {
  switch (orden.fuente) {
    case 'HISTORICO_TIPO':
      return `Sugerida según el consumo de ${orden.eventosBase} ${orden.eventosBase === 1 ? 'evento cerrado' : 'eventos cerrados'} de ${tipoEvento}.`;
    case 'PROPORCIONES':
      return `Sugerida según las proporciones de consumo de ${tipoEvento}: todavía hay pocos eventos cerrados del tipo.`;
    case 'HISTORICO_GENERAL':
      return `Sugerida según el consumo de ${orden.eventosBase} ${orden.eventosBase === 1 ? 'evento cerrado' : 'eventos cerrados'} de todos los tipos: no hay suficientes de ${tipoEvento} ni proporciones cargadas.`;
    case 'SIN_DATOS':
      return 'Todavía no hay consumo histórico ni proporciones cargadas: la orden sale vacía y las bebidas se cargan a mano.';
  }
}

function legible(r: RenglonOrden, botellas: number): string {
  return cantidadLegible(botellas, r.unidadesPorBulto, r.unidad);
}

function ContenidoOrden({ consulta, tipoEvento, alCambiar }: {
  consulta: OrdenDelEvento;
  tipoEvento: string;
  alCambiar: (consulta: OrdenDelEvento) => void;
}) {
  const { evento, orden, acciones } = consulta;
  const [confirmarRecalculo, setConfirmarRecalculo] = useState(false);
  const [aviso, setAviso] = useState<string>();
  const { guardando, error, enviar } = useEnvio();

  async function generar() {
    const nueva = await enviar(() => ordenes.generar(evento.id));
    if (nueva) {
      setAviso('Orden generada. Se avisó a Compras.');
      alCambiar(nueva);
    }
  }

  async function recalcular() {
    if (!orden) return;
    const nueva = await enviar(() => ordenes.recalcular(evento.id, orden.version));
    setConfirmarRecalculo(false);
    if (nueva) {
      setAviso('Sugerencia recalculada.');
      alCambiar(nueva);
    }
  }

  if (!orden) {
    return (
      <Card>
        {error && <Alert tone="danger">{error.message}</Alert>}
        <EmptyState
          icon="clipboard-list"
          title="Sin orden de preparación"
          action={acciones.generar && <Button icon="clipboard-list" loading={guardando} onClick={() => void generar()}>Generar orden</Button>}
        >
          {acciones.generar
            ? 'El evento se confirmó antes de que existiera la orden sugerida. Generala para ver cuánta bebida preparar.'
            : 'La orden se genera sola cuando el evento se confirma.'}
        </EmptyState>
      </Card>
    );
  }

  const estado = ESTADO_ORDEN[orden.estado];
  return (
    <div className="orden">
      {aviso && <Alert tone="success">{aviso}</Alert>}
      {error && <Alert tone="danger">{error.message}</Alert>}
      {consulta.asistentesCambiaron && (
        <Alert tone="warning" icon="triangle-alert" title="Cambiaron los asistentes del evento">
          La sugerencia se calculó sobre {orden.asistentesBase ?? 'una cantidad desconocida de'} asistentes y ahora son{' '}
          {evento.asistentes ?? 'desconocidos'}.{acciones.recalcular ? ' Podés recalcularla.' : ''}
        </Alert>
      )}
      <Card
        title="Orden de preparación"
        subtitle={`${evento.nombre} · ${evento.salon.nombre} · ${fechaCorta(evento.fecha, true)} · ${evento.asistentes ?? '—'} asistentes previstos`}
        actions={<Badge tone={estado.tone}>{estado.label}</Badge>}
      >
        <div className="orden__cuerpo">
          <p className="body-sm">{origenDeLaSugerencia(orden, tipoEvento)}</p>
          {orden.asistentesBase != null && (
            <p className="body-sm v-muted">
              Calculada sobre {orden.asistentesBase} asistentes, con {orden.margenPorcentaje} % de margen, en cajas completas.
              Sale de {orden.origen}.
            </p>
          )}
          <Table<RenglonOrden & { id: number }>
            caption="Bebida de la orden de preparación"
            rows={orden.renglones.map((r) => ({ ...r, id: r.bebidaId }))}
            empty="La orden no tiene bebidas."
            columns={[
              { key: 'bebida', header: 'Bebida', render: (r) => `${r.nombre} ${r.presentacion}${r.activa ? '' : ' · Dada de baja'}` },
              { key: 'sugerida', header: 'Cantidad propuesta', numeric: true, render: (r) => legible(r, r.sugerida) },
              { key: 'aRetirar', header: 'A retirar', numeric: true, render: (r) => legible(r, r.aRetirar) },
              { key: 'comprometido', header: 'Comprometido por otros eventos', numeric: true, render: (r) => (r.comprometido > 0 ? legible(r, r.comprometido) : '—') },
            ]}
          />
          <div className="orden__auditoria">
            <Actor name={orden.responsable?.nombre ?? 'Sistema'} action={orden.responsable ? 'la generó' : 'la generó al confirmar el evento'} at={fechaHora(orden.fechaCreacion)} size="sm" />
            {orden.modificacion?.usuario && <Actor name={orden.modificacion.usuario.nombre} action="la cambió por última vez" at={fechaHora(orden.modificacion.fechaHora)} size="sm" />}
          </div>
        </div>
      </Card>
      <div className="orden__acciones">
        <Button variant="outline" icon="printer" onClick={() => window.print()}>Vista para imprimir</Button>
        {acciones.recalcular && (
          <Button variant="outline" icon="refresh-cw" onClick={() => setConfirmarRecalculo(true)}>Recalcular sugerencia</Button>
        )}
      </div>
      {confirmarRecalculo && (
        <Dialog
          open
          title="Recalcular sugerencia"
          onClose={() => setConfirmarRecalculo(false)}
          actions={
            <>
              <Button variant="outline" onClick={() => setConfirmarRecalculo(false)}>Cancelar</Button>
              <Button icon="refresh-cw" loading={guardando} onClick={() => void recalcular()}>Recalcular sugerencia</Button>
            </>
          }
        >
          <p className="body">
            Se vuelve a calcular con los asistentes de hoy ({evento.asistentes ?? 'desconocidos'}) y el consumo histórico. La orden
            vuelve a Sugerida y se pierden los ajustes que se hayan hecho.
          </p>
        </Dialog>
      )}
    </div>
  );
}
