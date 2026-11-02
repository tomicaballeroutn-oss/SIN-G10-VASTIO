import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { cierres, type Cierre, type RenglonCierre } from '../../api/cierres';
import { useDatos, useEnvio } from '../../api/useDatos';
import {
  Alert, Button, Card, Dialog, ESTADO_POR_CODIGO, SalonTag, StatusChip, Table, cantidadLegible, type SalonId,
} from '../../ds';
import { Cargando } from '../comun/Cargando';
import { Encabezado } from '../layout/paginas';
import './barra.css';

const POR_ASISTENTE = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 3 });

/** «0,022 botellas»; sin invitados cargados, «Sin invitados». */
function porAsistente(botellas: number | null): string {
  if (botellas === null) return 'Sin invitados';
  return `${POR_ASISTENTE.format(botellas)} ${botellas === 1 ? 'botella' : 'botellas'}`;
}

const legible = (r: RenglonCierre, botellas: number) => cantidadLegible(botellas, r.unidadesPorBulto, r.unidad);

/**
 * UI-33 · Registrar cierre de evento: la conciliación final. Por bebida, en todas las barras del evento, lo entregado,
 * devuelto, remanente y consumido, y el consumo por asistente. Al cerrar, el consumo queda fijado y el evento pasa a
 * Cerrado: después todo es solo lectura y se corrige con ajustes. Sin importes: el sistema no maneja dinero.
 * La encargada de barra, Compras, Coordinación y Dirección.
 */
export function PaginaCierre() {
  const { id } = useParams();
  const eventoId = Number(id);
  const cargar = useCallback(() => cierres.pantalla(eventoId), [eventoId]);
  const datos = useDatos(cargar);
  return (
    <section className="pantalla">
      <Encabezado titulo="Cierre de evento" antetitulo="Barra" />
      <Cargando datos={datos}>
        {(cierre) => <Conciliacion cierre={cierre} alCerrar={(c) => datos.fijar(() => c)} />}
      </Cargando>
    </section>
  );
}

function Conciliacion({ cierre, alCerrar }: { cierre: Cierre; alCerrar: (c: Cierre) => void }) {
  const navegar = useNavigate();
  const [confirmando, setConfirmando] = useState(false);
  const [recienCerrado, setRecienCerrado] = useState(false);
  const { guardando, error, enviar, limpiarError } = useEnvio();
  const estado = ESTADO_POR_CODIGO[cierre.evento.estado as keyof typeof ESTADO_POR_CODIGO];
  const realizado = cierre.evento.estado === 'REALIZADO';
  const sePuedeCerrar = realizado && cierre.pendientes.length === 0;

  async function cerrar() {
    const cerrado = await enviar(() => cierres.cerrar(cierre.evento.id));
    if (cerrado) {
      setConfirmando(false);
      setRecienCerrado(true);
      alCerrar(cerrado);
    }
  }

  return (
    <>
      <Card title={cierre.evento.nombre} subtitle={cierre.evento.codigo} actions={estado && <StatusChip status={estado} size="sm" />}>
        <SalonTag salon={cierre.salon.codigo as SalonId} label={cierre.salon.nombre} />
      </Card>

      {recienCerrado && (
        <Alert tone="success" title="Evento cerrado">
          El consumo quedó fijado. Lo que quedó en la barra sigue ahí para el evento siguiente.
        </Alert>
      )}
      {cierre.cerrado && !recienCerrado && (
        <Alert tone="info" title="Este evento ya está cerrado">
          Es solo lectura: una diferencia se corrige con un ajuste de stock.
        </Alert>
      )}
      {!cierre.cerrado && !realizado && (
        <Alert tone="warning" icon="clock" title="Todavía no se puede cerrar">
          Se cierra un evento realizado: pasa solo a Realizado cuando termina el turno.
        </Alert>
      )}
      {cierre.pendientes.length > 0 && (
        <Alert tone="warning" title="Falta registrar devoluciones">
          Falta la devolución de {cierre.pendientes.map((b) => b.nombre).join(' y ')}. Registrala en Barra antes de cerrar.
        </Alert>
      )}

      <Card title="Consumo real" subtitle="Entregado − devuelto − remanente, en todas las barras del evento">
        <Table
          caption="Consumo por bebida"
          dense
          rows={cierre.renglones.map((r) => ({ ...r, id: r.bebidaId }))}
          empty="No se entregó bebida a ninguna barra para este evento."
          columns={[
            { key: 'bebida', header: 'Bebida', render: (r) => `${r.nombre} ${r.presentacion}` },
            { key: 'entregado', header: 'Entregado', numeric: true, render: (r) => legible(r, r.entregado) },
            { key: 'devuelto', header: 'Devuelto', numeric: true, render: (r) => legible(r, r.devuelto) },
            { key: 'remanente', header: 'Remanente', numeric: true, render: (r) => legible(r, r.remanente) },
            { key: 'consumo', header: 'Consumo', numeric: true, render: (r) => legible(r, r.consumo) },
            { key: 'porAsistente', header: 'Por asistente', numeric: true, render: (r) => porAsistente(r.porAsistente) },
          ]}
        />
      </Card>

      <Card title="Consumo por asistente">
        <p className="body">
          {cierre.asistenciaReal
            ? <>En total, <span className="numeral">{porAsistente(cierre.porAsistenteTotal)}</span> por asistente, sobre {cierre.asistenciaReal} asistentes reales.</>
            : cierre.invitados
              ? <>En total, <span className="numeral">{porAsistente(cierre.porAsistenteTotal)}</span> por asistente, sobre {cierre.invitados} invitados.</>
              : 'El evento no tiene la cantidad de invitados cargada: no se puede calcular por asistente.'}
        </p>
        {cierre.usaSegmentos && !cierre.asistenciaReal && (
          <p className="body-sm v-muted">
            Este tipo de evento cuenta la asistencia por segmento. Mientras la planner no cargue la real, se divide por la cantidad de
            invitados; cuando la cargue, el consumo por asistente se recalcula sobre ella (lo consumido no cambia).
          </p>
        )}
      </Card>

      <div className="barra__pie">
        <Button variant="outline" size="lg" onClick={() => navegar('/barra')}>Volver a Barra</Button>
        {!cierre.cerrado && (
          <Button size="lg" icon="archive" disabled={!sePuedeCerrar} onClick={() => { limpiarError(); setConfirmando(true); }}>
            Cerrar evento
          </Button>
        )}
      </div>

      {confirmando && (
        <Dialog
          open
          title="¿Cerrás el evento?"
          onClose={() => setConfirmando(false)}
          actions={
            <>
              <Button variant="outline" onClick={() => setConfirmando(false)}>Volver</Button>
              <Button icon="archive" loading={guardando} onClick={() => void cerrar()}>Registrar cierre</Button>
            </>
          }
        >
          <div className="barra__revision">
            {error && <Alert tone="danger">{error.message}</Alert>}
            <p>Se fija el consumo de {cierre.evento.nombre} y el evento pasa a Cerrado.</p>
            <p className="body-sm v-muted">Después no se modifica: una diferencia se corrige con un ajuste de stock.</p>
          </div>
        </Dialog>
      )}
    </>
  );
}
