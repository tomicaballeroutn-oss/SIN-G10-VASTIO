import { useState, type ChangeEvent, type KeyboardEvent, type ReactNode } from 'react';
import type { IconName } from '../tipos';
import { cx, tamanoLegible, useFieldId } from '../util';
import { Icon } from './Icon';

interface FieldBase {
  label?: string;
  hint?: string;
  error?: string;
  optional?: boolean;
  id?: string;
  disabled?: boolean;
  className?: string;
}

/** Etiqueta arriba, control, y abajo el error (con role="alert") o la ayuda. */
function Field({ label, hint, error, optional, id, className, children }: FieldBase & { id: string; children: ReactNode }) {
  return (
    <div className={cx('v-field', className)}>
      {label && (
        <label className="label v-field__label" htmlFor={id}>
          {label}
          {optional && <span className="v-muted"> (opcional)</span>}
        </label>
      )}
      {children}
      {error ? (
        <p id={`${id}-msg`} className="body-sm v-field__error" role="alert">
          <Icon name="circle-alert" size={16} />
          <span>{error}</span>
        </p>
      ) : (
        hint && <p id={`${id}-msg`} className="body-sm v-muted">{hint}</p>
      )}
    </div>
  );
}

export interface InputProps extends FieldBase {
  value?: string | number;
  defaultValue?: string | number;
  onChange?: (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => void;
  placeholder?: string;
  name?: string;
  type?: string;
  inputMode?: 'text' | 'numeric' | 'decimal' | 'tel' | 'email';
  icon?: IconName;
  suffix?: string;
  multiline?: boolean;
  rows?: number;
  size?: 'md' | 'lg';
  required?: boolean;
  readOnly?: boolean;
  autoComplete?: string;
}

export function Input(p: InputProps) {
  const id = useFieldId(p.id);
  const comunes = {
    id,
    name: p.name,
    value: p.value,
    defaultValue: p.defaultValue,
    onChange: p.onChange,
    placeholder: p.placeholder,
    disabled: p.disabled,
    required: p.required,
    readOnly: p.readOnly,
    inputMode: p.inputMode,
    autoComplete: p.autoComplete,
    className: cx('v-control', 'body', p.size === 'lg' && 'v-control--lg', p.icon && 'has-icon', p.suffix && 'has-suffix', p.multiline && 'v-control--area'),
    'aria-invalid': p.error ? true : undefined,
    'aria-describedby': p.error || p.hint ? `${id}-msg` : undefined,
  };
  return (
    <Field label={p.label} hint={p.hint} error={p.error} optional={p.optional} id={id} className={p.className}>
      <div className="v-control-wrap">
        {p.icon && <Icon name={p.icon} size={20} className="v-control__icon" />}
        {p.multiline ? <textarea {...comunes} rows={p.rows ?? 3} /> : <input {...comunes} type={p.type ?? 'text'} />}
        {p.suffix && <span className="body-sm v-muted v-control__suffix">{p.suffix}</span>}
      </div>
    </Field>
  );
}

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectProps extends FieldBase {
  options: SelectOption[];
  value?: string;
  defaultValue?: string;
  onChange?: (e: ChangeEvent<HTMLSelectElement>) => void;
  placeholder?: string;
  name?: string;
  size?: 'md' | 'lg';
  required?: boolean;
}

export function Select(p: SelectProps) {
  const id = useFieldId(p.id);
  const defaultValue = p.value === undefined ? (p.defaultValue ?? (p.placeholder ? '' : undefined)) : undefined;
  return (
    <Field label={p.label} hint={p.hint} error={p.error} optional={p.optional} id={id} className={p.className}>
      <div className="v-control-wrap">
        <select
          id={id}
          name={p.name}
          value={p.value}
          defaultValue={defaultValue}
          onChange={p.onChange}
          disabled={p.disabled}
          required={p.required}
          className={cx('v-control', 'body', 'v-select', p.size === 'lg' && 'v-control--lg')}
          aria-invalid={p.error ? true : undefined}
          aria-describedby={p.error || p.hint ? `${id}-msg` : undefined}
        >
          {p.placeholder && <option value="" disabled>{p.placeholder}</option>}
          {p.options.map((o) => (
            <option key={o.value} value={o.value} disabled={o.disabled}>{o.label}</option>
          ))}
        </select>
        <Icon name="chevron-down" size={20} className="v-select__chev" />
      </div>
    </Field>
  );
}

export interface ChoiceProps {
  label: string;
  hint?: string;
  checked?: boolean;
  defaultChecked?: boolean;
  onChange?: (e: ChangeEvent<HTMLInputElement>) => void;
  disabled?: boolean;
  name?: string;
  id?: string;
  className?: string;
}

function Choice({ variante, ...p }: ChoiceProps & { variante: 'check' | 'switch' }) {
  const id = useFieldId(p.id);
  return (
    <label className={cx('v-choice', variante === 'switch' && 'v-choice--switch', p.disabled && 'is-disabled', p.className)} htmlFor={id}>
      <input
        id={id}
        type="checkbox"
        role={variante === 'switch' ? 'switch' : undefined}
        className={variante === 'switch' ? 'v-switch' : 'v-check'}
        checked={p.checked}
        defaultChecked={p.defaultChecked}
        onChange={p.onChange}
        disabled={p.disabled}
        name={p.name}
      />
      <span className="v-choice__text">
        <span className="body">{p.label}</span>
        {p.hint && <span className="body-sm v-muted">{p.hint}</span>}
      </span>
    </label>
  );
}

export function Checkbox(p: ChoiceProps) {
  return <Choice {...p} variante="check" />;
}

export function Switch(p: ChoiceProps) {
  return <Choice {...p} variante="switch" />;
}

export interface StepperProps extends FieldBase {
  value?: number;
  defaultValue?: number;
  onChange?: (value: number) => void;
  min?: number;
  max?: number;
  step?: number;
  unit?: string;
}

export function Stepper(p: StepperProps) {
  const id = useFieldId(p.id);
  const [interno, setInterno] = useState(p.defaultValue ?? 0);
  const controlado = p.value !== undefined;
  const valor = controlado ? (p.value as number) : interno;
  const min = p.min ?? 0;
  const max = p.max ?? Infinity;
  const step = p.step ?? 1;

  function fijar(n: number) {
    const acotado = Math.max(min, Math.min(max, n));
    if (!controlado) setInterno(acotado);
    p.onChange?.(acotado);
  }

  return (
    <Field label={p.label} hint={p.hint} error={p.error} id={id} className={p.className}>
      <div className={cx('v-stepper', p.error && 'is-invalid')}>
        <button type="button" className="v-stepper__btn" aria-label={`Restar ${step}`} disabled={p.disabled || valor <= min} onClick={() => fijar(valor - step)}>
          <Icon name="minus" size={22} />
        </button>
        <div className="v-stepper__value">
          <input
            id={id}
            className="numeral v-stepper__input"
            inputMode="numeric"
            value={valor}
            disabled={p.disabled}
            aria-describedby={p.error || p.hint ? `${id}-msg` : undefined}
            aria-invalid={p.error ? true : undefined}
            onChange={(e) => {
              const n = parseInt(String(e.target.value).replace(/\D/g, ''), 10);
              fijar(Number.isNaN(n) ? min : n);
            }}
          />
          {p.unit && <span className="caption v-muted">{p.unit}</span>}
        </div>
        <button type="button" className="v-stepper__btn" aria-label={`Sumar ${step}`} disabled={p.disabled || valor >= max} onClick={() => fijar(valor + step)}>
          <Icon name="plus" size={22} />
        </button>
      </div>
    </Field>
  );
}

export interface ComboboxOption {
  value: string;
  label: string;
  /** Segunda línea, p. ej. documento y teléfono. */
  detail?: string;
  icon?: IconName;
}

export interface ComboboxProps extends FieldBase {
  /** Texto escrito. */
  value: string;
  onInputChange: (texto: string) => void;
  options: ComboboxOption[];
  onSelect: (opcion: ComboboxOption) => void;
  placeholder?: string;
  icon?: IconName;
  /** Mientras se buscan sugerencias. */
  loading?: boolean;
  /** Se muestra si hay texto y ninguna opción. */
  emptyText?: string;
  name?: string;
}

/**
 * Campo de texto con sugerencias (patrón combobox de WAI-ARIA): flechas para recorrer, Enter para elegir,
 * Escape para cerrar. Las opciones las decide la pantalla (p. ej. resultados de una búsqueda más «Cargar nuevo»).
 */
export function Combobox(p: ComboboxProps) {
  const id = useFieldId(p.id);
  const listaId = `${id}-lista`;
  const [abierta, setAbierta] = useState(false);
  const [activa, setActiva] = useState(-1);
  const hayTexto = p.value.trim().length > 0;
  const mostrarVacio = hayTexto && !p.loading && p.options.length === 0 && !!p.emptyText;
  const visible = abierta && hayTexto && (p.options.length > 0 || !!p.loading || mostrarVacio);

  function elegir(opcion: ComboboxOption) {
    p.onSelect(opcion);
    setAbierta(false);
    setActiva(-1);
  }

  function alTeclear(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      setAbierta(true);
      if (p.options.length === 0) return;
      const paso = e.key === 'ArrowDown' ? 1 : -1;
      setActiva((i) => (i + paso + p.options.length) % p.options.length);
    } else if (e.key === 'Enter' && visible && activa >= 0 && p.options[activa]) {
      e.preventDefault();
      elegir(p.options[activa]);
    } else if (e.key === 'Escape' && visible) {
      e.preventDefault();
      setAbierta(false);
    }
  }

  return (
    <Field label={p.label} hint={p.hint} error={p.error} optional={p.optional} id={id} className={cx('v-combo', p.className)}>
      <div className="v-control-wrap">
        {p.icon && <Icon name={p.icon} size={20} className="v-control__icon" />}
        <input
          id={id}
          name={p.name}
          type="text"
          role="combobox"
          autoComplete="off"
          aria-autocomplete="list"
          aria-expanded={visible}
          aria-controls={listaId}
          aria-activedescendant={visible && activa >= 0 ? `${listaId}-${activa}` : undefined}
          aria-invalid={p.error ? true : undefined}
          aria-describedby={p.error || p.hint ? `${id}-msg` : undefined}
          className={cx('v-control', 'body', p.icon && 'has-icon')}
          value={p.value}
          placeholder={p.placeholder}
          disabled={p.disabled}
          onChange={(e) => {
            p.onInputChange(e.target.value);
            setAbierta(true);
            setActiva(-1);
          }}
          onFocus={() => setAbierta(true)}
          onBlur={() => setAbierta(false)}
          onKeyDown={alTeclear}
        />
        {visible && (
          <ul id={listaId} role="listbox" className="v-combo__lista" aria-busy={p.loading || undefined}>
            {p.options.map((o, i) => (
              <li
                key={o.value}
                id={`${listaId}-${i}`}
                role="option"
                aria-selected={i === activa}
                className={cx('v-combo__opcion', i === activa && 'is-active')}
                // mousedown en lugar de click: si no, el blur del campo cierra la lista antes de elegir.
                onMouseDown={(e) => {
                  e.preventDefault();
                  elegir(o);
                }}
              >
                {o.icon && <Icon name={o.icon} size={18} />}
                <span className="v-combo__texto">
                  <span className="body">{o.label}</span>
                  {o.detail && <span className="body-sm v-muted">{o.detail}</span>}
                </span>
              </li>
            ))}
            {p.loading && <li className="body-sm v-muted v-combo__estado" role="presentation">Buscando…</li>}
            {mostrarVacio && <li className="body-sm v-muted v-combo__estado" role="presentation">{p.emptyText}</li>}
          </ul>
        )}
      </div>
    </Field>
  );
}

export interface SelectorArchivosProps extends FieldBase {
  /** Archivos elegidos (controlado). */
  files: File[];
  onChange: (files: File[]) => void;
  /** Tipos aceptados, como en `<input accept>`: `application/pdf,image/jpeg,image/png`. */
  accept?: string;
  multiple?: boolean;
  /** Texto de la zona: qué hacer («Sacá una foto o elegí un archivo de la galería»). */
  prompt?: string;
}

/**
 * Elegir archivos: en el celular abre la cámara o la galería; en la PC, el explorador o arrastrar y soltar.
 * Debajo, la lista de los elegidos con su tamaño y un botón para quitar cada uno.
 */
export function SelectorArchivos(p: SelectorArchivosProps) {
  const id = useFieldId(p.id);
  const [arrastrando, setArrastrando] = useState(false);

  function agregar(nuevos: FileList | null) {
    if (!nuevos || nuevos.length === 0) return;
    const lista = Array.from(nuevos);
    p.onChange(p.multiple ? [...p.files, ...lista] : lista.slice(0, 1));
  }

  return (
    <Field label={p.label} hint={p.hint} error={p.error} optional={p.optional} id={id} className={cx('v-files', p.className)}>
      <div
        className={cx('v-files__zona', arrastrando && 'is-dragging', p.error && 'is-invalid', p.disabled && 'is-disabled')}
        onDragOver={(e) => {
          e.preventDefault();
          if (!p.disabled) setArrastrando(true);
        }}
        onDragLeave={() => setArrastrando(false)}
        onDrop={(e) => {
          e.preventDefault();
          setArrastrando(false);
          if (!p.disabled) agregar(e.dataTransfer.files);
        }}
      >
        <Icon name="upload" size={24} />
        <span className="body-sm">{p.prompt ?? 'Elegí un archivo'}</span>
        <input
          id={id}
          type="file"
          className="v-files__input"
          accept={p.accept}
          multiple={p.multiple}
          disabled={p.disabled}
          aria-invalid={p.error ? true : undefined}
          aria-describedby={p.error || p.hint ? `${id}-msg` : undefined}
          onChange={(e) => {
            agregar(e.target.files);
            // Permite volver a elegir el mismo archivo después de quitarlo.
            e.target.value = '';
          }}
        />
      </div>
      {p.files.length > 0 && (
        <ul className="v-files__lista">
          {p.files.map((f, i) => (
            <li key={`${f.name}-${i}`} className="v-files__item">
              <Icon name="file-text" size={20} />
              <span className="body-sm v-files__nombre">{f.name}</span>
              <span className="caption v-muted">{tamanoLegible(f.size)}</span>
              <button
                type="button"
                className="v-iconbtn v-iconbtn--text v-iconbtn--sm"
                aria-label={`Quitar ${f.name}`}
                title={`Quitar ${f.name}`}
                disabled={p.disabled}
                onClick={() => p.onChange(p.files.filter((_, j) => j !== i))}
              >
                <Icon name="x" size={18} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </Field>
  );
}
