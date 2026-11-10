/**
 * Títulos para agrupar la lista de eventos por cercanía: «Esta semana», «La semana que viene» y, después, el mes
 * («Diciembre»; con el año si no es el actual). Las semanas van de lunes a domingo. Fechas aaaa-mm-dd.
 */
const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];

function aDia(iso: string): number {
  const [a, m, d] = iso.split('-').map(Number);
  return Date.UTC(a, m - 1, d) / 86_400_000;
}

/** Lunes de la semana de una fecha, en días desde 1970. */
function lunes(iso: string): number {
  const dia = aDia(iso);
  const diaSemana = (new Date(dia * 86_400_000).getUTCDay() + 6) % 7; // 0 = lunes
  return dia - diaSemana;
}

export function grupoDeFecha(fecha: string, hoy: string): string {
  const semanas = (lunes(fecha) - lunes(hoy)) / 7;
  if (semanas <= 0) return 'Esta semana';
  if (semanas === 1) return 'La semana que viene';
  const mes = MESES[Number(fecha.slice(5, 7)) - 1];
  return fecha.slice(0, 4) === hoy.slice(0, 4) ? mes : `${mes} ${fecha.slice(0, 4)}`;
}

/** Agrupa una lista ya ordenada por fecha, conservando el orden. */
export function agruparPorFecha<T>(lista: T[], fechaDe: (item: T) => string, hoy: string): { titulo: string; items: T[] }[] {
  const grupos: { titulo: string; items: T[] }[] = [];
  for (const item of lista) {
    const titulo = grupoDeFecha(fechaDe(item), hoy);
    const ultimo = grupos[grupos.length - 1];
    if (ultimo?.titulo === titulo) ultimo.items.push(item);
    else grupos.push({ titulo, items: [item] });
  }
  return grupos;
}
