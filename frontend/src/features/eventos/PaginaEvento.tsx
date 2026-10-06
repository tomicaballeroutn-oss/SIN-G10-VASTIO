import { useCallback, useState, type ReactNode } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router';
import type { EstadoEvento } from '../../api/agenda';
import { hora } from '../../api/catalogos';
import {
  fichas, type CambioDeEstado, type CambioDeUnidad, type DocumentoLegajo, type EntradaHistorial, type Ficha, type Modificacion,
} from '../../api/eventos';
import { useDatos, useEnvio } from '../../api/useDatos';
import {
  Actor, Alert, Button, Card, EmptyState, ESTADOS, ESTADO_POR_CODIGO, Icon, IconButton, SalonTag, StatusChip, Tabs, Timeline,
  tamanoLegible, type TimelineItem, type TimelineTone,
} from '../../ds';
import { Cargando } from '../comun/Cargando';
import { guardarArchivo } from '../comun/archivos';
import { fechaCorta, fechaHora, pesos } from '../comun/formato';
import { DialogoCancelar } from './DialogoCancelar';
import { DialogoConfirmar } from './DialogoConfirmar';
import { DialogoFirma } from './DialogoFirma';
import { DialogoInvitados } from './DialogoInvitados';
import { DialogoPlanner } from './DialogoPlanner';
import { DialogoLiberar } from './DialogoLiberar';
import { PestanaServicios } from './PestanaServicios';
import { DialogoSena } from './DialogoSena';
import './eventos.css';

/**
 * UI-09 · Ficha del evento: estado, datos y el historial de cambios en un solo lugar.
 * Pestañas Datos, Servicios, Documentos (solo para quien ve el legajo) e Historial; las demás llegan con sus historias.
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
type Dialogo = 'liberar' | 'sena' | 'firma' | 'invitados' | 'planner' | 'confirmar' | 'cancelar' | null;

type Pestana = 'datos' | 'servicios' | 'documentos' | 'historial';

function ContenidoFicha({ ficha, alCambiar }: { ficha: Ficha; alCambiar: (ficha: Ficha) => void }) {
  const [busqueda, setBusqueda] = useSearchParams();
  const pedida = busqueda.get('pestana');
  const pestana: Pestana = pedida === 'historial' || pedida === 'servicios' ? pedida
    : pedida === 'documentos' && ficha.documentos ? 'documentos' : 'datos';
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
        {ficha.reprogramadoDesde && (
          <p className="body-sm ficha__reprogramado">
            <Icon name="calendar-clock" size={16} /> Reprogramado. Fecha original: {ficha.reprogramadoDesde}
          </p>
        )}
      </header>

      {Object.values(acciones).some(Boolean) && (
        <div className="ficha__acciones">
          {acciones.registrarSena && (
            <Button icon="banknote" onClick={() => setDialogo('sena')}>Registrar seña</Button>
          )}
          {acciones.registrarFirma && (
            <Button icon="file-check" onClick={() => setDialogo('firma')}>Registrar firma</Button>
          )}
          {acciones.confirmar && (
            <Button icon="circle-check" onClick={() => setDialogo('confirmar')}>Confirmar evento</Button>
          )}
          {acciones.asignarPlanner && (
            <Button variant={ficha.planner ? 'outline' : 'solid'} icon="user-plus" onClick={() => setDialogo('planner')}>
              {ficha.planner ? 'Cambiar planner' : 'Asignar planner'}
            </Button>
          )}
          {acciones.modificar && (
            <Button variant="outline" icon="pencil" onClick={() => navegar(`/eventos/${ficha.id}/datos`)}>Modificar datos</Button>
          )}
          {acciones.modificar && (
            <Button variant="outline" icon="users" onClick={() => setDialogo('invitados')}>Cantidad de invitados</Button>
          )}
          {acciones.reprogramar && (
            <Button variant="outline" icon="calendar-clock" onClick={() => navegar(`/eventos/${ficha.id}/reprogramar`)}>Reprogramar</Button>
          )}
          {acciones.liberar && (
            <Button variant="outline" icon="lock-open" onClick={() => setDialogo('liberar')}>Liberar pre-reserva</Button>
          )}
          {acciones.cancelar && (
            <Button variant="outline" tone="danger" icon="calendar-x" onClick={() => setDialogo('cancelar')}>Cancelar evento</Button>
          )}
        </div>
      )}
      {dialogo === 'sena' && (
        <DialogoSena
          ficha={ficha}
          alCerrar={() => setDialogo(null)}
          alRegistrar={(f) => hecho(f, 'Seña registrada. El evento pasó a Señado.')}
        />
      )}
      {dialogo === 'firma' && (
        <DialogoFirma
          ficha={ficha}
          alCerrar={() => setDialogo(null)}
          alRegistrar={(f) => hecho(f, 'Firma registrada. El evento pasó a Contratado.')}
        />
      )}
      {dialogo === 'invitados' && (
        <DialogoInvitados
          ficha={ficha}
          alCerrar={() => setDialogo(null)}
          alGuardar={(f) => hecho(f, 'Cantidad de invitados guardada.')}
        />
      )}
      {dialogo === 'cancelar' && (
        <DialogoCancelar
          ficha={ficha}
          alCerrar={() => setDialogo(null)}
          alCancelar={(f) => hecho(f, 'Evento cancelado. La fecha volvió a estar disponible y se avisó a las áreas.')}
        />
      )}
      {dialogo === 'confirmar' && (
        <DialogoConfirmar
          ficha={ficha}
          alCerrar={() => setDialogo(null)}
          alConfirmar={(f) => hecho(f, 'Evento confirmado. Se avisó a Compras y Cocina.')}
        />
      )}
      {dialogo === 'planner' && (
        <DialogoPlanner
          ficha={ficha}
          alCerrar={() => setDialogo(null)}
          alAsignar={(f) => hecho(f, f.planner ? `Planner asignada: ${f.planner.nombre}.` : 'Planner quitada.')}
        />
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
          { id: 'servicios', label: 'Servicios', count: ficha.servicios.filter((x) => x.descripcion).length },
          ...(ficha.documentos ? [{ id: 'documentos', label: 'Documentos', count: ficha.documentos.length }] : []),
          { id: 'historial', label: 'Historial', count: ficha.historial.length },
        ]}
        value={pestana}
        onChange={(p) => setBusqueda(p === 'datos' ? {} : { pestana: p }, { replace: true })}
      />

      {pestana === 'datos' && <Datos ficha={ficha} />}
      {pestana === 'servicios' && <PestanaServicios key={ficha.id} ficha={ficha} alGuardar={alCambiar} />}
      {pestana === 'documentos' && <Documentos ficha={ficha} documentos={ficha.documentos ?? []} />}
      {pestana === 'historial' && <Historial historial={ficha.historial} />}
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
          <Dato etiqueta="Hora de inicio">
            {ficha.horaInicio ? hora(ficha.horaInicio) : `${hora(ficha.turno.horaInicio)} (la del turno)`}
          </Dato>
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
      {ficha.cancelacion && (
        <Card title="Cancelación">
          <dl className="ficha__lista">
            <Dato etiqueta="Motivo">{ficha.cancelacion.motivo}</Dato>
            <Dato etiqueta="Detalle">{ficha.cancelacion.detalle ?? SIN_DATO}</Dato>
          </dl>
        </Card>
      )}
      {ficha.fechaFirmaContrato && (
        <Card title="Contrato">
          <dl className="ficha__lista">
            <Dato etiqueta="Fecha de firma">{fechaCorta(ficha.fechaFirmaContrato, true)}</Dato>
          </dl>
        </Card>
      )}
      <Card title="Observaciones internas" subtitle="No se muestran en la vista de cocina.">
        <p className="body ficha__observaciones">{ficha.observacionesInternas || 'Sin observaciones.'}</p>
      </Card>
    </div>
  );
}

/** Legajo del evento: el contrato digitalizado. Solo llega a quien ve datos económicos. */
function Documentos({ ficha, documentos }: { ficha: Ficha; documentos: DocumentoLegajo[] }) {
  const { error, enviar } = useEnvio();

  async function descargar(d: DocumentoLegajo) {
    const archivo = await enviar(() => fichas.descargarDocumento(ficha.id, d.id));
    if (archivo) guardarArchivo(archivo.contenido, archivo.nombre ?? d.nombreArchivo);
  }

  if (documentos.length === 0) {
    return (
      <Card>
        <EmptyState icon="file-text" title="Sin documentos">El contrato digitalizado se adjunta al registrar la firma.</EmptyState>
      </Card>
    );
  }
  return (
    <Card title="Contrato digitalizado">
      {error && <Alert tone="danger">{error.message}</Alert>}
      <ul className="ficha__documentos">
        {documentos.map((d) => (
          <li key={d.id} className="ficha__documento">
            <div className="ficha__documento-datos">
              <span className="body">{d.nombreArchivo}</span>
              <span className="caption v-muted">{tamanoLegible(d.tamanoBytes)}</span>
              <Actor name={d.usuario.nombre} action="lo adjuntó" at={fechaHora(d.fechaCarga)} size="sm" />
            </div>
            <IconButton icon="download" label={`Descargar ${d.nombreArchivo}`} onClick={() => void descargar(d)} />
          </li>
        ))}
      </ul>
    </Card>
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
    case 'CONTRATADO': return { titulo: 'Contrato firmado', accion: 'registró la firma del contrato' };
    case 'CONFIRMADO': return { titulo: 'Evento confirmado', accion: 'confirmó el evento' };
    case 'CANCELADO': return { titulo: 'Evento cancelado', accion: 'canceló el evento' };
    case 'LIBERADA': return { titulo: 'Pre-reserva liberada', accion: 'liberó la fecha' };
    default: return { titulo: `Pasó a ${ESTADOS[ESTADO_POR_CODIGO[e.estadoNuevo]].label}`, accion: 'cambió el estado' };
  }
}

/** Nombre en pantalla de cada dato del registro de modificaciones. */
const CAMPOS: Record<string, string> = {
  nombre: 'Nombre del evento',
  tipo_evento: 'Tipo',
  cantidad_invitados: 'Invitados',
  invitados_definitivos: 'Invitados definitivos',
  planner: 'Planner',
  hora_inicio: 'Hora de inicio',
  unidad: 'Salón, fecha y turno',
  observaciones_internas: 'Observaciones internas',
  'cliente.nombre': 'Cliente',
  'cliente.documento': 'Documento del cliente',
  'cliente.telefono': 'Teléfono del cliente',
  'cliente.email': 'Correo del cliente',
  contacto: 'Contacto',
};

/** Valores largos (p. ej. la descripción de un servicio) se recortan en el historial. */
function corto(valor: string | undefined): string {
  if (valor === undefined) return 'sin dato';
  return valor.length <= 80 ? valor : `${valor.slice(0, 79)}…`;
}

function describirModificacion(m: Modificacion): string {
  if (m.campo.startsWith('servicio.')) {
    const categoria = m.campo.slice('servicio.'.length);
    if (!m.valorAnterior) return `Cargó ${categoria}: ${corto(m.valorNuevo)}`;
    if (!m.valorNuevo) return `Vació ${categoria}`;
    return `${categoria}: ${corto(m.valorAnterior)} → ${corto(m.valorNuevo)}`;
  }
  const campo = CAMPOS[m.campo] ?? m.campo;
  if (m.campo === 'contacto') {
    if (!m.valorAnterior) return `Agregó el contacto ${m.valorNuevo}`;
    if (!m.valorNuevo) return `Quitó el contacto ${m.valorAnterior}`;
  }
  if (m.campo === 'observaciones_internas') return m.valorNuevo ? 'Cambió las observaciones internas' : 'Borró las observaciones internas';
  return `${campo}: ${m.valorAnterior ?? 'sin dato'} → ${m.valorNuevo ?? 'sin dato'}`;
}

/** Los cambios de datos de un mismo guardado (misma persona y momento) van juntos en una entrada. */
function agrupar(historial: EntradaHistorial[]): (CambioDeEstado | CambioDeUnidad | Modificacion[])[] {
  const grupos: (CambioDeEstado | CambioDeUnidad | Modificacion[])[] = [];
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
    if (g.tipo === 'REPROGRAMACION') {
      return {
        title: 'Evento reprogramado',
        detail: `${g.valorAnterior} → ${g.valorNuevo}.${g.observacion ? ` Motivo: ${g.observacion}.` : ''}`,
        actor: g.usuario.nombre,
        action: 'reprogramó el evento',
        at: fechaHora(g.fechaHora),
        icon: 'calendar-clock',
        tone: 'info',
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
