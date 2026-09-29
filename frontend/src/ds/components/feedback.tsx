import { useEffect, useRef, type ReactNode } from 'react';
import type { BadgeTone, IconName } from '../tipos';
import { cx, useFieldId } from '../util';
import { IconButton } from './Button';
import { Icon } from './Icon';

type AlertTone = 'info' | 'success' | 'warning' | 'danger';

const ICONO_DE_TONO: Record<AlertTone, IconName> = {
  success: 'circle-check',
  warning: 'triangle-alert',
  danger: 'circle-alert',
  info: 'info',
};

export interface AlertProps {
  tone?: AlertTone;
  title?: string;
  children?: ReactNode;
  action?: ReactNode;
  icon?: IconName;
  className?: string;
}

export function Alert({ tone = 'info', title, children, action, icon, className }: AlertProps) {
  return (
    <div className={cx('v-alert', `v-alert--${tone}`, className)} role={tone === 'danger' || tone === 'warning' ? 'alert' : 'status'}>
      <Icon name={icon ?? ICONO_DE_TONO[tone]} size={22} className="v-alert__icon" />
      <div className="v-alert__body">
        {title && <p className="label">{title}</p>}
        {children && <div className="body-sm">{children}</div>}
      </div>
      {action && <div className="v-alert__action">{action}</div>}
    </div>
  );
}

export interface DialogProps {
  open?: boolean;
  /** Se dibuja en el flujo de la página (para documentación), sin scrim fijo ni foco. */
  inline?: boolean;
  title: string;
  onClose?: () => void;
  children?: ReactNode;
  actions?: ReactNode;
  id?: string;
  className?: string;
}

export function Dialog({ open, inline, title, onClose, children, actions, id, className }: DialogProps) {
  const tituloId = useFieldId(id);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open || inline) return undefined;
    ref.current?.focus();
    function alTeclear(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose?.();
    }
    document.addEventListener('keydown', alTeclear);
    return () => document.removeEventListener('keydown', alTeclear);
  }, [open, inline, onClose]);

  if (!open && !inline) return null;

  const panel = (
    <div ref={ref} tabIndex={-1} role="dialog" aria-modal={inline ? undefined : true} aria-labelledby={tituloId} className={cx('v-dialog', className)}>
      <div className="v-dialog__head">
        <h2 id={tituloId} className="h2">{title}</h2>
        {onClose && <IconButton icon="x" label="Cerrar" variant="text" onClick={onClose} />}
      </div>
      <div className="body v-dialog__body">{children}</div>
      {actions && <div className="v-dialog__foot">{actions}</div>}
    </div>
  );
  if (inline) return <div className="v-dialog-stage">{panel}</div>;
  return (
    <div
      className="v-scrim"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose?.();
      }}
    >
      {panel}
    </div>
  );
}

export interface EmptyStateProps {
  icon?: IconName;
  title: string;
  children?: ReactNode;
  action?: ReactNode;
  className?: string;
}

export function EmptyState({ icon = 'sparkles', title, children, action, className }: EmptyStateProps) {
  return (
    <div className={cx('v-empty', className)}>
      <span className="v-empty__icon" aria-hidden>
        <Icon name={icon} size={28} />
      </span>
      <h3 className="h3">{title}</h3>
      {children && <p className="body v-muted v-empty__text">{children}</p>}
      {action}
    </div>
  );
}

export interface BadgeProps {
  tone?: BadgeTone;
  icon?: IconName;
  children: ReactNode;
  className?: string;
}

export function Badge({ tone = 'neutral', icon, children, className }: BadgeProps) {
  return (
    <span className={cx('v-badge', 'caption', `v-badge--${tone}`, className)}>
      {icon && <Icon name={icon} size={14} strokeWidth={2.25} />}
      {children}
    </span>
  );
}
