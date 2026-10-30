import { ESTADOS, ORDEN_SALONES, SALONES } from '../estados';
import type { EventStatus, SalonId, TurnoId } from '../tipos';
import { cx } from '../util';
import { IconButton } from './Button';
import { Actor, Card } from './datos';
import { Badge } from './feedback';
import { SalonTag, StatusChip } from './estado';
import { Icon } from './Icon';

const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
const DIAS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
const DIAS_LARGOS = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'];
const TURNOS: TurnoId[] = ['mediodia', 'noche'];
/** Lo que puede verse en un punto de la agenda, de libre a cerrado. Liberada y cancelado se ven como disponible. */
const LEYENDA: EventStatus[] = ['disponible', 'prereserva', 'senado', 'contratado', 'confirmado', 'en-curso', 'realizado', 'cerrado', 'bloqueado'];

const dosDigitos = (n: number) => (n < 10 ? '0' : '') + n;

export interface AgendaEvent {
  /** yyyy-mm-dd: fecha operativa (día en que empieza la jornada). */
  date: string;
  salon: SalonId;
  turno: TurnoId;
  /** Liberada y cancelado no ocupan la unidad: el día se ve disponible. */
  status: Exclude<EventStatus, 'cancelado' | 'liberada' | 'disponible'>;
}

export interface AgendaGridProps {
  year: number;
  /** 0 = enero. */
  month: number;
  events?: AgendaEvent[];
  today?: string;
  selected?: string;
  onSelectDay?: (isoDate: string) => void;
  onPrev?: () => void;
  onNext?: () => void;
  /**
   * Nombres de los salones (se editan en Parámetros). El color y el orden de las columnas salen del código.
   * Sin ellos, los nombres por defecto.
   */
  salones?: { codigo: SalonId; nombre: string }[];
  /**
   * Filtro de la pantalla: las unidades para las que devuelve false se ven atenuadas, no ocultas,
   * para que una unidad ocupada nunca parezca disponible.
   */
  resaltar?: (unidad: { salon: SalonId; turno: TurnoId; status: EventStatus }) => boolean;
  /**
   * Solo dice qué unidades tienen evento, sin su estado (vista de cocina): todos los puntos van llenos y se anuncian
   * «con evento».
   */
  soloOcupacion?: boolean;
  className?: string;
}

/**
 * Mes de la agenda: cada día muestra las seis unidades como puntos (columna = salón, fila = mediodía / noche).
 * Arriba, qué salón es cada columna. Qué significa cada relleno va aparte, en AgendaLeyenda.
 */
export function AgendaGrid({
  year, month, events = [], today, selected, onSelectDay, onPrev, onNext, salones, resaltar, soloOcupacion, className,
}: AgendaGridProps) {
  const primero = new Date(year, month, 1);
  const blancos = (primero.getDay() + 6) % 7;
  const total = new Date(year, month + 1, 0).getDate();
  const porFecha = new Map<string, AgendaEvent[]>();
  for (const e of events) porFecha.set(e.date, [...(porFecha.get(e.date) ?? []), e]);

  const celdas = [];
  for (let i = 0; i < blancos; i++) celdas.push(<div key={`b${i}`} className="v-day v-day--blank" aria-hidden />);
  for (let d = 1; d <= total; d++) {
    const iso = `${year}-${dosDigitos(month + 1)}-${dosDigitos(d)}`;
    const delDia = porFecha.get(iso) ?? [];
    const pips = [];
    const descripcion: string[] = [];
    for (const turno of TURNOS) {
      for (const salon of ORDEN_SALONES) {
        const ev = delDia.find((e) => e.salon === salon && e.turno === turno);
        const estado: EventStatus = ev ? (soloOcupacion ? 'confirmado' : ev.status) : 'disponible';
        const coincide = resaltar ? resaltar({ salon, turno, status: estado }) : false;
        pips.push(<span key={turno + salon} className={cx('v-pip', `v-pip--${salon}`, `v-pip--${estado}`, resaltar && (coincide ? 'is-match' : 'is-dim'))} />);
        if (ev && estado !== 'disponible') {
          descripcion.push(`${SALONES[salon]} ${turno === 'noche' ? 'noche' : 'mediodía'}: ${soloOcupacion ? 'con evento' : ESTADOS[estado]?.label ?? estado}`);
        }
      }
    }
    const diaSemana = DIAS_LARGOS[(new Date(year, month, d).getDay() + 6) % 7];
    celdas.push(
      <button
        key={iso}
        type="button"
        className={cx('v-day', selected === iso && 'is-selected', today === iso && 'is-today')}
        aria-pressed={selected === iso}
        aria-label={`${diaSemana} ${d} de ${MESES[month].toLowerCase()}${descripcion.length ? '. ' + descripcion.join('; ') : '. Todo disponible'}`}
        onClick={() => onSelectDay?.(iso)}
      >
        <span className="v-day__num numeral-sm">{d}</span>
        <span className="v-day__pips" aria-hidden>{pips}</span>
      </button>,
    );
  }

  const nombre = (s: SalonId) => salones?.find((x) => x.codigo === s)?.nombre ?? SALONES[s];

  return (
    <div className={cx('v-agenda', className)}>
      <div className="v-agenda__head">
        {onPrev && <IconButton icon="chevron-left" label="Mes anterior" onClick={onPrev} />}
        <h2 className="h2 v-agenda__title">{MESES[month]} {year}</h2>
        {onNext && <IconButton icon="chevron-right" label="Mes siguiente" onClick={onNext} />}
      </div>
      {/* Qué salón es cada columna de puntos, en el mismo orden en que aparecen en cada día. */}
      <div className="v-agenda__salons">
        <span className="caption v-muted">Cada día, de izquierda a derecha</span>
        <ol className="v-agenda__salons-lista">
          {ORDEN_SALONES.map((s) => <li key={s}><SalonTag salon={s} label={nombre(s)} /></li>)}
        </ol>
      </div>
      <div className="v-agenda__weekdays label" aria-hidden>
        {DIAS.map((n) => <span key={n}>{n}</span>)}
      </div>
      <div className="v-agenda__grid">{celdas}</div>
    </div>
  );
}

export interface AgendaLeyendaProps {
  /** Abierta al cargar. Conviene abierta en escritorio y cerrada en el teléfono. */
  defaultOpen?: boolean;
  className?: string;
}

/** Machete de la agenda: qué estado significa cada relleno de punto. Se abre y se cierra. */
export function AgendaLeyenda({ defaultOpen = false, className }: AgendaLeyendaProps) {
  return (
    <details className={cx('v-agenda-leyenda', className)} open={defaultOpen}>
      <summary className="v-agenda-leyenda__resumen">
        <span className="h4">Qué significa cada punto</span>
        <Icon name="chevron-down" size={20} className="v-agenda-leyenda__chevron" />
      </summary>
      <ul className="v-agenda-leyenda__lista body-sm">
        {LEYENDA.map((s) => (
          <li key={s} className="v-legend__item">
            <span className={`v-pip v-pip--club v-pip--${s}`} aria-hidden />
            {ESTADOS[s].label}
          </li>
        ))}
      </ul>
      <p className="caption v-muted">Una fecha cancelada o liberada vuelve a verse disponible.</p>
    </details>
  );
}

/** Ocupación de un día en el MiniCalendar. Los libres no llevan marca. */
export type DiaOcupado = 'reservado' | 'prereserva';

export interface MiniCalendarProps {
  year: number;
  /** 0 = enero. */
  month: number;
  /**
   * yyyy-mm-dd → `reservado` (al menos una unidad señada, contratada, confirmada o posterior) o
   * `prereserva` (solo pre-reservas). Bloqueos y cancelados no marcan.
   */
  days?: Record<string, DiaOcupado>;
  today?: string;
  selected?: string;
  onSelectDay?: (isoDate: string) => void;
  onPrev?: () => void;
  onNext?: () => void;
  legend?: boolean;
  className?: string;
}

const INICIALES_DIAS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

/** v2: el mes de un vistazo para el inicio y como selector rápido de fecha. El detalle por salón y turno está en AgendaGrid. */
export function MiniCalendar({ year, month, days = {}, today, selected, onSelectDay, onPrev, onNext, legend = true, className }: MiniCalendarProps) {
  const blancos = (new Date(year, month, 1).getDay() + 6) % 7;
  const total = new Date(year, month + 1, 0).getDate();
  const titulo = `${MESES[month]} ${year}`;

  const celdas = [];
  for (let i = 0; i < blancos; i++) celdas.push(<span key={`b${i}`} aria-hidden />);
  for (let d = 1; d <= total; d++) {
    const iso = `${year}-${dosDigitos(month + 1)}-${dosDigitos(d)}`;
    const ocupado = days[iso];
    const estado = ocupado === 'reservado' ? 'con reserva' : ocupado === 'prereserva' ? 'con pre-reserva' : 'libre';
    celdas.push(
      <button
        key={iso}
        type="button"
        className={cx('v-minical__day', 'numeral-sm', ocupado && `is-${ocupado}`, today === iso && 'is-today', selected === iso && 'is-selected')}
        aria-label={`${d} de ${MESES[month].toLowerCase()}: ${estado}`}
        aria-current={today === iso ? 'date' : undefined}
        aria-pressed={onSelectDay ? selected === iso : undefined}
        onClick={onSelectDay ? () => onSelectDay(iso) : undefined}
      >
        {d}
      </button>,
    );
  }

  return (
    <div className={cx('v-minical', className)}>
      <div className="v-minical__head">
        <p className="label">{titulo}</p>
        {(onPrev || onNext) && (
          <div className="v-minical__nav">
            <IconButton icon="chevron-left" label="Mes anterior" variant="text" size="sm" onClick={onPrev} />
            <IconButton icon="chevron-right" label="Mes siguiente" variant="text" size="sm" onClick={onNext} />
          </div>
        )}
      </div>
      <div className="v-minical__grid" role="group" aria-label={titulo}>
        {INICIALES_DIAS.map((n, i) => <span key={`d${i}`} className="caption v-muted v-minical__wd" aria-hidden>{n}</span>)}
        {celdas}
      </div>
      {legend && (
        <div className="v-minical__legend caption v-muted">
          <span><i className="v-minical__key is-reservado" />Con reserva</span>
          <span><i className="v-minical__key is-prereserva" />Pre-reserva</span>
        </div>
      )}
    </div>
  );
}

export interface EventCardProps {
  salon: SalonId;
  status: EventStatus;
  tipo: string;
  title: string;
  fecha: string;
  turno: string;
  invitados?: number;
  vendedora?: string;
  planner?: string | null;
  onOpen?: (props: EventCardProps) => void;
  className?: string;
}

export function EventCard(props: EventCardProps) {
  const { salon, status, tipo, title, fecha, turno, invitados, vendedora, planner, onOpen, className } = props;
  const abrir = onOpen ? () => onOpen(props) : undefined;
  return (
    <Card interactive={!!abrir} onClick={abrir} className={cx('v-event', className)}>
      <div className="v-event__top">
        <SalonTag salon={salon} />
        <StatusChip status={status} />
      </div>
      <div>
        <p className="overline v-muted">{tipo}</p>
        <h3 className="h3">{title}</h3>
      </div>
      <dl className="v-event__meta body-sm">
        <div><Icon name="calendar" size={18} /><dt className="v-sr">Fecha</dt><dd>{fecha}</dd></div>
        <div><Icon name="clock" size={18} /><dt className="v-sr">Turno</dt><dd>{turno}</dd></div>
        {invitados != null && (
          <div><Icon name="users" size={18} /><dt className="v-sr">Invitados</dt><dd className="numeral-sm">{invitados} invitados</dd></div>
        )}
      </dl>
      <div className="v-event__foot">
        {vendedora && <Actor name={vendedora} action="Vendedora" size="sm" />}
        {planner ? <Actor name={planner} action="Planner" size="sm" /> : <Badge tone="warning" icon="user">Sin planner</Badge>}
      </div>
    </Card>
  );
}
