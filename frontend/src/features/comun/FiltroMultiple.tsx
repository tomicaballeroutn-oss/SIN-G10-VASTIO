import { useCallback, useEffect, useId, useRef, useState, type CSSProperties } from 'react';
import { Badge, Button, Checkbox, IconButton } from '../../ds';
import './filtro-multiple.css';

export interface OpcionFiltro {
  value: string;
  label: string;
}

/** Selector compacto de varias opciones con un panel flotante que no altera el flujo de la página. */
export function FiltroMultiple({ label, opciones, valores, alCambiar }: {
  label: string;
  opciones: OpcionFiltro[];
  valores: string[];
  alCambiar: (valores: string[]) => void;
}) {
  const [abierto, setAbierto] = useState(false);
  const [posicion, setPosicion] = useState<{ left: number; top: number; width: number; maxHeight: number } | null>(null);
  const id = useId();
  const raiz = useRef<HTMLDivElement>(null);
  const panel = useRef<HTMLDivElement>(null);

  const cerrarPanel = useCallback(() => {
    setAbierto(false);
    raiz.current?.querySelector<HTMLButtonElement>('.filtro-multiple__trigger')?.focus();
  }, []);

  function alternar(valor: string, seleccionado: boolean) {
    alCambiar(seleccionado ? [...valores, valor] : valores.filter((actual) => actual !== valor));
  }

  const posicionar = useCallback(() => {
    const disparador = raiz.current?.querySelector<HTMLButtonElement>('.filtro-multiple__trigger');
    if (!disparador) return;
    const caja = disparador.getBoundingClientRect();
    const ancho = Math.min(Math.max(caja.width, 260), window.innerWidth - 32);
    const izquierda = Math.max(16, Math.min(caja.left, window.innerWidth - ancho - 16));
    const maxHeight = Math.min(420, window.innerHeight - 32);
    const alturaEstimada = Math.min(maxHeight, 140 + Math.min(opciones.length * 48, 280));
    const espacioAbajo = window.innerHeight - caja.bottom - 16;
    const espacioArriba = caja.top - 16;
    const abrirArriba = espacioAbajo < alturaEstimada && espacioArriba > espacioAbajo;
    const altoDisponible = abrirArriba ? espacioArriba : espacioAbajo;
    const top = abrirArriba
      ? Math.max(16, caja.top - Math.min(alturaEstimada, altoDisponible) - 8)
      : Math.max(16, caja.bottom + 8);
    setPosicion({ left: izquierda, top, width: ancho, maxHeight: Math.max(160, Math.min(maxHeight, altoDisponible)) });
  }, [opciones.length]);

  useEffect(() => {
    if (!abierto) return undefined;
    function cerrarAlHacerClickAfuera(evento: PointerEvent) {
      if (!raiz.current?.contains(evento.target as Node)) setAbierto(false);
    }
    function cerrarConEscape(evento: KeyboardEvent) {
      if (evento.key !== 'Escape') return;
      cerrarPanel();
    }
    function cerrarAlDesplazarse(evento: Event) {
      if (!raiz.current?.contains(evento.target as Node)) setAbierto(false);
    }
    function cerrarAlPerderFoco(evento: FocusEvent) {
      if (!raiz.current?.contains(evento.target as Node)) setAbierto(false);
    }
    document.addEventListener('pointerdown', cerrarAlHacerClickAfuera);
    document.addEventListener('keydown', cerrarConEscape);
    document.addEventListener('focusin', cerrarAlPerderFoco);
    window.addEventListener('scroll', cerrarAlDesplazarse, true);
    return () => {
      document.removeEventListener('pointerdown', cerrarAlHacerClickAfuera);
      document.removeEventListener('keydown', cerrarConEscape);
      document.removeEventListener('focusin', cerrarAlPerderFoco);
      window.removeEventListener('scroll', cerrarAlDesplazarse, true);
    };
  }, [abierto, cerrarPanel]);

  useEffect(() => {
    if (!abierto) return undefined;
    window.addEventListener('resize', posicionar);
    return () => {
      window.removeEventListener('resize', posicionar);
    };
  }, [abierto, posicionar]);

  const estiloPanel: CSSProperties = posicion
    ? { left: posicion.left, top: posicion.top, width: posicion.width, maxHeight: posicion.maxHeight }
    : {};

  function alternarPanel() {
    if (abierto) {
      setAbierto(false);
      return;
    }
    posicionar();
    setAbierto(true);
  }

  return (
    <div ref={raiz} className="filtro-multiple">
      <Button
        variant="outline"
        size="md"
        iconEnd={abierto ? 'chevron-up' : 'chevron-down'}
        className={`filtro-multiple__trigger${valores.length > 0 ? ' is-activo' : ''}${abierto ? ' is-abierto' : ''}`}
        ariaExpanded={abierto}
        ariaControls={abierto ? id : undefined}
        onClick={alternarPanel}
      >
        <span className="filtro-multiple__nombre">{label}</span>
        {valores.length > 0 && <>{' '}<Badge tone="accent">{valores.length}</Badge></>}
      </Button>
      {abierto && (
        <div
          ref={panel}
          id={id}
          className="filtro-multiple__panel"
          style={estiloPanel}
        >
          <header className="filtro-multiple__cabecera">
            <div>
              <p className="label">{label}</p>
              <p className="caption v-muted">Elegí una o más opciones</p>
            </div>
            <IconButton icon="x" label={`Cerrar filtro de ${label.toLowerCase()}`} variant="text" size="sm" onClick={cerrarPanel} />
          </header>
          <fieldset className="filtro-multiple__opciones">
            <legend className="v-sr">{label}</legend>
            {opciones.map((opcion) => (
              <Checkbox
                key={opcion.value}
                name={label}
                label={opcion.label}
                className="filtro-multiple__opcion"
                checked={valores.includes(opcion.value)}
                onChange={(e) => alternar(opcion.value, e.target.checked)}
              />
            ))}
            {opciones.length === 0 && <p className="body-sm v-muted filtro-multiple__vacio">No hay opciones para mostrar.</p>}
          </fieldset>
        <footer className="filtro-multiple__pie">
            {valores.length > 0
              ? <Button variant="text" size="sm" onClick={() => alCambiar([])}>Limpiar</Button>
              : <span className="caption v-muted">Sin filtros activos</span>}
            <Button variant="solid" size="sm" onClick={cerrarPanel}>Listo</Button>
          </footer>
        </div>
      )}
    </div>
  );
}
