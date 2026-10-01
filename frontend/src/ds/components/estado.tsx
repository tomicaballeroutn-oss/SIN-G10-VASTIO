import { ESTADOS, SALONES } from '../estados';
import type { EventStatus, SalonId, StockStatus } from '../tipos';
import { cx } from '../util';
import { Icon } from './Icon';

export interface StatusChipProps {
  status: EventStatus | StockStatus;
  size?: 'md' | 'sm';
  label?: string;
  className?: string;
}

export function StatusChip({ status, size = 'md', label, className }: StatusChipProps) {
  const estado = ESTADOS[status] ?? ESTADOS.disponible;
  const sm = size === 'sm';
  return (
    <span className={cx('v-chip', 'label', `v-chip--${estado.tone}`, sm && 'v-chip--sm', className)}>
      <Icon name={estado.icon} size={sm ? 14 : 16} strokeWidth={2.25} />
      {label ?? estado.label}
    </span>
  );
}

export interface SalonTagProps {
  salon: SalonId;
  variant?: 'tint' | 'dot';
  /** Nombre a mostrar si el salón fue renombrado en Parámetros; por defecto, el nombre original. */
  label?: string;
  className?: string;
}

export function SalonTag({ salon, variant = 'tint', label, className }: SalonTagProps) {
  return (
    <span className={cx('v-salon', 'label', `v-salon--${variant}`, `v-salon--${salon}`, className)}>
      <span className="v-salon__dot" aria-hidden />
      {label ?? SALONES[salon] ?? salon}
    </span>
  );
}
