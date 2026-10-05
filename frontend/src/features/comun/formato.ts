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
