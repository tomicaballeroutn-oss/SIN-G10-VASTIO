/** Formatos de fecha, hora e importes para las pantallas. Siempre en la zona de Córdoba. */

const ZONA = 'America/Argentina/Cordoba';

/** Fecha de calendario (yyyy-mm-dd) a mediodía UTC, para que ninguna zona la corra de día. */
function calendario(iso: string): Date {
  return new Date(`${iso}T12:00:00Z`);
}

/** Sin comas ni puntos de abreviatura; «sept» como «sep», igual que los prototipos. */
function sinPuntuacion(texto: string): string {
  return texto.replace(/,/g, '').replace(/\./g, '').replace(/\bsept\b/g, 'sep');
}

/** Hoy en Córdoba como fecha de calendario: «2026-10-07». */
export function hoy(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONA, year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}

/** «2026-10-10» → «sáb 10 oct». Con año: «sáb 10 oct 2026». */
export function fechaCorta(iso: string, conAnio = false): string {
  return sinPuntuacion(new Intl.DateTimeFormat('es-AR', {
    timeZone: 'UTC', weekday: 'short', day: 'numeric', month: 'short', ...(conAnio ? { year: 'numeric' } : {}),
  }).format(calendario(iso)));
}

/** «2026-09-21T10:15:00-03:00» → «lun 21 sep 10:15». */
export function fechaHora(isoConHora: string): string {
  const partes = new Intl.DateTimeFormat('es-AR', {
    timeZone: ZONA, weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false,
  }).formatToParts(new Date(isoConHora));
  const de = (tipo: Intl.DateTimeFormatPartTypes) => sinPuntuacion(partes.find((p) => p.type === tipo)?.value ?? '');
  return `${de('weekday')} ${de('day')} ${de('month')} ${de('hour')}:${de('minute')}`;
}

/** 150000 → «$ 150.000,00». */
export function pesos(importe: number): string {
  return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(importe).replace(/\s+/gu, ' ');
}

/**
 * Lee un importe escrito a mano: «150.000,50», «150000,5», «150000.50» o «150.000».
 * Con coma, el punto es separador de miles; sin coma, solo si agrupa de a tres. Null si no es un número.
 */
export function leerImporte(texto: string): number | null {
  const limpio = texto.replace(/[\s$]/g, '');
  if (!limpio) return null;
  let normal: string;
  if (limpio.includes(',')) normal = limpio.replace(/\./g, '').replace(',', '.');
  else if (/^\d{1,3}(\.\d{3})+$/.test(limpio)) normal = limpio.replace(/\./g, '');
  else normal = limpio;
  if (!/^\d+(\.\d+)?$/.test(normal)) return null;
  return Number(normal);
}
