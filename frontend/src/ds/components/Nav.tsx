import type { ReactNode } from 'react';
import type { IconName } from '../tipos';
import { cx } from '../util';
import { Icon } from './Icon';

export interface NavItem {
  id: string;
  label: string;
  icon: IconName;
  group?: string;
  badge?: number | string;
}

export interface NavProps {
  items: NavItem[];
  value?: string;
  onSelect?: (id: string) => void;
  /** sidebar en escritorio; bottom en teléfono y tableta (cinco ítems como máximo). */
  layout?: 'sidebar' | 'bottom';
  logo?: ReactNode;
  footer?: ReactNode;
  label?: string;
  className?: string;
}

export function Nav({ items, value, onSelect, layout = 'sidebar', logo, footer, label = 'Principal', className }: NavProps) {
  if (layout === 'bottom') {
    return (
      <nav aria-label={label} className={cx('v-nav', 'v-nav--bottom', className)}>
        {items.map((it) => {
          const activo = it.id === value;
          return (
            <button key={it.id} type="button" className={cx('v-nav__item', 'caption', activo && 'is-on')} aria-current={activo ? 'page' : undefined} onClick={() => onSelect?.(it.id)}>
              <span className="v-nav__ico">
                <Icon name={it.icon} size={22} />
                {it.badge != null && <span className="v-nav__badge caption">{it.badge}</span>}
              </span>
              {it.label}
            </button>
          );
        })}
      </nav>
    );
  }

  const grupos: { nombre: string; items: NavItem[] }[] = [];
  for (const it of items) {
    const nombre = it.group ?? '';
    let grupo = grupos.find((g) => g.nombre === nombre);
    if (!grupo) {
      grupo = { nombre, items: [] };
      grupos.push(grupo);
    }
    grupo.items.push(it);
  }

  return (
    <nav aria-label={label} className={cx('v-nav', 'v-nav--sidebar', className)}>
      {logo && <div className="v-nav__logo">{logo}</div>}
      <div className="v-nav__scroll">
        {grupos.map((g) => (
          <div key={g.nombre || '_'} className="v-nav__group">
            {g.nombre && <p className="overline v-muted v-nav__title">{g.nombre}</p>}
            {g.items.map((it) => {
              const activo = it.id === value;
              return (
                <button key={it.id} type="button" className={cx('v-nav__item', 'label', activo && 'is-on')} aria-current={activo ? 'page' : undefined} onClick={() => onSelect?.(it.id)}>
                  <Icon name={it.icon} size={20} />
                  <span className="v-nav__text">{it.label}</span>
                  {it.badge != null && <span className="v-nav__count caption">{it.badge}</span>}
                </button>
              );
            })}
          </div>
        ))}
      </div>
      {footer && <div className="v-nav__foot">{footer}</div>}
    </nav>
  );
}
