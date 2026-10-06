import { useId } from 'react';

export function cx(...clases: (string | false | null | undefined)[]): string {
  return clases.filter(Boolean).join(' ');
}

/** Id estable para vincular label, input y mensaje. Respeta el id que venga por props. */
export function useFieldId(id?: string): string {
  const generado = useId();
  return id ?? 'v' + generado.replace(/[^a-zA-Z0-9]/g, '');
}

/** Tamaño legible: «850 KB», «2,4 MB». */
export function tamanoLegible(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toLocaleString('es-AR', { maximumFractionDigits: 1 })} MB`;
}

export function iniciales(nombre: string): string {
  const palabras = String(nombre || '?').trim().split(/\s+/);
  const primera = (palabras[0] || '?').charAt(0);
  const ultima = palabras.length > 1 ? palabras[palabras.length - 1].charAt(0) : '';
  return (primera + ultima).toUpperCase();
}
