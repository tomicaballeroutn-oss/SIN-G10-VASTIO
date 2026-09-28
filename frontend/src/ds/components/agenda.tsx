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
/** Estados que se muestran en la leyenda de la agenda. */
const LEYENDA: EventStatus[] = ['prereserva', 'senado', 'contratado', 'confirmado', 'bloqueado'];

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
  legend?: boolean;
  className?: string;
}

/** Mes de la agenda: cada día muestra las seis unidades (columna = salón, fila = mediodía / noche). */
export function AgendaGrid({ year, month, events = [], today, selected, onSelectDay, onPrev, onNext, legend = true, className }: AgendaGridProps) {
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
        const estado: EventStatus = ev ? ev.status : 'disponible';
        pips.push(<span key={turno + salon} className={cx('v-pip', `v-pip--${salon}`, `v-pip--${estado}`)} />);
        if (ev && estado !== 'disponible') {
          descripcion.push(`${SALONES[salon]} ${turno === 'noche' ? 'noche' : 'mediodía'}: ${ESTADOS[estado]?.label ?? estado}`);
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

  return (
    <div className={cx('v-agenda', className)}>
      <div className="v-agenda__head">
        {onPrev && <IconButton icon="chevron-left" label="Mes anterior" onClick={onPrev} />}
        <h2 className="h2 v-agenda__title">{MESES[month]} {year}</h2>
        {onNext && <IconButton icon="chevron-right" label="Mes siguiente" onClick={onNext} />}
      </div>
      <div className="v-agenda__weekdays label" aria-hidden>
        {DIAS.map((n) => <span key={n}>{n}</span>)}
      </div>
      <div className="v-agenda__grid">{celdas}</div>
      {legend && (
        <div className="v-agenda__legend body-sm v-muted">
          <span className="v-legend__item"><Icon name="sun" size={16} />Fila superior: mediodía</span>
          <span className="v-legend__item"><Icon name="moon" size={16} />Fila inferior: noche</span>
          {LEYENDA.map((s) => (
            <span key={s} className="v-legend__item">
              <span className={`v-pip v-pip--club v-pip--${s}`} />
              {ESTADOS[s].label}
            </span>
          ))}
        </div>
      )}
      <div className="v-agenda__salons">
        {ORDEN_SALONES.map((s) => <SalonTag key={s} salon={s} />)}
      </div>
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
