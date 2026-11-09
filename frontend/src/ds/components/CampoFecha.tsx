import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { cx } from '../util';
import { IconButton } from './Button';
import { MiniCalendar } from './agenda';
import { fechaDdMmAaaa, fechaIso } from '../fecha';

/** Deja solo dígitos y pone las barras: «0105» → «01/05». */
function conMascara(texto: string): string {
  if (/^\d{4}-\d{2}-\d{2}$/.test(texto)) return fechaDdMmAaaa(texto);
  const digitos = texto.replace(/\D/g, '').slice(0, 8);
  return [digitos.slice(0, 2), digitos.slice(2, 4), digitos.slice(4)].filter(Boolean).join('/');
}

function hoyIso(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Argentina/Cordoba' }).format(new Date());
}

export interface CampoFechaControlProps {
  id: string;
  name?: string;
  /** aaaa-mm-dd o ''. */
  value?: string;
  defaultValue?: string;
  /** e.target.value llega como aaaa-mm-dd ('' si se borró): igual que el campo de fecha nativo. */
  onChange?: (e: ChangeEvent<HTMLInputElement>) => void;
  disabled?: boolean;
  required?: boolean;
  readOnly?: boolean;
  size?: 'md' | 'lg';
  'aria-invalid'?: boolean;
  'aria-describedby'?: string;
}

/**
 * Fecha siempre como día/mes/año, sin depender del idioma del navegador (el campo nativo muestra mm/dd/aaaa en un
 * navegador en inglés). Se escribe con máscara o se elige en el calendario. Hacia afuera, aaaa-mm-dd.
 */
export function CampoFechaControl(p: CampoFechaControlProps) {
  const controlado = p.value !== undefined;
  const [interno, setInterno] = useState(p.defaultValue ?? '');
  const valor = controlado ? p.value ?? '' : interno;
  const [texto, setTexto] = useState(fechaDdMmAaaa(valor));
  const [abierto, setAbierto] = useState(false);
  const zona = useRef<HTMLDivElement>(null);
  const campo = useRef<HTMLInputElement>(null);
  const inicial = fechaIso(texto) ?? (valor || hoyIso());
  const [mes, setMes] = useState({ anio: Number(inicial.slice(0, 4)), mes: Number(inicial.slice(5, 7)) - 1 });

  // Si el valor cambia desde afuera, se muestra; mientras se escribe una fecha incompleta, no se pisa.
  const [valorVisto, setValorVisto] = useState(valor);
  if (valor !== valorVisto) {
    setValorVisto(valor);
    if (fechaIso(texto) !== valor && !(texto === '' && valor === '')) setTexto(fechaDdMmAaaa(valor));
  }

  // Una fecha a medio escribir no es válida: el formulario la frena como haría el campo nativo.
  useEffect(() => {
    campo.current?.setCustomValidity(texto && !fechaIso(texto) ? 'Completá la fecha como dd/mm/aaaa.' : '');
  }, [texto]);

  useEffect(() => {
    if (!abierto) return undefined;
    function alTocar(e: PointerEvent) {
      if (e.target instanceof Node && !zona.current?.contains(e.target)) setAbierto(false);
    }
    function alTeclear(e: KeyboardEvent) {
      if (e.key === 'Escape') { setAbierto(false); campo.current?.focus(); }
    }
    document.addEventListener('pointerdown', alTocar);
    document.addEventListener('keydown', alTeclear);
    return () => { document.removeEventListener('pointerdown', alTocar); document.removeEventListener('keydown', alTeclear); };
  }, [abierto]);

  function emitir(iso: string) {
    if (!controlado) setInterno(iso);
    p.onChange?.({ target: { value: iso, name: p.name }, currentTarget: { value: iso, name: p.name } } as unknown as ChangeEvent<HTMLInputElement>);
  }

  function alEscribir(e: ChangeEvent<HTMLInputElement>) {
    const nuevo = conMascara(e.target.value);
    setTexto(nuevo);
    if (nuevo === '') { if (valor !== '') emitir(''); return; }
    const iso = fechaIso(nuevo);
    if (iso && iso !== valor) emitir(iso);
  }

  function abrir() {
    const base = fechaIso(texto) ?? (valor || hoyIso());
    setMes({ anio: Number(base.slice(0, 4)), mes: Number(base.slice(5, 7)) - 1 });
    setAbierto((v) => !v);
  }

  const mover = (delta: number) => setMes(({ anio, mes: m }) => {
    const d = new Date(anio, m + delta, 1);
    return { anio: d.getFullYear(), mes: d.getMonth() };
  });

  return (
    <div ref={zona} className="v-campo-fecha">
      <input
        ref={campo}
        id={p.id}
        name={p.name}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        placeholder="dd/mm/aaaa"
        maxLength={10}
        value={texto}
        onChange={alEscribir}
        onBlur={() => { if (texto && !fechaIso(texto)) setTexto(fechaDdMmAaaa(valor)); }}
        disabled={p.disabled}
        required={p.required}
        readOnly={p.readOnly}
        aria-invalid={p['aria-invalid']}
        aria-describedby={p['aria-describedby']}
        className={cx('v-control', 'body', 'v-campo-fecha__texto', p.size === 'lg' && 'v-control--lg')}
      />
      {!p.disabled && !p.readOnly && (
        <span className="v-campo-fecha__boton">
          <IconButton icon="calendar" label="Elegir en el calendario" variant="text" size="sm" pressed={abierto} onClick={abrir} />
        </span>
      )}
      {abierto && (
        <div className="v-campo-fecha__panel" role="dialog" aria-label="Elegir fecha">
          <MiniCalendar
            year={mes.anio}
            month={mes.mes}
            today={hoyIso()}
            selected={valor || undefined}
            legend={false}
            onPrev={() => mover(-1)}
            onNext={() => mover(1)}
            onSelectDay={(iso) => { setTexto(fechaDdMmAaaa(iso)); emitir(iso); setAbierto(false); campo.current?.focus(); }}
          />
        </div>
      )}
    </div>
  );
}
