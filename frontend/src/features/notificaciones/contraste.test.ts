/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
const css = readFileSync('src/features/notificaciones/notificaciones.css', 'utf8');
const tokens = readFileSync('src/ds/tokens.css', 'utf8');

const colores = (texto: string) => Object.fromEntries([...texto.matchAll(/--([\w-]+):\s*(#[\da-f]{6})/gi)].map((m) => [m[1], m[2]]));
const rgb = (hex: string) => hex.slice(1).match(/../g)!.map((v) => parseInt(v, 16));
const luminancia = (c: number[]) => c.map((v) => v / 255).map((v) => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4)
  .reduce((s, v, i) => s + v * [.2126, .7152, .0722][i], 0);
const contraste = (a: number[], b: number[]) => {
  const x = luminancia(a), y = luminancia(b);
  return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
};

describe('M-08 · contraste WCAG de íconos informativos (mínimo 3:1)', () => {
  for (const oscuro of [false, true]) {
    const [base, dark] = tokens.split('[data-theme="dark"]');
    const superficies = colores(oscuro ? dark : base);
    const [claro, noche] = css.split('[data-theme="dark"]');
    const paleta = { ...colores(oscuro ? noche : claro), 'calendar-pendiente': superficies.warning };
    for (const [tipo, hex] of Object.entries(paleta)) {
      it(`${oscuro ? 'oscuro' : 'claro'}: ${tipo}, leído, hover y sin leer`, () => {
        const icono = rgb(hex);
        for (const superficie of ['surface', 'surface-raised', 'surface-sunken', 'canvas']) {
          expect(contraste(icono, rgb(superficies[superficie]))).toBeGreaterThanOrEqual(3);
        }
        const mezclas = [...css.matchAll(/var\(--aviso-tipo\) (\d+)%/g)];
        const peso = Number(mezclas[oscuro ? 1 : 0][1]) / 100;
        const fondo = rgb(superficies.surface).map((v, i) => v * (1 - peso) + icono[i] * peso);
        expect(contraste(icono, fondo)).toBeGreaterThanOrEqual(3);
      });
    }
    it(`cubre diez tokens en ${oscuro ? 'oscuro' : 'claro'}`, () => expect(Object.keys(paleta)).toHaveLength(10));
  }
});
