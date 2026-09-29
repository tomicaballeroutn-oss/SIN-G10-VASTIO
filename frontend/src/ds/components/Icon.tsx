import { trazoDe } from '../iconos';
import type { IconName } from '../tipos';
import { cx } from '../util';

export interface IconProps {
  name: IconName;
  size?: number;
  strokeWidth?: number;
  /** Si tiene título se anuncia como imagen; si no, es decorativo. */
  title?: string;
  className?: string;
}

export function Icon({ name, size = 20, strokeWidth = 2, title, className }: IconProps) {
  return (
    <svg
      className={cx('v-icon', className)}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={title ? undefined : true}
      role={title ? 'img' : undefined}
      aria-label={title}
      focusable="false"
      // Trazos fijos de Lucide (iconos.ts), nunca texto de usuario.
      dangerouslySetInnerHTML={{ __html: trazoDe(name) }}
    />
  );
}
