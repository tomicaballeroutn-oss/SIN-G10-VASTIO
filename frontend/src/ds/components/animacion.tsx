import { useEffect, useRef, useState, type ReactNode, type SyntheticEvent } from 'react';
import { cx } from '../util';
import { Icon } from './Icon';
import { DURACION_ANIMACION_MS, puedeAnimar, useEsperaAnimada } from '../animacion';

/** Safari e iPhone no muestran bien la transparencia del WebM: ahí va el GIF. */
function usaGif(): boolean {
  if (typeof navigator === 'undefined') return false;
  const ua = navigator.userAgent;
  return /iPhone|iPad|iPod/.test(ua) || (/Safari\//.test(ua) && !/Chrome\/|Chromium\/|Edg\//.test(ua));
}

export interface AnimacionMarcaProps {
  /** azul para fondos claros; blanco para el azul de la marca o el tema oscuro. Sin indicar, sigue al tema. */
  tono?: 'azul' | 'blanco';
  /** Ancho en px. */
  tamano?: number;
  /** Se llama cada vez que termina una vuelta completa. */
  alTerminar?: () => void;
  /** Repetir hasta que se desmonte. */
  repetir?: boolean;
  /** La marca quieta, en el mismo lugar y tamaño (p. ej. mientras se decide si animar). */
  quieta?: boolean;
  className?: string;
}

/**
 * La marca de Vastio animada. Con «reducir movimiento» (o sin forma de saberlo) muestra la marca quieta.
 */
export function AnimacionMarca({ tono, tamano = 120, alTerminar, repetir, quieta, className }: AnimacionMarcaProps) {
  const [puede] = useState(puedeAnimar);
  const animar = puede && !quieta;
  const [gif] = useState(usaGif);
  const ultimo = useRef(alTerminar);
  useEffect(() => { ultimo.current = alTerminar; });

  // El GIF no avisa cuándo termina, y la marca quieta tampoco: se cuenta la duración.
  useEffect(() => {
    if (animar && !gif) return undefined;
    const espera = DURACION_ANIMACION_MS;
    const id = repetir && animar ? window.setInterval(() => ultimo.current?.(), espera) : window.setTimeout(() => ultimo.current?.(), espera);
    return () => { window.clearInterval(id); window.clearTimeout(id); };
  }, [animar, gif, repetir]);

  const clase = cx('v-animacion-marca', tono && `v-animacion-marca--${tono}`, className);
  const estilo = { width: tamano };
  const nombre = (t: 'azul' | 'blanco') => `/brand/animaciones/vastio-logo-animado-${t}`;

  if (!animar) {
    return (
      <span className={clase} style={estilo} aria-hidden>
        {tono !== 'blanco' && <img src="/brand/vastio-mark.svg" alt="" className="v-animacion-marca__claro" />}
        {tono !== 'azul' && <img src="/brand/vastio-mark-reverse.svg" alt="" className="v-animacion-marca__oscuro" />}
      </span>
    );
  }
  if (gif) {
    return (
      <span className={clase} style={estilo} aria-hidden>
        {tono !== 'blanco' && <img src={`${nombre('azul')}.gif`} alt="" className="v-animacion-marca__claro" />}
        {tono !== 'azul' && <img src={`${nombre('blanco')}.gif`} alt="" className="v-animacion-marca__oscuro" />}
      </span>
    );
  }
  const terminar = (e: SyntheticEvent<HTMLVideoElement>) => {
    ultimo.current?.();
    if (repetir) { e.currentTarget.currentTime = 0; e.currentTarget.play()?.catch(() => undefined); }
  };
  return (
    <span className={clase} style={estilo} aria-hidden>
      {tono !== 'blanco' && (
        <video className="v-animacion-marca__claro" src={`${nombre('azul')}.webm`} autoPlay muted playsInline onEnded={terminar} />
      )}
      {tono !== 'azul' && (
        <video className="v-animacion-marca__oscuro" src={`${nombre('blanco')}.webm`} autoPlay muted playsInline onEnded={terminar} />
      )}
    </span>
  );
}

export interface EsperaAnimadaProps {
  cargando: boolean;
  /** Lo que se ve en una carga corta, o si no se anima (p. ej. «Cargando…»). */
  mientras?: ReactNode;
  children: ReactNode;
  /** A pantalla completa, para la carga inicial de la app. */
  pantallaCompleta?: boolean;
}

/** Envuelve una carga: texto breve, animación si tarda, y el contenido cuando terminó la vuelta. */
export function EsperaAnimada({ cargando, mientras = null, children, pantallaCompleta }: EsperaAnimadaProps) {
  const { animando, alTerminarVuelta } = useEsperaAnimada(cargando);
  if (animando) {
    return (
      <div className={cx('v-espera-animada', pantallaCompleta && 'v-espera-animada--pantalla')} role="status" aria-label="Cargando">
        <AnimacionMarca tamano={pantallaCompleta ? 160 : 96} repetir alTerminar={alTerminarVuelta} />
      </div>
    );
  }
  return <>{cargando ? mientras : children}</>;
}

export interface CelebracionHitoProps {
  /** Lo que se logró, p. ej. «Evento confirmado». */
  titulo: string;
  detalle?: string;
  alCerrar: () => void;
}

/**
 * Un hito del circuito (evento confirmado, evento cerrado): un cartel chico en el centro con la marca animada, sobre la
 * pantalla apenas desenfocada. No tapa todo: se cierra solo al terminar la animación, tocando en cualquier lado o con Escape.
 */
export function CelebracionHito({ titulo, detalle, alCerrar }: CelebracionHitoProps) {
  const ultimo = useRef(alCerrar);
  useEffect(() => { ultimo.current = alCerrar; });
  useEffect(() => {
    const teclas = (e: KeyboardEvent) => { if (e.key === 'Escape') ultimo.current(); };
    document.addEventListener('keydown', teclas);
    // Respaldo por si el video no avisa que terminó.
    const id = window.setTimeout(() => ultimo.current(), DURACION_ANIMACION_MS + 1500);
    return () => { document.removeEventListener('keydown', teclas); window.clearTimeout(id); };
  }, []);
  return (
    <div className="v-hito" onClick={() => ultimo.current()} role="presentation">
      <div className="v-hito__cartel" role="status" aria-live="polite">
        <AnimacionMarca tamano={112} alTerminar={() => window.setTimeout(() => ultimo.current(), 600)} />
        <p className="h3 v-hito__titulo"><Icon name="circle-check" size={22} /> {titulo}</p>
        {detalle && <p className="body-sm v-muted">{detalle}</p>}
      </div>
    </div>
  );
}
