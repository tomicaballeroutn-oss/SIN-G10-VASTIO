import type { SalonId, StockStatus } from '../tipos';
import { cx } from '../util';
import { Actor, Card } from './datos';
import { Badge } from './feedback';
import { SalonTag, StatusChip } from './estado';
import { Icon } from './Icon';

export interface StockLevelProps {
  name: string;
  presentacion?: string;
  ubicacion?: string;
  cantidad: number;
  /** Lo que la agenda ya tiene comprometido; se marca sobre la barra. */
  comprometido?: number;
  unidad?: string;
  unidadUno?: string;
  status?: StockStatus;
  className?: string;
}

export function StockLevel({ name, presentacion, ubicacion, cantidad, comprometido = 0, unidad = 'cajones', unidadUno = 'cajón', status, className }: StockLevelProps) {
  const enUnidad = (n: number) => (n === 1 ? unidadUno : unidad);
  const estado: StockStatus = status ?? (cantidad <= 0 ? 'sin-stock' : cantidad < comprometido ? 'bajo' : 'ok');
  const escala = Math.max(cantidad, comprometido, 1) * 1.15;
  const relleno = Math.max(0, Math.min(100, (cantidad / escala) * 100));
  const marca = Math.min(100, (comprometido / escala) * 100);
  return (
    <div className={cx('v-stock', className)}>
      <div className="v-stock__top">
        <div>
          <p className="label">{name}</p>
          <p className="body-sm v-muted">{[presentacion, ubicacion].filter(Boolean).join(' · ')}</p>
        </div>
        <StatusChip status={estado} size="sm" />
      </div>
      <p className="v-stock__qty">
        <span className="numeral">{cantidad}</span>
        <span className="body-sm v-muted"> {enUnidad(cantidad)}</span>
      </p>
      <div
        className={cx('v-meter', `v-meter--${estado}`)}
        role="meter"
        aria-valuemin={0}
        aria-valuemax={Math.round(escala)}
        aria-valuenow={cantidad}
        aria-label={`Existencias de ${name}`}
      >
        <span className="v-meter__fill" style={{ width: `${relleno}%` }} />
        {comprometido > 0 && <span className="v-meter__mark" style={{ left: `${marca}%` }} />}
      </div>
      {comprometido > 0 && (
        <p className="body-sm v-muted">
          <span className="numeral-sm">{comprometido}</span> {enUnidad(comprometido)}
          {comprometido === 1 ? ' comprometido' : ' comprometidos'} por la agenda
        </p>
      )}
    </div>
  );
}

const MOVIMIENTOS = {
  entrega: { label: 'Entrega a barra', icon: 'arrow-right-left', tone: 'info' },
  devolucion: { label: 'Devolución', icon: 'undo-2', tone: 'success' },
  'retiro-adicional': { label: 'Retiro adicional', icon: 'plus', tone: 'warning' },
  ingreso: { label: 'Ingreso al depósito', icon: 'truck', tone: 'brand' },
} as const;

export interface MovementCardProps {
  tipo: keyof typeof MOVIMIENTOS;
  evento?: string;
  salon?: SalonId;
  desde?: string;
  hasta?: string;
  items: { nombre: string; cantidad: number; unidad?: string; unidadUno?: string }[];
  nota?: string;
  actor: string;
  at?: string;
  className?: string;
}

/** Movimiento de mercadería. No se edita: se corrige con un ajuste. */
export function MovementCard({ tipo, evento, salon, desde, hasta, items, nota, actor, at, className }: MovementCardProps) {
  const mov = MOVIMIENTOS[tipo] ?? MOVIMIENTOS.entrega;
  return (
    <Card className={cx('v-move', className)}>
      <div className="v-move__top">
        <Badge tone={mov.tone} icon={mov.icon}>{mov.label}</Badge>
        {salon && <SalonTag salon={salon} />}
      </div>
      {evento && <p className="h4">{evento}</p>}
      {(desde || hasta) && (
        <p className="v-move__route label">
          {desde}
          <Icon name="arrow-right" size={16} className="v-muted" />
          {hasta}
        </p>
      )}
      <ul className="v-move__items">
        {items.map((it, i) => (
          <li key={i} className="body-sm">
            <span>{it.nombre}</span>
            <span className="numeral-sm">{it.cantidad} {it.cantidad === 1 ? (it.unidadUno ?? 'cajón') : (it.unidad ?? 'cajones')}</span>
          </li>
        ))}
      </ul>
      {nota && <p className="body-sm v-muted">{nota}</p>}
      <div className="v-move__foot">
        <Actor name={actor} at={at} action="Registró" size="sm" />
        <span className="caption v-muted v-move__lock">
          <Icon name="lock" size={14} />
          No editable · se corrige con un ajuste
        </span>
      </div>
    </Card>
  );
}
