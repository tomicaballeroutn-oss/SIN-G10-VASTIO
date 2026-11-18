import { useId, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { IconButton } from './Button';
import './popover.css';

export interface PopoverProps {
  title: string;
  label: string;
  children: ReactNode;
}

/** Ayuda no modal. El panel pertenece al diálogo que la contiene, si lo hay. */
export function Popover({ title, label, children }: PopoverProps) {
  const id = useId();
  const disparador = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const [abierto, setAbierto] = useState(false);
  const [posicion, setPosicion] = useState<{ top: number; left: number; maxHeight?: number }>({ top: 0, left: 0 });
  const [destino, setDestino] = useState<Element | null>(null);

  function cerrar() {
    setAbierto(false);
    disparador.current?.focus({ preventScroll: true });
  }

  useLayoutEffect(() => {
    if (!abierto) return;
    function ubicar() {
      if (!disparador.current || !panel.current) return;
      const ancla = disparador.current.getBoundingClientRect();
      const margen = 16;
      const contenido = {
        width: panel.current.getBoundingClientRect().width,
        height: Math.min(panel.current.scrollHeight + 2, window.innerHeight - margen * 2),
      };
      const debajo = window.innerHeight - ancla.bottom - margen;
      let top = debajo < contenido.height && ancla.top > debajo
        ? ancla.top - contenido.height - 8 : ancla.bottom + 8;
      let left = ancla.left;
      let maxHeight = window.innerHeight - margen * 2;
      const dialogo = disparador.current.closest('.v-dialog');
      const limitesDialogo = dialogo?.getBoundingClientRect();
      if (limitesDialogo) {
        if (limitesDialogo.top >= contenido.height + margen + 8) {
          top = limitesDialogo.top - contenido.height - 8;
        } else if (window.innerWidth - limitesDialogo.right >= contenido.width + margen + 8) {
          left = limitesDialogo.right + 8;
        } else if (limitesDialogo.left >= contenido.width + margen + 8) {
          left = limitesDialogo.left - contenido.width - 8;
        } else {
          // En una ventana baja el panel puede desplazarse, conservando libres las acciones.
          const acciones = dialogo?.querySelector('.v-dialog__foot')?.getBoundingClientRect();
          if (acciones) {
            maxHeight = Math.max(44, acciones.top - margen - 8);
            top = Math.min(top, acciones.top - Math.min(contenido.height, maxHeight) - 8);
          }
        }
      }
      setPosicion({
        top: Math.max(margen, Math.min(top, window.innerHeight - Math.min(contenido.height, maxHeight) - margen)),
        left: Math.max(margen, Math.min(left, window.innerWidth - contenido.width - margen)),
        maxHeight,
      });
    }
    function afuera(evento: Event) {
      const objetivo = evento.target;
      if (objetivo instanceof Node && !panel.current?.contains(objetivo) && !disparador.current?.contains(objetivo)) {
        setAbierto(false);
      }
    }
    ubicar();
    panel.current?.focus({ preventScroll: true });
    document.addEventListener('pointerdown', afuera, true);
    document.addEventListener('focusin', afuera);
    window.addEventListener('resize', ubicar);
    window.addEventListener('scroll', ubicar, true);
    return () => {
      document.removeEventListener('pointerdown', afuera, true);
      document.removeEventListener('focusin', afuera);
      window.removeEventListener('resize', ubicar);
      window.removeEventListener('scroll', ubicar, true);
    };
  }, [abierto]);

  return (
    <span className="v-popover" onKeyDown={(evento) => {
      if (abierto && evento.key === 'Escape') {
        evento.preventDefault();
        evento.stopPropagation();
        cerrar();
      }
    }}>
      <button ref={disparador} type="button" className="v-popover__disparador" aria-label={label}
        aria-haspopup="dialog" aria-expanded={abierto} aria-controls={abierto ? id : undefined}
        onClick={(evento) => {
          // Dentro de un summary, abrir la ayuda conserva el estado de la leyenda.
          evento.preventDefault();
          evento.stopPropagation();
          setDestino(evento.currentTarget.closest('[role="dialog"]') ?? document.body);
          setAbierto(!abierto);
        }}>
        <span aria-hidden="true">?</span>
      </button>
      {abierto && destino && createPortal(
        <div ref={panel} id={id} className="v-popover__panel" role="dialog" tabIndex={-1}
          aria-labelledby={`${id}-titulo`} aria-describedby={`${id}-contenido`} style={posicion}>
          <div className="v-popover__cabecera">
            <h3 id={`${id}-titulo`} className="h4">{title}</h3>
            <IconButton icon="x" label="Cerrar ayuda" variant="text" onClick={cerrar} />
          </div>
          <div id={`${id}-contenido`} className="v-popover__contenido body-sm">{children}</div>
        </div>, destino,
      )}
    </span>
  );
}
