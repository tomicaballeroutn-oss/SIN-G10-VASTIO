import { describe, expect, it } from 'vitest';
import { agruparPorFecha, grupoDeFecha } from './gruposDeFecha';

// Martes 10 de noviembre de 2026: la semana va del lunes 9 al domingo 15.
const HOY = '2026-11-10';

describe('grupos por fecha', () => {
  it.each([
    ['2026-11-10', 'Esta semana'],
    ['2026-11-15', 'Esta semana'],
    ['2026-11-16', 'La semana que viene'],
    ['2026-11-22', 'La semana que viene'],
    ['2026-11-23', 'Noviembre'],
    ['2026-12-05', 'Diciembre'],
    ['2027-01-09', 'Enero 2027'],
  ])('%s → %s', (fecha, grupo) => {
    expect(grupoDeFecha(fecha, HOY)).toBe(grupo);
  });

  it('agrupa en orden y sin repetir títulos seguidos', () => {
    const grupos = agruparPorFecha(['2026-11-11', '2026-11-14', '2026-11-18', '2026-12-01'], (f) => f, HOY);
    expect(grupos.map((g) => [g.titulo, g.items.length])).toEqual([['Esta semana', 2], ['La semana que viene', 1], ['Diciembre', 1]]);
  });
});
