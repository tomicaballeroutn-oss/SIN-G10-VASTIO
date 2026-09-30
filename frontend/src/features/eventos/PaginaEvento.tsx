import { useCallback, type ReactNode } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import type { EstadoEvento } from '../../api/agenda';
import { hora } from '../../api/catalogos';
import { fichas, type EntradaHistorial, type Ficha } from '../../api/eventos';
import { useDatos } from '../../api/useDatos';
import {
  Actor, Button, Card, ESTADOS, ESTADO_POR_CODIGO, SalonTag, StatusChip, Tabs, Timeline,
  type TimelineItem, type TimelineTone,
} from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta, fechaHora, pesos } from '../comun/formato';
import './eventos.css';

/**
 * UI-09 · Ficha del evento: estado, datos y el historial de cambios en un solo lugar.
 * En este sprint, pestañas Datos e Historial; las demás llegan con sus historias.
 */
export function PaginaEvento() {
  const { id } = useParams();
  const cargar = useCallback(() => fichas.ficha(Number(id)), [id]);
  const datos = useDatos(cargar);
  const navegar = useNavigate();
  // Si se abrió desde un enlace directo no hay a dónde volver dentro de la app: va a la lista.
  const volver = () => ((window.history.state as { idx?: number } | null)?.idx ? navegar(-1) : navegar('/eventos'));

  return (
    <section className="pantalla">
      <div>
        <Button variant="text" icon="arrow-left" onClick={volver}>Volver</Button>
      </div>
      <Cargando datos={datos}>{(ficha) => <ContenidoFicha ficha={ficha} />}</Cargando>
    </section>
  );
}

function ContenidoFicha({ ficha }: { ficha: Ficha }) {
  const [busqueda, setBusqueda] = useSearchParams();
  const pestana = busqueda.get('pestana') === 'historial' ? 'historial' : 'datos';

  return (
    <>
      <header className="ficha__cabecera">
        <div className="ficha__etiquetas">
          <StatusChip status={ESTADO_POR_CODIGO[ficha.estado]} />
          <SalonTag salon={ficha.salon.codigo} label={ficha.salon.nombre} variant="dot" />
          <span className="body">
            {fechaCorta(ficha.fecha, true)} · {ficha.turno.nombre} ({hora(ficha.turno.horaInicio)} a {hora(ficha.turno.horaFin)})
          </span>
        </div>
        <h1>{ficha.nombre}</h1>
        <p className="body-sm v-muted">
          {ficha.codigo} · {ficha.tipo.nombre} · {ficha.planner ? `Planner: ${ficha.planner.nombre}` : 'Sin planner asignada'}
        </p>
      </header>

      <Tabs
        label="Secciones de la ficha"
        items={[
          { id: 'datos', label: 'Datos' },
          { id: 'historial', label: 'Historial', count: ficha.historial.length },
        ]}
        value={pestana}
        onChange={(p) => setBusqueda(p === 'datos' ? {} : { pestana: p }, { replace: true })}
      />

      {pestana === 'datos' ? <Datos ficha={ficha} /> : <Historial historial={ficha.historial} />}
    </>
  );
}

function Dato({ etiqueta, children }: { etiqueta: string; children: ReactNode }) {
  return (
    <div className="ficha__dato">
      <dt className="label v-muted">{etiqueta}</dt>
      <dd className="body">{children}</dd>
    </div>
  );
}

const SIN_DATO = '—';

function Datos({ ficha }: { ficha: Ficha }) {
  const { cliente, sena } = ficha;
  return (
    <div className="ficha__datos">
      <Card title="Cliente">
        <dl className="ficha__lista">
          <Dato etiqueta="Nombre">{cliente.nombre}</Dato>
          <Dato etiqueta="Documento">{cliente.documento ?? SIN_DATO}</Dato>
          <Dato etiqueta="Teléfono">{cliente.telefono ?? SIN_DATO}</Dato>
          <Dato etiqueta="Correo">{cliente.email ?? SIN_DATO}</Dato>
        </dl>
      </Card>
      <Card title="Evento">
        <dl className="ficha__lista">
          <Dato etiqueta="Tipo">{ficha.tipo.nombre}</Dato>
          <Dato etiqueta="Invitados">
            {ficha.cantidadInvitados == null
              ? 'Sin definir'
              : `${ficha.cantidadInvitados}${ficha.invitadosDefinitivos ? ' (definitivos)' : ' (previstos)'}`}
          </Dato>
          <Dato etiqueta="Vendedora"><Actor name={ficha.vendedora.nombre} size="sm" /></Dato>
          <Dato etiqueta="Planner">{ficha.planner ? <Actor name={ficha.planner.nombre} size="sm" /> : 'Sin asignar'}</Dato>
          <Dato etiqueta="Registrado">{fechaHora(ficha.fechaCreacion)}</Dato>
        </dl>
      </Card>
      {sena && (
        <Card title="Seña">
          <dl className="ficha__lista">
            {sena.importe != null && <Dato etiqueta="Importe"><span className="numeral">{pesos(sena.importe)}</span></Dato>}
            <Dato etiqueta="Fecha del pago">{fechaCorta(sena.fecha, true)}</Dato>
            <Dato etiqueta="Firmante">{[sena.firmanteNombre, sena.firmanteDni && `DNI ${sena.firmanteDni}`].filter(Boolean).join(' · ') || SIN_DATO}</Dato>
            <Dato etiqueta="Contacto del firmante">{sena.firmanteContacto ?? SIN_DATO}</Dato>
          </dl>
        </Card>
      )}
      <Card title="Observaciones internas" subtitle="No se muestran en la vista de cocina.">
        <p className="body ficha__observaciones">{ficha.observacionesInternas || 'Sin observaciones.'}</p>
      </Card>
    </div>
  );
}

const TONO: Partial<Record<EstadoEvento, TimelineTone>> = {
  PRE_RESERVA: 'warning',
  SENADO: 'info',
  CONTRATADO: 'brand',
  CONFIRMADO: 'success',
  CANCELADO: 'danger',
};

/** Título y verbo de cada transición, como en UI-09: «Pre-reserva · Lucía Ferreyra · apartó la fecha». */
function describir(e: EntradaHistorial): { titulo: string; accion: string } {
  if (e.estadoAnterior === null) return { titulo: 'Pre-reserva registrada', accion: 'apartó la fecha' };
  switch (e.estadoNuevo) {
    case 'SENADO': return { titulo: 'Seña registrada', accion: 'registró la seña' };
    case 'LIBERADA': return { titulo: 'Pre-reserva liberada', accion: 'liberó la fecha' };
    default: return { titulo: `Pasó a ${ESTADOS[ESTADO_POR_CODIGO[e.estadoNuevo]].label}`, accion: 'cambió el estado' };
  }
}

function Historial({ historial }: { historial: EntradaHistorial[] }) {
  const items: TimelineItem[] = historial.map((e) => {
    const { titulo, accion } = describir(e);
    return {
      title: titulo,
      from: e.estadoAnterior ? ESTADO_POR_CODIGO[e.estadoAnterior] : undefined,
      to: ESTADO_POR_CODIGO[e.estadoNuevo],
      detail: e.observacion ?? undefined,
      actor: e.usuario?.nombre ?? 'Sistema',
      action: e.usuario ? accion : 'cambio automático',
      at: fechaHora(e.fechaHora),
      icon: ESTADOS[ESTADO_POR_CODIGO[e.estadoNuevo]].icon,
      tone: TONO[e.estadoNuevo] ?? 'neutral',
    };
  });
  return (
    <Card>
      <Timeline items={items} className="ficha__historial" />
    </Card>
  );
}
