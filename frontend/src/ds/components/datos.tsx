import { useState, type KeyboardEvent, type ReactNode, type SyntheticEvent, type JSX } from 'react';
import type { BadgeTone, EventStatus, IconName, TimelineTone } from '../tipos';
import { cx, iniciales } from '../util';
import { Badge } from './feedback';
import { StatusChip } from './estado';
import { Icon } from './Icon';

export interface CardProps {
  children?: ReactNode;
  eyebrow?: string;
  title?: string;
  subtitle?: string;
  actions?: ReactNode;
  footer?: ReactNode;
  interactive?: boolean;
  flush?: boolean;
  onClick?: (e: SyntheticEvent) => void;
  as?: keyof JSX.IntrinsicElements;
  className?: string;
}

export function Card({ children, eyebrow, title, subtitle, actions, footer, interactive, flush, onClick, as, className }: CardProps) {
  const Tag = (as ?? 'section') as 'section';
  const conCabecera = title || eyebrow || actions;
  return (
    <Tag
      className={cx('v-card', interactive && 'v-card--interactive', flush && 'v-card--flush', className)}
      onClick={onClick}
      tabIndex={interactive ? 0 : undefined}
      onKeyDown={interactive && onClick ? (e: KeyboardEvent) => { if (e.key === 'Enter') onClick(e); } : undefined}
    >
      {conCabecera && (
        <header className="v-card__head">
          <div className="v-card__titles">
            {eyebrow && <p className="overline v-muted">{eyebrow}</p>}
            {title && <h3 className="h3">{title}</h3>}
            {subtitle && <p className="body-sm v-muted">{subtitle}</p>}
          </div>
          {actions && <div className="v-card__actions">{actions}</div>}
        </header>
      )}
      <div className="v-card__body">{children}</div>
      {footer && <footer className="v-card__foot">{footer}</footer>}
    </Tag>
  );
}

export interface TableColumn<T> {
  key: string;
  header: string;
  numeric?: boolean;
  width?: string;
  render?: (row: T) => ReactNode;
}

export interface TableProps<T> {
  columns: TableColumn<T>[];
  rows: (T & { id?: string | number })[];
  caption?: string;
  dense?: boolean;
  onRowClick?: (row: T) => void;
  empty?: string;
  className?: string;
}

export function Table<T extends object>({ columns, rows, caption, dense, onRowClick, empty = 'Sin resultados', className }: TableProps<T>) {
  return (
    <div className={cx('v-table-wrap', className)}>
      <table className={cx('v-table', dense && 'v-table--dense')}>
        {caption && <caption className="v-sr">{caption}</caption>}
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} scope="col" className={cx('label', c.numeric && 'v-num')} style={c.width ? { width: c.width } : undefined}>{c.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length ? (
            rows.map((fila, i) => (
              <tr
                key={fila.id ?? i}
                className={onRowClick ? 'is-click' : undefined}
                tabIndex={onRowClick ? 0 : undefined}
                onClick={onRowClick ? () => onRowClick(fila) : undefined}
                onKeyDown={onRowClick ? (e) => { if (e.key === 'Enter') onRowClick(fila); } : undefined}
              >
                {columns.map((c) => (
                  <td key={c.key} className={c.numeric ? 'numeral-sm v-num' : 'body-sm'}>
                    {c.render ? c.render(fila) : (fila as Record<string, ReactNode>)[c.key]}
                  </td>
                ))}
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={columns.length} className="body-sm v-muted v-table__empty">{empty}</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

export interface TabItem {
  id: string;
  label: string;
  icon?: IconName;
  count?: number;
}

export interface TabsProps {
  items: TabItem[];
  value?: string;
  defaultValue?: string;
  onChange?: (id: string) => void;
  variant?: 'underline' | 'segmented';
  label?: string;
  className?: string;
}

export function Tabs({ items, value, defaultValue, onChange, variant = 'underline', label, className }: TabsProps) {
  const [interno, setInterno] = useState(defaultValue ?? items[0]?.id);
  const actual = value !== undefined ? value : interno;

  function elegir(id: string) {
    if (value === undefined) setInterno(id);
    onChange?.(id);
  }

  function alTeclear(e: KeyboardEvent<HTMLButtonElement>, i: number) {
    const n = items.length;
    const j = e.key === 'ArrowRight' ? (i + 1) % n : e.key === 'ArrowLeft' ? (i + n - 1) % n : -1;
    if (j < 0) return;
    e.preventDefault();
    elegir(items[j].id);
    (e.currentTarget.parentNode?.children[j] as HTMLElement | undefined)?.focus();
  }

  return (
    <div role="tablist" aria-label={label} className={cx('v-tabs', `v-tabs--${variant}`, className)}>
      {items.map((it, i) => {
        const activo = it.id === actual;
        return (
          <button
            key={it.id}
            role="tab"
            type="button"
            aria-selected={activo}
            tabIndex={activo ? 0 : -1}
            className={cx('v-tab', 'label', activo && 'is-on')}
            onClick={() => elegir(it.id)}
            onKeyDown={(e) => alTeclear(e, i)}
          >
            {it.icon && <Icon name={it.icon} size={18} />}
            {it.label}
            {it.count != null && <span className="v-tab__count caption">{it.count}</span>}
          </button>
        );
      })}
    </div>
  );
}

export interface ActorProps {
  name: string;
  action?: string;
  at?: string;
  size?: 'md' | 'sm';
  className?: string;
}

/** Quién y cuándo: «Lucía Ferreyra · apartó la fecha · hoy 14:32». */
export function Actor({ name, action, at, size = 'md', className }: ActorProps) {
  const meta = [action, at].filter(Boolean).join(' · ');
  return (
    <div className={cx('v-actor', `v-actor--${size}`, className)}>
      <span className="v-avatar label" aria-hidden>{iniciales(name)}</span>
      <div className="v-actor__text">
        <span className="label">{name}</span>
        {meta && <span className="body-sm v-muted">{meta}</span>}
      </div>
    </div>
  );
}

export interface StatProps {
  label: string;
  value: string | number;
  unit?: string;
  delta?: string;
  deltaTone?: BadgeTone;
  deltaIcon?: IconName;
  hint?: string;
  className?: string;
}

export function Stat({ label, value, unit, delta, deltaTone = 'neutral', deltaIcon, hint, className }: StatProps) {
  return (
    <div className={cx('v-stat', className)}>
      <p className="label v-muted">{label}</p>
      <p className="v-stat__row">
        <span className="stat">{value}</span>
        {unit && <span className="body-sm v-muted">{unit}</span>}
      </p>
      {delta && (
        <div>
          <Badge tone={deltaTone} icon={deltaIcon}>{delta}</Badge>
        </div>
      )}
      {hint && <p className="body-sm v-muted">{hint}</p>}
    </div>
  );
}

export interface TimelineItem {
  title: string;
  from?: EventStatus;
  to?: EventStatus;
  detail?: string;
  actor?: string;
  action?: string;
  at?: string;
  icon?: IconName;
  tone?: TimelineTone;
}

export function Timeline({ items, className }: { items: TimelineItem[]; className?: string }) {
  return (
    <ol className={cx('v-timeline', className)}>
      {items.map((it, i) => (
        <li key={i} className="v-tl__item">
          <span className={cx('v-tl__dot', `v-tl__dot--${it.tone ?? 'neutral'}`)} aria-hidden>
            <Icon name={it.icon ?? 'circle-check'} size={16} strokeWidth={2.25} />
          </span>
          <div className="v-tl__body">
            <p className="label">{it.title}</p>
            {(it.from || it.to) && (
              <p className="v-tl__flow">
                {it.from && <StatusChip status={it.from} size="sm" />}
                {it.from && it.to && <Icon name="arrow-right" size={14} className="v-muted" />}
                {it.to && <StatusChip status={it.to} size="sm" />}
              </p>
            )}
            {it.detail && <p className="body-sm v-muted">{it.detail}</p>}
            {it.actor && <Actor name={it.actor} at={it.at} action={it.action} size="sm" />}
          </div>
        </li>
      ))}
    </ol>
  );
}
