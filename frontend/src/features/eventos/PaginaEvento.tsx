import { useCallback, useState, type ReactNode } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router';
import type { EstadoEvento } from '../../api/agenda';
import { hora } from '../../api/catalogos';
import { fichas, type CambioDeEstado, type EntradaHistorial, type Ficha, type Modificacion } from '../../api/eventos';
import { useDatos } from '../../api/useDatos';
import {
  Actor, Alert, Button, Card, ESTADOS, ESTADO_POR_CODIGO, SalonTag, StatusChip, Tabs, Timeline,
  type TimelineItem, type TimelineTone,
} from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta, fechaHora, pesos } from '../comun/formato';
import { DialogoLiberar } from './DialogoLiberar';
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
      <Cargando datos={datos}>{(ficha) => <ContenidoFicha ficha={ficha} alCambiar={(nueva) => datos.fijar(() => nueva)} />}</Cargando>
    </section>
  );
}

/** Diálogos de las acciones que cambian el estado desde la ficha. */
type Dialogo = 'liberar' | null;

function ContenidoFicha({ ficha, alCambiar }: { ficha: Ficha; alCambiar: (ficha: Ficha) => void }) {
  const [busqueda, setBusqueda] = useSearchParams();
  const pestana = busqueda.get('pestana') === 'historial' ? 'historial' : 'datos';
  // Aviso que deja otra pantalla al volver a la ficha (p. ej. «Datos del evento guardados.»).
  const recibido = (useLocation().state as { aviso?: string } | null)?.aviso;
  const [aviso, setAviso] = useState(recibido);
  const [dialogo, setDialogo] = useState<Dialogo>(null);
  const navegar = useNavigate();
  const { acciones } = ficha;

  function hecho(nueva: Ficha, mensaje: string) {
    setDialogo(null);
    setAviso(mensaje);
    alCambiar(nueva);
  }

  return (
    <>
      {aviso && <Alert tone="success">{aviso}</Alert>}
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

      {(acciones.modificar || acciones.liberar) && (
        <div className="ficha__acciones">
          {acciones.modificar && (
            <Button variant="outline" icon="pencil" onClick={() => navegar(`/eventos/${ficha.id}/datos`)}>Modificar datos</Button>
          )}
          {acciones.liberar && (
            <Button variant="outline" icon="lock-open" onClick={() => setDialogo('liberar')}>Liberar pre-reserva</Button>
          )}
        </div>
      )}
      {dialogo === 'liberar' && (
        <DialogoLiberar
          ficha={ficha}
          alCerrar={() => setDialogo(null)}
          alLiberar={(f) => hecho(f, 'Pre-reserva liberada. La fecha volvió a estar disponible en la agenda.')}
        />
      )}

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
      <Card title="Otros contactos">
        {ficha.contactos.length === 0 ? (
          <p className="body-sm v-muted">Sin contactos además del cliente.</p>
        ) : (
          <dl className="ficha__lista">
            {ficha.contactos.map((c) => (
              <Dato key={c.id} etiqueta={c.vinculo ?? 'Contacto'}>
                {[c.nombre, c.telefono, c.email].filter(Boolean).join(' · ')}
              </Dato>
            ))}
          </dl>
        )}
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
function describir(e: CambioDeEstado): { titulo: string; accion: string } {
  if (!e.estadoAnterior) return { titulo: 'Pre-reserva registrada', accion: 'apartó la fecha' };
  switch (e.estadoNuevo) {
    case 'SENADO': return { titulo: 'Seña registrada', accion: 'registró la seña' };
    case 'LIBERADA': return { titulo: 'Pre-reserva liberada', accion: 'liberó la fecha' };
    default: return { titulo: `Pasó a ${ESTADOS[ESTADO_POR_CODIGO[e.estadoNuevo]].label}`, accion: 'cambió el estado' };
  }
}

/** Nombre en pantalla de cada dato del registro de modificaciones. */
const CAMPOS: Record<string, string> = {
  nombre: 'Nombre del evento',
  tipo_evento: 'Tipo',
  cantidad_invitados: 'Invitados',
  observaciones_internas: 'Observaciones internas',
  'cliente.nombre': 'Cliente',
  'cliente.documento': 'Documento del cliente',
  'cliente.telefono': 'Teléfono del cliente',
  'cliente.email': 'Correo del cliente',
  contacto: 'Contacto',
};

function describirModificacion(m: Modificacion): string {
  const campo = CAMPOS[m.campo] ?? m.campo;
  if (m.campo === 'contacto') {
    if (!m.valorAnterior) return `Agregó el contacto ${m.valorNuevo}`;
    if (!m.valorNuevo) return `Quitó el contacto ${m.valorAnterior}`;
  }
  if (m.campo === 'observaciones_internas') return m.valorNuevo ? 'Cambió las observaciones internas' : 'Borró las observaciones internas';
  return `${campo}: ${m.valorAnterior ?? 'sin dato'} → ${m.valorNuevo ?? 'sin dato'}`;
}

/** Los cambios de datos de un mismo guardado (misma persona y momento) van juntos en una entrada. */
function agrupar(historial: EntradaHistorial[]): (CambioDeEstado | Modificacion[])[] {
  const grupos: (CambioDeEstado | Modificacion[])[] = [];
  for (const e of historial) {
    const ultimo = grupos[grupos.length - 1];
    if (e.tipo === 'MODIFICACION' && Array.isArray(ultimo) && ultimo[0].fechaHora === e.fechaHora && ultimo[0].usuario.id === e.usuario.id) {
      ultimo.push(e);
    } else {
      grupos.push(e.tipo === 'MODIFICACION' ? [e] : e);
    }
  }
  return grupos;
}

function Historial({ historial }: { historial: EntradaHistorial[] }) {
  const items: TimelineItem[] = agrupar(historial).map((g) => {
    if (Array.isArray(g)) {
      return {
        title: 'Datos modificados',
        detail: g.map(describirModificacion).join('. ') + '.',
        actor: g[0].usuario.nombre,
        action: 'modificó los datos',
        at: fechaHora(g[0].fechaHora),
        icon: 'pencil',
        tone: 'neutral',
      };
    }
    const { titulo, accion } = describir(g);
    return {
      title: titulo,
      from: g.estadoAnterior ? ESTADO_POR_CODIGO[g.estadoAnterior] : undefined,
      to: ESTADO_POR_CODIGO[g.estadoNuevo],
      detail: g.observacion,
      actor: g.usuario?.nombre ?? 'Sistema',
      action: g.usuario ? accion : 'cambio automático',
      at: fechaHora(g.fechaHora),
      icon: ESTADOS[ESTADO_POR_CODIGO[g.estadoNuevo]].icon,
      tone: TONO[g.estadoNuevo] ?? 'neutral',
    };
  });
  return (
    <Card>
      <Timeline items={items} className="ficha__historial" />
    </Card>
  );
}
