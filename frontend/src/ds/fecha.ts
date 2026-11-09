/* Fechas como las ve la gente (día/mes/año) y como viajan (aaaa-mm-dd). */

/** aaaa-mm-dd → dd/mm/aaaa. Vacío o inválido → ''. */
export function fechaDdMmAaaa(iso: string | undefined): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? '');
  return m ? `${m[3]}/${m[2]}/${m[1]}` : '';
}

/** dd/mm/aaaa (o aaaa-mm-dd, por si se pega) → aaaa-mm-dd si es una fecha que existe; si no, null. */
export function fechaIso(texto: string): string | null {
  const iso = /^(\d{4})-(\d{2})-(\d{2})$/.exec(texto);
  const dmy = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(texto);
  const [a, m, d] = iso ? [iso[1], iso[2], iso[3]] : dmy ? [dmy[3], dmy[2], dmy[1]] : [];
  if (!a) return null;
  const fecha = new Date(Date.UTC(Number(a), Number(m) - 1, Number(d)));
  const valida = fecha.getUTCFullYear() === Number(a) && fecha.getUTCMonth() === Number(m) - 1 && fecha.getUTCDate() === Number(d);
  return valida ? `${a}-${m}-${d}` : null;
}
