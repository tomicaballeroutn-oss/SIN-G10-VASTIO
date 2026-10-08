import { describe, expect, it } from 'vitest';
import { cantidadLegible, enBultos, plural } from './cantidad';

describe('cantidades de bebida (se guardan en botellas)', () => {
  it('muestra cajas y botellas sueltas', () => {
    expect(cantidadLegible(51, 6, 'Caja')).toBe('8 cajas y 3 botellas');
    expect(cantidadLegible(6, 6, 'Caja')).toBe('1 caja');
    expect(cantidadLegible(1, 12, 'Caja')).toBe('1 botella');
    expect(cantidadLegible(0, 6, 'Caja')).toBe('0 botellas');
    expect(cantidadLegible(48, 24, 'Pack')).toBe('2 packs');
  });

  it('con unidad Botella, solo botellas', () => {
    expect(cantidadLegible(7, 1, 'Botella')).toBe('7 botellas');
  });

  it('un saldo negativo lleva el signo y una botella abierta, decimales', () => {
    expect(cantidadLegible(-8, 6, 'Caja')).toBe('−1 caja y 2 botellas');
    expect(cantidadLegible(6.5, 6, 'Caja')).toBe('1 caja y 0,5 botellas');
  });

  it('separa bultos enteros y sueltas', () => {
    expect(enBultos(27, 12)).toEqual({ bultos: 2, sueltas: 3 });
    expect(enBultos(5, 1)).toEqual({ bultos: 0, sueltas: 5 });
    expect(plural('Caja')).toBe('cajas');
  });
});
