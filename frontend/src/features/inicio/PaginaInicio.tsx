import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { agenda, fechaLarga, hoyEnCordoba, sumarMeses, type Agenda, type UnidadOcupada } from '../../api/agenda';
import type { Rol, UsuarioSesion } from '../../api/cliente';
import { fichas, type EventoResumen } from '../../api/eventos';
import { ESTADO_STOCK, stock, type EstadoStock } from '../../api/stock';
import { useDatos } from '../../api/useDatos';
import {
  Atajo, Button, Card, ESTADO_POR_CODIGO, IconButton, MiniBarras, MiniCalendar, SalonTag, StatusChip, StockLevel, Tabs,
  cantidadLegible, type EventStatus,
} from '../../ds';
import { Cargando } from '../comun/Cargando';
import { fechaCorta } from '../comun/formato';
import { ListaNotificaciones } from '../notificaciones/ListaNotificaciones';
import { useSesion } from '../sesion/contexto';
import {
  alcanceDe, diasOcupados, eventosDelDia, libresDelDia, mesesDe, resumenSemana, semanaDe, sumarDias, tareasDe, tieneTareas,
  type Alcance,
} from './tablero';
import './inicio.css';

/** Perfiles que consultan el stock del depósito madre (la encargada de barra no tiene Inicio). */
const VEN_STOCK: readonly Rol[] = ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'];
/** Días que se recorren con las solapas: hoy y los tres siguientes. */
const DIAS_PROXIMOS = 4;
const MAXIMO_TAREAS = 5;
const MAXIMO_BEBIDAS = 4;
const DIAS_SEMANA = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'];

/**
 * UI-04 · Inicio: pantalla de bienvenida con recordatorios y atajos al resto de la app. No tiene acciones propias:
 * cada bloque lleva a la pantalla donde se hace la tarea. Dirección, Coordinación, Administración y Compras ven el
 * complejo; la vendedora, sus eventos; la planner, los que tiene asignados.
 */
export function PaginaInicio() {
  const { usuario } = useSesion();
  return usuario ? <Tablero usuario={usuario} /> : null;
}

function Tablero({ usuario }: { usuario: UsuarioSesion }) {
  const navegar = useNavigate();
  const hoy = hoyEnCordoba();
  const alcance = useMemo(() => alcanceDe(usuario), [usuario]);
  const semana = useMemo(() => semanaDe(hoy), [hoy]);
  const dias = useMemo(() => Array.from({ length: DIAS_PROXIMOS }, (_, i) => sumarDias(hoy, i)), [hoy]);
  const meses = mesesDe([...semana, ...dias]).join(',');

  const cargarAgendas = useCallback(() => Promise.all(meses.split(',').map((m) => agenda.mes(m))), [meses]);
  const agendas = useDatos(cargarAgendas);
  const eventos = useDatos(fichas.proximos);

  const verStock = usuario.roles.some((r) => VEN_STOCK.includes(r));
  const verTareas = tieneTareas(usuario.roles);

  return (
    <section className="pantalla inicio">
      <header className="inicio__cabeza">
        <div className="pantalla__titulos">
          <p className="overline v-muted">Inicio · {fechaLarga(hoy)}</p>
          <h1>Hola, {usuario.nombreCompleto.split(' ')[0]}</h1>
        </div>
        <Button variant="outline" icon="calendar-days" onClick={() => navegar('/agenda')}>Ir a la agenda</Button>
      </header>

      <Cargando datos={agendas}>
        {(lista) => {
          const unidades = lista.flatMap((a) => a.unidades);
          const r = resumenSemana(unidades, semana, alcance);
          return (
            <>
              <Card
                tone="deep"
                className="inicio__semana"
                notch={<IconButton icon="arrow-right" label="Ver la semana en la agenda" variant="solid" onClick={() => navegar(`/agenda?mes=${hoy.slice(0, 7)}&dia=${hoy}`)} />}
              >
                <p className="overline v-muted">{alcance.complejo ? 'Esta semana' : 'Tus eventos esta semana'}</p>
                <div className="inicio__semana-fila">
                  <p className="inicio__numero"><span className="stat">{r.total}</span><span className="body-sm v-muted">{r.total === 1 ? 'evento' : 'eventos'}</span></p>
                  <MiniBarras values={r.porDia} label={`Eventos por día: ${DIAS_SEMANA.map((d, i) => `${d} ${r.porDia[i]}`).join(', ')}`} />
                </div>
                <p className="body-sm v-muted inicio__semana-resumen">
                  {r.confirmados} {r.confirmados === 1 ? 'confirmado' : 'confirmados'} · {r.contratados} {r.contratados === 1 ? 'contratado' : 'contratados'} · {r.porCerrar} por cerrar
                </p>
              </Card>
              <Numero etiqueta="Confirmados" valor={r.confirmados} unidad={r.confirmados === 1 ? 'evento' : 'eventos'} estado="confirmado" />
              <Numero etiqueta="Contratados" valor={r.contratados} unidad={r.contratadosSinPlanner > 0 ? `${r.contratadosSinPlanner} sin planner` : r.contratados === 1 ? 'evento' : 'eventos'} estado="contratado" />
              <Numero etiqueta="Señados y pre-reservas" valor={r.porCerrar} unidad="por cerrar" estado="senado" />
              <ProximosDias dias={dias} hoy={hoy} agendaDe={(dia) => lista.find((a) => a.mes === dia.slice(0, 7))} unidades={unidades} eventos={eventos.datos} alcance={alcance} />
            </>
          );
        }}
      </Cargando>

      <Calendario hoy={hoy} />

      <div className="inicio__fila">
        {verTareas && (
          <Card eyebrow="Para hacer" title="Te toca a vos">
            <Cargando datos={eventos}>
              {(lista) => <Tareas tareas={tareasDe(lista, alcance)} />}
            </Cargando>
          </Card>
        )}
        <Card eyebrow="Novedades" title="Notificaciones" notch={<IconButton icon="arrow-right" label="Ver todas las notificaciones" variant="solid" onClick={() => navegar('/notificaciones')} />}>
          <ListaNotificaciones limite={4} />
        </Card>
        {verStock && <DepositoMadre />}
      </div>
    </section>
  );
}

function Numero({ etiqueta, valor, unidad, estado }: { etiqueta: string; valor: number; unidad: string; estado: EventStatus }) {
  return (
    <Card className="inicio__kpi">
      <p className="overline v-muted">{etiqueta}</p>
      <div className="inicio__kpi-fila">
        <p className="inicio__numero"><span className="stat">{valor}</span><span className="body-sm v-muted">{unidad}</span></p>
        <StatusChip status={estado} size="sm" />
      </div>
    </Card>
  );
}

/** «Hoy», «Mañana», «Vie 24»… */
function etiquetaDia(dia: string, hoy: string): string {
  if (dia === hoy) return 'Hoy';
  if (dia === sumarDias(hoy, 1)) return 'Mañana';
  const corta = fechaCorta(dia).split(' ').slice(0, 2).join(' ');
  return corta.charAt(0).toUpperCase() + corta.slice(1);
}

function ProximosDias({ dias, hoy, agendaDe, unidades, eventos, alcance }: {
  dias: string[];
  hoy: string;
  agendaDe: (dia: string) => Agenda | undefined;
  unidades: UnidadOcupada[];
  eventos: EventoResumen[] | undefined;
  alcance: Alcance;
}) {
  const navegar = useNavigate();
  const [dia, setDia] = useState(dias[0]);
  const agendaDelDia = agendaDe(dia);
  const libres = agendaDelDia ? libresDelDia(agendaDelDia, unidades, dia) : null;
  const delDia = eventos ? eventosDelDia(eventos, dia, alcance) : [];
  const titulo = fechaLarga(dia);

  return (
    <div className="inicio__dias">
      <Tabs variant="folder" label="Próximos días" items={dias.map((d) => ({ id: d, label: etiquetaDia(d, hoy) }))} value={dia} onChange={setDia} />
      <Card>
        <div className="inicio__dia-cabeza">
          <h2 className="h4">{titulo.charAt(0).toUpperCase() + titulo.slice(1)}</h2>
          {libres && (
            <span className="body-sm v-muted">
              {libres.libres === libres.total ? 'Todo libre' : libres.libres === 0 ? 'Sin lugares libres' : `${libres.libres} de ${libres.total} libres`}
            </span>
          )}
        </div>
        {eventos === undefined ? (
          <p className="body-sm v-muted" role="status">Cargando…</p>
        ) : delDia.length === 0 ? (
          <p className="body-sm v-muted">{alcance.complejo ? 'No hay eventos este día.' : 'No tenés eventos este día.'}</p>
        ) : (
          <ul className="inicio__eventos">
            {delDia.map((e) => (
              <li key={e.id}>
                <button type="button" className="inicio__evento" onClick={() => navegar(`/eventos/${e.id}`)}>
                  <span className="inicio__evento-lugar">
                    <SalonTag salon={e.salon.codigo} label={e.salon.nombre} variant="dot" />
                    <span className="caption v-muted">{e.turno.nombre}</span>
                  </span>
                  <span className="label">{e.nombre}</span>
                  <span className="body-sm v-muted">{detalleEvento(e)}</span>
                  <StatusChip status={ESTADO_POR_CODIGO[e.estado]} size="sm" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

/** «160 invitados · Planner: Julieta Ruiz», «sin planner». */
function detalleEvento(e: EventoResumen): string {
  const partes = [];
  if (e.cantidadInvitados != null) partes.push(`${e.cantidadInvitados} invitados`);
  partes.push(e.planner ? `Planner: ${e.planner.nombre}` : 'sin planner');
  return partes.join(' · ');
}

/** El mes de un vistazo; tocar un día abre la agenda en ese día. */
function Calendario({ hoy }: { hoy: string }) {
  const navegar = useNavigate();
  const [mes, setMes] = useState(hoy.slice(0, 7));
  const cargar = useCallback(() => agenda.mes(mes), [mes]);
  const datos = useDatos(cargar);
  const [anio, numero] = mes.split('-').map(Number);

  return (
    <Card className="inicio__calendario">
      <MiniCalendar
        year={anio}
        month={numero - 1}
        days={datos.datos?.mes === mes ? diasOcupados(datos.datos.unidades) : {}}
        today={hoy}
        onSelectDay={(dia) => navegar(`/agenda?mes=${dia.slice(0, 7)}&dia=${dia}`)}
        onPrev={() => setMes((m) => sumarMeses(m, -1))}
        onNext={() => setMes((m) => sumarMeses(m, 1))}
      />
    </Card>
  );
}

function Tareas({ tareas }: { tareas: ReturnType<typeof tareasDe> }) {
  const navegar = useNavigate();
  if (tareas.length === 0) return <p className="body-sm v-muted">No tenés nada pendiente por ahora.</p>;
  return (
    <div className="inicio__tareas">
      {tareas.slice(0, MAXIMO_TAREAS).map((t) => (
        <Atajo key={t.clave} icon={t.icono} tone={t.tono} title={t.titulo} subtitle={t.detalle} onClick={() => navegar(`/eventos/${t.eventoId}`)} />
      ))}
      {tareas.length > MAXIMO_TAREAS && (
        <Button variant="text" size="sm" onClick={() => navegar('/eventos')}>Ver los otros {tareas.length - MAXIMO_TAREAS} en Eventos</Button>
      )}
    </div>
  );
}

const ORDEN_FALTANTES: EstadoStock[] = ['NEGATIVO', 'SIN_STOCK', 'BAJO'];

/** Existencias del depósito madre (la única ubicación de tipo depósito). */
async function cargarDepositoMadre() {
  const madre = (await stock.ubicaciones()).find((u) => u.ubicacion.tipo === 'DEPOSITO');
  return madre ? stock.existencias(madre.ubicacion.id) : null;
}

/** Solo lo que falta o está por debajo del mínimo; el detalle completo está en Existencias. */
function DepositoMadre() {
  const navegar = useNavigate();
  const datos = useDatos(cargarDepositoMadre);
  return (
    <Card
      eyebrow="Bebidas"
      title={datos.datos?.ubicacion?.nombre ?? 'Depósito madre'}
      footer={<Button variant="text" size="sm" icon="package" onClick={() => navegar('/existencias')}>Ver existencias</Button>}
    >
      <Cargando datos={datos}>
        {(existencias) => {
          const faltantes = (existencias?.renglones ?? [])
            .filter((r) => r.estado && r.estado !== 'OK')
            .sort((x, y) => ORDEN_FALTANTES.indexOf(x.estado!) - ORDEN_FALTANTES.indexOf(y.estado!));
          if (faltantes.length === 0) return <p className="body-sm v-muted">Sin faltantes en el depósito madre.</p>;
          return (
            <div className="inicio__bebidas">
              {faltantes.slice(0, MAXIMO_BEBIDAS).map((r) => (
                <StockLevel
                  key={r.bebida.id}
                  name={r.bebida.nombre}
                  presentacion={r.bebida.presentacion}
                  cantidad={r.cantidad}
                  cantidadTexto={cantidadLegible(r.cantidad, r.bebida.unidadesPorBulto, r.bebida.unidad)}
                  status={ESTADO_STOCK[r.estado!]}
                />
              ))}
              {faltantes.length > MAXIMO_BEBIDAS && (
                <p className="body-sm v-muted">Y {faltantes.length - MAXIMO_BEBIDAS} más en Existencias.</p>
              )}
            </div>
          );
        }}
      </Cargando>
    </Card>
  );
}
