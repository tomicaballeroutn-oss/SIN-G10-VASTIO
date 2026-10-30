import { expect, test } from '@playwright/test';

test.use({ channel: process.env.E2E_BROWSER_CHANNEL });
const tipos = ['EVENTO_NUEVO', 'SENA', 'CONTRATO', 'CONFIRMACION', 'MODIFICACION', 'REPROGRAMACION', 'CANCELACION', 'PLANNER_ASIGNADA', 'ALERTA_STOCK', 'CALENDAR_PENDIENTE'];

for (const tema of ['claro', 'oscuro', 'sistema']) {
  test(`colores: ${tema}, contraste y estados con API simulada`, async ({ page }, info) => {
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.addInitScript((valor) => localStorage.setItem('vastio.tema', valor), tema);
    const avisos = tipos.flatMap((tipo, i) => [false, true].map((leida) => ({
      id: i * 2 + Number(leida), tipo, leida, mensaje: `Aviso ${tipo}`, eventoId: null, fechaHora: '2026-10-30T10:00:00-03:00',
    })));
    await page.route('**/api/v1/**', async (route) => {
      const url = new URL(route.request().url());
      if (url.pathname.endsWith('/auth/refresh')) return route.fulfill({ json: {
        tokenAcceso: 'sintetico', tokenAccesoVence: '2099-01-01T00:00:00Z', minutosExpiracionSesion: 60, minutosAvisoExpiracion: 5,
        usuario: { id: 1, nombreCompleto: 'Prueba visual', nombreUsuario: 'prueba', roles: ['DIRECCION'], debeCambiarContrasena: false },
      } });
      if (url.pathname.endsWith('/notificaciones/sin-leer')) return route.fulfill({ json: { cantidad: tipos.length } });
      if (url.pathname.endsWith('/notificaciones')) return route.fulfill({ json: {
        notificaciones: url.searchParams.get('soloSinLeer') === 'true' ? avisos.filter((a) => !a.leida) : avisos,
        pagina: 0, hayMas: false, sinLeer: tipos.length,
      } });
      return route.fulfill({ json: [] });
    });
    await page.goto('/notificaciones');
    await expect(page.locator('html')).toHaveAttribute('data-theme', tema === 'claro' ? 'light' : 'dark');
    await page.getByRole('tab', { name: 'Todas', exact: true }).click();
    const botones = page.locator('.avisos__aviso');
    await expect(botones).toHaveCount(tipos.length * 2);
    const calendar = page.locator('.avisos__aviso--calendar-pendiente');
    await expect(calendar).toHaveCount(2);
    await expect(calendar.filter({ hasText: 'Sin leer:' })).toHaveCount(1);
    const contrastes: unknown[] = [];
    for (const hover of [false, true]) {
      for (const boton of await botones.all()) {
        if (hover) await boton.hover();
        const resultado = await boton.evaluate((el) => {
          const rgb = (value: string) => {
            const canvas = document.createElement('canvas'); canvas.width = canvas.height = 1;
            const ctx = canvas.getContext('2d')!; ctx.fillStyle = value; ctx.fillRect(0, 0, 1, 1);
            return [...ctx.getImageData(0, 0, 1, 1).data].slice(0, 3);
          };
          const lum = (c: number[]) => c.map((v) => v / 255).map((v) => v <= .04045 ? v / 12.92 : ((v + .055) / 1.055) ** 2.4)
            .reduce((s, v, i) => s + v * [.2126, .7152, .0722][i], 0);
          let fondo: Element | null = el;
          while (fondo && getComputedStyle(fondo).backgroundColor === 'rgba(0, 0, 0, 0)') fondo = fondo.parentElement;
          if (!fondo) throw new Error('Superficie opaca no encontrada');
          const icono = el.querySelector(':scope > svg.v-icon')!;
          const color = getComputedStyle(icono).color;
          const a = lum(rgb(color)), b = lum(rgb(getComputedStyle(fondo).backgroundColor));
          const token = getComputedStyle(el).getPropertyValue('--aviso-tipo').trim();
          return { clase: el.className, color, token, aplicaToken: JSON.stringify(rgb(color)) === JSON.stringify(rgb(token)),
            contraste: (Math.max(a, b) + .05) / (Math.min(a, b) + .05), stroke: getComputedStyle(icono).stroke };
        });
        expect(resultado.token).not.toBe('');
        expect(resultado.aplicaToken).toBe(true);
        expect(resultado.stroke).toBe(resultado.color);
        expect(resultado.contraste, JSON.stringify(resultado)).toBeGreaterThanOrEqual(3);
        contrastes.push({ hover, ...resultado });
      }
    }
    // Diez tipos reales, nueve trazos: Calendar y reprogramación comparten calendar-clock.
    // El mensaje explícito conserva su distinción sin depender del color.
    const trazos = await botones.locator('svg').evaluateAll((icons) => icons.filter((_, i) => i % 2 === 0).map((el) => el.innerHTML));
    expect(new Set(trazos).size).toBe(9);
    await expect(page.locator('.avisos__aviso.is-nuevo .v-sr').first()).toHaveText('Sin leer: ');
    await expect(page.locator('.avisos__aviso:not(.is-nuevo) .avisos__punto')).toHaveCount(0);
    await info.attach('contrastes', { body: JSON.stringify(contrastes, null, 2), contentType: 'application/json' });
    // Expande solo la lista del fixture para documentar todos los tipos en una captura.
    await page.addStyleTag({ content: '.avisos__lista { max-height: none !important; overflow: visible !important; }' });
    await page.mouse.move(0, 0);
    await page.screenshot({ path: info.outputPath(`${tema}.png`), fullPage: true });
    await info.attach(`${tema}`, { path: info.outputPath(`${tema}.png`), contentType: 'image/png' });
  });
}
