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
}

export function Button({
  children, variant = 'solid', tone, size = 'md', icon, iconEnd, block, loading, disabled, onClick, href, type = 'button', title, className,
}: ButtonProps) {
  const cls = cx('v-btn', 'button', `v-btn--${variant}`, `v-btn--${size}`, tone === 'danger' && 'v-tone-danger',
    block && 'v-btn--block', loading && 'is-loading', className);
  const iconSize = size === 'sm' ? 18 : 20;
  const contenido = (
    <>
      {loading ? <span className="v-spin" aria-hidden /> : icon && <Icon name={icon} size={iconSize} />}
      <span>{children}</span>
      {iconEnd && !loading && <Icon name={iconEnd} size={iconSize} />}
    </>
  );
  if (href) {
    return <a href={href} className={cls} onClick={onClick} title={title} aria-busy={loading || undefined}>{contenido}</a>;
  }
  return (
    <button type={type} className={cls} onClick={onClick} title={title} disabled={disabled || loading} aria-busy={loading || undefined}>
      {contenido}
    </button>
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
  onClick?: (e: MouseEvent<HTMLButtonElement>) => void;
  type?: 'button' | 'submit';
  className?: string;
}

export function IconButton({ icon, label, variant = 'outline', tone, size = 'md', disabled, pressed, onClick, type = 'button', className }: IconButtonProps) {
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
    </button>
  );
}
