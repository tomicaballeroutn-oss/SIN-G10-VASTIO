import { cx } from '../util';

export interface LogoProps {
  /** completo: marca + wordmark (login, portadas; 120 px de alto o más). marca: solo la V (barra lateral, menos de 120 px). */
  variant?: 'completo' | 'marca';
  /** Siempre la versión -reverse, en los dos temas: para fondos azules como el riel o el login. */
  reverse?: boolean;
  className?: string;
}

/**
 * Logo de Vastio desde los archivos de public/brand. En tema oscuro usa la versión -reverse.
 * La marca es SVG (nítida en cualquier tamaño); el completo sigue en PNG hasta tener el wordmark en SVG.
 * No se recolorea ni se reescribe el wordmark con tipografía.
 */
export function Logo({ variant = 'completo', reverse, className }: LogoProps) {
  const base = variant === 'completo' ? 'vastio-logo' : 'vastio-mark';
  const ext = variant === 'completo' ? 'png' : 'svg';
  return (
    <span className={cx('v-brand', `v-brand--${variant}`, className)}>
      {reverse ? (
        <img src={`/brand/${base}-reverse.${ext}`} alt="Vastio" />
      ) : (
        <>
          <img src={`/brand/${base}.${ext}`} alt="Vastio" className="v-brand__claro" />
          <img src={`/brand/${base}-reverse.${ext}`} alt="Vastio" className="v-brand__oscuro" />
        </>
      )}
    </span>
  );
}
