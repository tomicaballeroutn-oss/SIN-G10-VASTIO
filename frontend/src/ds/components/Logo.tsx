import { cx } from '../util';

export interface LogoProps {
  /** completo: marca + wordmark (login, portadas; 120 px de alto o más). marca: solo la V (barra lateral, menos de 120 px). */
  variant?: 'completo' | 'marca';
  className?: string;
}

/**
 * Logo de Vastio desde los archivos de public/brand. En tema oscuro usa la versión -reverse.
 * No se recolorea ni se reescribe el wordmark con tipografía.
 */
export function Logo({ variant = 'completo', className }: LogoProps) {
  const base = variant === 'completo' ? 'vastio-logo' : 'vastio-mark';
  return (
    <span className={cx('v-brand', `v-brand--${variant}`, className)}>
      <img src={`/brand/${base}.png`} alt="Vastio" className="v-brand__claro" />
      <img src={`/brand/${base}-reverse.png`} alt="Vastio" className="v-brand__oscuro" />
    </span>
  );
}
