import { useId } from 'react';
import type { MouseEvent, ReactNode } from 'react';
import type { IconName, Tone } from '../tipos';
import { cx } from '../util';
import { Icon } from './Icon';

export interface ButtonProps {
  children: ReactNode;
  variant?: 'solid' | 'outline' | 'text';
  tone?: Tone;
  size?: 'sm' | 'md' | 'lg';
  icon?: IconName;
  iconEnd?: IconName;
  block?: boolean;
  loading?: boolean;
  disabled?: boolean;
  onClick?: (e: MouseEvent<HTMLElement>) => void;
  href?: string;
  type?: 'button' | 'submit';
  title?: string;
  className?: string;
  ariaExpanded?: boolean;
  ariaControls?: string;
  /**
   * La acción todavía no se puede hacer: el botón deja el relleno sólido y muestra que falta algo, pero se puede tocar
   * para ver qué falta. `true` anuncia «Faltan requisitos»; un texto lo reemplaza.
   */
  pendiente?: boolean | string;
}

export function Button({
  children, variant = 'solid', tone, size = 'md', icon, iconEnd, block, loading, disabled, onClick, href, type = 'button', title, className,
  ariaExpanded, ariaControls, pendiente,
}: ButtonProps) {
  const idPendiente = useId();
  const cls = cx('v-btn', 'button', `v-btn--${variant}`, `v-btn--${size}`, tone === 'danger' && 'v-tone-danger',
    block && 'v-btn--block', loading && 'is-loading', pendiente && 'is-pendiente', className);
  const iconSize = size === 'sm' ? 18 : 20;
  const contenido = (
    <>
      {loading ? <span className="v-spin" aria-hidden /> : icon && <Icon name={icon} size={iconSize} />}
      <span>{children}</span>
      {iconEnd && !loading && <Icon name={iconEnd} size={iconSize} />}
      {pendiente && !loading && <Icon name="circle-alert" size={iconSize} className="v-btn__pendiente" />}
    </>
  );
  if (href) {
    return <a href={href} className={cls} onClick={onClick} title={title} aria-busy={loading || undefined}>{contenido}</a>;
  }
  // Lo que falta va fuera del botón: lo describe, no cambia su nombre.
  const descripcion = pendiente ? <span id={idPendiente} className="v-sr">{pendiente === true ? 'Faltan requisitos' : pendiente}</span> : null;
  return (
    <>
    <button
      type={type}
      className={cls}
      onClick={onClick}
      title={title}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      aria-expanded={ariaExpanded}
      aria-controls={ariaControls}
      aria-describedby={pendiente ? idPendiente : undefined}
    >
      {contenido}
    </button>
    {descripcion}
    </>
  );
}

export interface IconButtonProps {
  icon: IconName;
  /** Obligatoria: es lo que anuncia el lector de pantalla. */
  label: string;
  variant?: 'outline' | 'text' | 'solid';
  tone?: Tone;
  size?: 'sm' | 'md' | 'lg';
  disabled?: boolean;
  pressed?: boolean;
  /**
   * Contador sobre el ícono (p. ej. avisos sin leer); 0 o ausente no se dibuja y más de 99 se muestra «99+».
   * Es solo visual: `label` tiene que decir la cantidad («Notificaciones, 3 sin leer»).
   */
  count?: number;
  onClick?: (e: MouseEvent<HTMLButtonElement>) => void;
  type?: 'button' | 'submit';
  className?: string;
}

export function IconButton({ icon, label, variant = 'outline', tone, size = 'md', disabled, pressed, count, onClick, type = 'button', className }: IconButtonProps) {
  return (
    <button
      type={type}
      className={cx('v-iconbtn', `v-iconbtn--${variant}`, `v-iconbtn--${size}`, tone === 'danger' && 'v-tone-danger', className)}
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
      aria-pressed={pressed}
    >
      <Icon name={icon} size={size === 'sm' ? 18 : 22} />
      {!!count && count > 0 && <span className="v-iconbtn__count caption" aria-hidden>{count > 99 ? '99+' : count}</span>}
    </button>
  );
}
