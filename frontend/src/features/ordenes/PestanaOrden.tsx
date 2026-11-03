import { useCallback, useMemo, useState } from 'react';
import { bebidas as catalogoDeBebidas, type Bebida } from '../../api/bebidas';
import { ordenes, ESTADO_ORDEN, type Orden, type OrdenDelEvento, type RenglonOrden } from '../../api/ordenes';
import { useDatos, useEnvio } from '../../api/useDatos';
import { Actor, Alert, Badge, Button, CantidadEnCajas, Card, Combobox, Dialog, EmptyState, Table, cantidadLegible } from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta, fechaHora } from '../comun/formato';
import './ordenes.css';

/**
 * UI-35 · Orden de preparación del evento (docs/sprint-4.md, decisiones 11 a 25): lo sugerido por el consumo histórico,
 * lo que se va a retirar y lo comprometido por otros eventos próximos, en cajas y botellas. Se genera sola al confirmar
 * el evento; Compras, Coordinación y Dirección la generan a mano, la recalculan, la ajustan, la confirman y la reabren.
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

type Dialogo = 'recalcular' | 'confirmar' | 'reabrir' | null;

function ContenidoOrden({ consulta, tipoEvento, alCambiar }: {
  consulta: OrdenDelEvento;
  tipoEvento: string;
  alCambiar: (consulta: OrdenDelEvento) => void;
}) {
  const { evento, orden, acciones } = consulta;
  const [dialogo, setDialogo] = useState<Dialogo>(null);
  const [ajustando, setAjustando] = useState(false);
  const [aviso, setAviso] = useState<string>();
  const { guardando, error, enviar } = useEnvio();

  async function hacer(accion: () => Promise<OrdenDelEvento>, mensaje: string) {
    const nueva = await enviar(accion);
    setDialogo(null);
    if (nueva) {
      setAviso(mensaje);
      setAjustando(false);
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
          action={acciones.generar && (
            <Button icon="clipboard-list" loading={guardando} onClick={() => void hacer(() => ordenes.generar(evento.id), 'Orden generada. Se avisó a Compras.')}>
              Generar orden
            </Button>
          )}
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
          {ajustando ? (
            <Ajuste
              orden={orden}
              guardando={guardando}
              alCancelar={() => setAjustando(false)}
              alGuardar={(renglones) => void hacer(() => ordenes.ajustar(evento.id, orden.version, renglones), 'Ajustes guardados. La orden quedó Modificada.')}
            />
          ) : (
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
          )}
          <div className="orden__auditoria">
            <Actor name={orden.responsable?.nombre ?? 'Sistema'} action={orden.responsable ? 'la generó' : 'la generó al confirmar el evento'} at={fechaHora(orden.fechaCreacion)} size="sm" />
            {orden.modificacion?.usuario && <Actor name={orden.modificacion.usuario.nombre} action="la cambió por última vez" at={fechaHora(orden.modificacion.fechaHora)} size="sm" />}
            {orden.confirmacion?.usuario && <Actor name={orden.confirmacion.usuario.nombre} action="la confirmó" at={fechaHora(orden.confirmacion.fechaHora)} size="sm" />}
            {orden.reapertura?.usuario && <Actor name={orden.reapertura.usuario.nombre} action="la reabrió" at={fechaHora(orden.reapertura.fechaHora)} size="sm" />}
          </div>
        </div>
      </Card>
      {!ajustando && (
        <div className="orden__acciones">
          {acciones.confirmar && <Button icon="circle-check" onClick={() => setDialogo('confirmar')}>Confirmar orden</Button>}
          {acciones.ajustar && <Button variant="outline" icon="pencil" onClick={() => setAjustando(true)}>Ajustar cantidades</Button>}
          {acciones.reabrir && <Button variant="outline" icon="lock-open" onClick={() => setDialogo('reabrir')}>Reabrir orden</Button>}
          {acciones.recalcular && <Button variant="outline" icon="refresh-cw" onClick={() => setDialogo('recalcular')}>Recalcular sugerencia</Button>}
          <Button variant="outline" icon="printer" onClick={() => window.print()}>Vista para imprimir</Button>
        </div>
      )}
      {dialogo === 'recalcular' && (
        <Confirmacion
          titulo="Recalcular sugerencia"
          accion="Recalcular sugerencia"
          icono="refresh-cw"
          guardando={guardando}
          alCerrar={() => setDialogo(null)}
          alConfirmar={() => void hacer(() => ordenes.recalcular(evento.id, orden.version), 'Sugerencia recalculada.')}
        >
          Se vuelve a calcular con los asistentes de hoy ({evento.asistentes ?? 'desconocidos'}) y el consumo histórico. La orden
          vuelve a Sugerida y se pierden los ajustes que se hayan hecho.
        </Confirmacion>
      )}
      {dialogo === 'confirmar' && (
        <Confirmacion
          titulo="Confirmar orden"
          accion="Confirmar orden"
          icono="circle-check"
          guardando={guardando}
          alCerrar={() => setDialogo(null)}
          alConfirmar={() => void hacer(() => ordenes.confirmar(evento.id, orden.version), 'Orden confirmada. La salida a barra queda vinculada a ella.')}
        >
          Con la orden confirmada ya no se cambian las cantidades. Si algo cambia sobre la marcha, se puede reabrir.
        </Confirmacion>
      )}
      {dialogo === 'reabrir' && (
        <Confirmacion
          titulo="Reabrir orden"
          accion="Reabrir orden"
          icono="lock-open"
          guardando={guardando}
          alCerrar={() => setDialogo(null)}
          alConfirmar={() => void hacer(() => ordenes.reabrir(evento.id, orden.version), 'Orden reabierta: ya podés ajustarla.')}
        >
          La orden vuelve a Modificada para ajustar las cantidades. Después volvé a confirmarla.
        </Confirmacion>
      )}
    </div>
  );
}

function Confirmacion({ titulo, accion, icono, guardando, alCerrar, alConfirmar, children }: {
  titulo: string;
  accion: string;
  icono: 'refresh-cw' | 'circle-check' | 'lock-open';
  guardando: boolean;
  alCerrar: () => void;
  alConfirmar: () => void;
  children: string | (string | number)[];
}) {
  return (
    <Dialog
      open
      title={titulo}
      onClose={alCerrar}
      actions={
        <>
          <Button variant="outline" onClick={alCerrar}>Cancelar</Button>
          <Button icon={icono} loading={guardando} onClick={alConfirmar}>{accion}</Button>
        </>
      }
    >
      <p className="body">{children}</p>
    </Dialog>
  );
}

/** Un renglón mientras se ajusta: los datos de la bebida y las botellas a retirar. */
interface EnAjuste {
  bebidaId: number;
  nombre: string;
  presentacion: string;
  unidad: string;
  unidadesPorBulto: number;
  sugerida: number;
  aRetirar: number;
}

/**
 * Ajustar orden (decisión 23): «a retirar» en cajas y botellas sueltas por bebida, y bebidas que no estaban sugeridas.
 * Para «sacar» una bebida se la deja en 0.
 */
function Ajuste({ orden, guardando, alCancelar, alGuardar }: {
  orden: Orden;
  guardando: boolean;
  alCancelar: () => void;
  alGuardar: (renglones: { bebidaId: number; cantidad: number }[]) => void;
}) {
  const [renglones, setRenglones] = useState<EnAjuste[]>(() => orden.renglones.map((r) => ({ ...r })));
  const [buscar, setBuscar] = useState('');
  const catalogo = useDatos(catalogoDeBebidas.listar);

  const opciones = useMemo(() => {
    const texto = buscar.trim().toLowerCase();
    return (catalogo.datos ?? [])
      .filter((b) => b.activo && !renglones.some((r) => r.bebidaId === b.id))
      .filter((b) => `${b.nombre} ${b.presentacion}`.toLowerCase().includes(texto))
      .slice(0, 8)
      .map((b) => ({ value: String(b.id), label: `${b.nombre} ${b.presentacion}` }));
  }, [buscar, catalogo.datos, renglones]);

  function agregar(bebida: Bebida) {
    setRenglones((previos) => [...previos, {
      bebidaId: bebida.id, nombre: bebida.nombre, presentacion: bebida.presentacion, unidad: bebida.unidad.nombre,
      unidadesPorBulto: bebida.unidadesPorBulto, sugerida: 0, aRetirar: 0,
    }]);
    setBuscar('');
  }

  const cambiados = renglones.filter((r) => r.aRetirar !== orden.renglones.find((o) => o.bebidaId === r.bebidaId)?.aRetirar);

  return (
    <div className="orden__ajuste">
      <ul className="orden__renglones" aria-label="Cantidades a retirar">
        {renglones.map((r) => (
          <li key={r.bebidaId} className="orden__renglon">
            <div>
              <p className="label">{r.nombre} {r.presentacion}</p>
              <p className="body-sm v-muted">
                {r.sugerida > 0 ? `Propuesta: ${cantidadLegible(r.sugerida, r.unidadesPorBulto, r.unidad)}` : 'Agregada a mano'}
              </p>
            </div>
            <CantidadEnCajas
              label={`A retirar de ${r.nombre}`}
              value={r.aRetirar}
              porBulto={r.unidadesPorBulto}
              unidad={r.unidad}
              onChange={(botellas) => setRenglones((previos) => previos.map((x) => (x.bebidaId === r.bebidaId ? { ...x, aRetirar: botellas } : x)))}
            />
          </li>
        ))}
      </ul>
      <Combobox
        label="Agregar bebida"
        value={buscar}
        onInputChange={setBuscar}
        options={opciones}
        loading={catalogo.cargando}
        onSelect={(o) => {
          const bebida = catalogo.datos?.find((b) => String(b.id) === o.value);
          if (bebida) agregar(bebida);
        }}
        emptyText="Ninguna bebida activa con ese nombre que no esté en la orden."
        hint="Para sacar una bebida de la orden, dejala en 0."
      />
      <div className="orden__acciones">
        <Button
          icon="check"
          loading={guardando}
          disabled={cambiados.length === 0}
          onClick={() => alGuardar(cambiados.map((r) => ({ bebidaId: r.bebidaId, cantidad: r.aRetirar })))}
        >
          Guardar ajustes
        </Button>
        <Button variant="outline" onClick={alCancelar}>Cancelar</Button>
      </div>
    </div>
  );
}
