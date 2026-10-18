import type { BebidaParaRetiro } from '../../api/entregas';
import { enBultos, plural } from '../../ds';

/** Una bebida del retiro, en botellas. */
export interface RenglonRetiro {
  bebida: Pick<BebidaParaRetiro, 'unidad' | 'unidadesPorBulto'>;
  cantidad: number;
}

/** «Salen 6 cajas y 2 botellas a Barra Avril.» Suma los bultos por unidad y las botellas sueltas de todos los renglones. */
export function resumenDeSalida(renglones: RenglonRetiro[], barra: string): string {
  const porUnidad = new Map<string, number>();
  let botellas = 0;
  for (const r of renglones) {
    if (r.bebida.unidadesPorBulto <= 1) {
      botellas += r.cantidad;
      continue;
    }
    const { bultos, sueltas } = enBultos(r.cantidad, r.bebida.unidadesPorBulto);
    if (bultos > 0) porUnidad.set(r.bebida.unidad, (porUnidad.get(r.bebida.unidad) ?? 0) + bultos);
    botellas += sueltas;
  }
  const partes = [...porUnidad].map(([unidad, n]) => `${n} ${n === 1 ? unidad.toLowerCase() : plural(unidad)}`);
  if (botellas > 0) partes.push(`${botellas} ${botellas === 1 ? 'botella' : 'botellas'}`);
  const texto = partes.length > 1 ? `${partes.slice(0, -1).join(', ')} y ${partes.at(-1)}` : partes[0] ?? '0 botellas';
  const total = [...porUnidad.values()].reduce((a, n) => a + n, 0) + botellas;
  return `${total === 1 ? 'Sale' : 'Salen'} ${texto} a ${barra}.`;
}
