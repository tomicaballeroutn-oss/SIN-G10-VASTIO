import { describe, expect, it } from 'vitest';
import { resumenDeServicios } from './opcionesServicios';

const s = (categoria: string, ...textos: string[]) => ({ categoria, renglones: textos.map((texto) => ({ texto })) });

describe('resumen de servicios', () => {
  it('menús, bodega y barra en una línea, sin cantidades ni especiales', () => {
    expect(resumenDeServicios([
      s('Menú', 'Menú Tradición', 'Menú Infantil'), s('Menús especiales', 'Sin TACC'), s('Bodega', 'Reserva'), s('Tipo de barra', 'Barra de gin'),
    ])).toBe('Menú Tradición, Menú Infantil · Bodega Reserva · Barra de gin');
  });

  it('completa el nombre cuando la opción sola no se entiende', () => {
    expect(resumenDeServicios([s('Bodega', 'Bodega de Autor'), s('Tipo de barra', 'Sin alcohol')])).toBe('Bodega de Autor · Barra sin alcohol');
  });

  it('sin nada cargado, vacío', () => {
    expect(resumenDeServicios([s('Menú'), s('Otros', 'Cabina de fotos')])).toBe('');
  });
});
