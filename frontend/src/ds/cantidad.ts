/**
 * Cantidades de bebida. El backend guarda y calcula todo en botellas; la caja (o el pack) es solo la forma de mostrar
 * y cargar, con las botellas que trae cada bulto (`unidades_por_bulto`).
 */

const NUMERO = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 });

/** «Caja» → «cajas», «Pack» → «packs», «Botella» → «botellas». */
export function plural(unidad: string): string {
  return `${unidad.toLowerCase()}s`;
}

function conUnidad(n: number, singular: string): string {
  return `${NUMERO.format(n)} ${n === 1 ? singular.toLowerCase() : plural(singular)}`;
}

/** Bultos enteros y botellas sueltas de una cantidad en botellas (sin signo). */
export function enBultos(botellas: number, porBulto: number): { bultos: number; sueltas: number } {
  const total = Math.abs(botellas);
  if (porBulto <= 1) return { bultos: 0, sueltas: total };
  const bultos = Math.floor(total / porBulto);
  return { bultos, sueltas: Math.round((total - bultos * porBulto) * 100) / 100 };
}

/**
 * «8 cajas y 3 botellas», «1 caja», «5 botellas», «−2 botellas». Con unidad Botella (o 1 por bulto), solo botellas.
 */
export function cantidadLegible(botellas: number, porBulto: number, unidad: string): string {
  const signo = botellas < 0 ? '−' : '';
  if (porBulto <= 1) return signo + conUnidad(Math.abs(botellas), 'Botella');
  const { bultos, sueltas } = enBultos(botellas, porBulto);
  const partes = [bultos > 0 && conUnidad(bultos, unidad), sueltas > 0 && conUnidad(sueltas, 'Botella')].filter(Boolean);
  return partes.length ? signo + partes.join(' y ') : '0 botellas';
}
