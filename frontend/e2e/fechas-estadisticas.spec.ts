import { expect, test } from '@playwright/test';

// Navegador real y API simulada: control nativo de escritorio con teclado, en ambos anchos.
// El selector móvil evita la edición de segmentos; no se simula badInput en el DOM.
test.use({ channel: process.env.E2E_BROWSER_CHANNEL, isMobile: false, hasTouch: false });

for (const caso of ['incompleta', 'imposible'] as const) {
test(`una fecha ${caso} se rechaza y una fecha vacía intencional permite consultar`, async ({ page }) => {
  const consultas: string[] = [];
  await page.route('**/api/v1/**', async (route) => {
    const ruta = new URL(route.request().url()).pathname;
    let cuerpo: unknown;
    if (ruta.endsWith('/auth/refresh')) cuerpo = {
      tokenAcceso: 'token-sintetico', tokenAccesoVence: '2099-01-01T00:00:00Z',
      minutosExpiracionSesion: 60, minutosAvisoExpiracion: 5,
      usuario: { id: 1, nombreCompleto: 'Prueba B501', nombreUsuario: 'prueba.b501', roles: ['COMPRAS'], debeCambiarContrasena: false },
    };
    else if (ruta.endsWith('/notificaciones/sin-leer')) cuerpo = { cantidad: 0 };
    else if (ruta.endsWith('/estadisticas/consumo')) {
      consultas.push(route.request().url());
      cuerpo = { unidad: 'BOTELLAS', denominador: 'INVITADOS_REGISTRADOS', filtros: {},
        resumen: { eventos: 0, eventosConConsumoRegistrado: 0, consumoTotal: 0, promedioPorEvento: null,
          eventosConInvitadosValidos: 0, invitadosRegistrados: 0, consumoConInvitadosValidos: 0, porInvitadoRegistrado: null },
        porBebida: [], porTipoEvento: [], detalle: [] };
    } else cuerpo = [];
    await route.fulfill({ json: cuerpo });
  });
  await page.goto('/bebidas/estadisticas');
  await expect(page.getByText('Consultá el consumo histórico')).toBeVisible();
  // El control nativo conserva segmentos incompletos aunque input.value sea vacío.
  // Se navega con teclado, sin falsificar validity ni eventos del DOM.
  const fecha = page.getByLabel('Fecha desde');
  await fecha.fill(caso === 'incompleta' ? '2038-05-20' : '2038-01-31');
  await fecha.click();
  for (let i = 0; i < 3; i++) await fecha.press('ArrowLeft');
  if (caso === 'incompleta') await fecha.press('Delete');
  else {
    // Cambiar enero a febrero conservando 31 produce un día inexistente.
    // La posición del mes depende del idioma del navegador instalado.
    await fecha.press('ArrowUp');
    if (!(await fecha.evaluate((e: HTMLInputElement) => e.validity.badInput))) {
      await fecha.fill('2038-01-31');
      await fecha.click();
      for (let i = 0; i < 3; i++) await fecha.press('ArrowLeft');
      await fecha.press('ArrowRight');
      await fecha.press('ArrowUp');
    }
  }
  const nativo = await fecha.evaluate((e: HTMLInputElement) => ({ valor: e.value, badInput: e.validity.badInput }));
  expect(nativo).toEqual({ valor: '', badInput: true });
  await page.getByRole('button', { name: 'Consultar estadísticas' }).click();
  await expect(page.getByText(/Revisá el período:/)).toBeVisible();
  expect(consultas).toEqual([]);
  await fecha.fill('2038-05-01');
  await page.getByRole('button', { name: 'Consultar estadísticas' }).click();
  await expect(page.getByText('No hay eventos cerrados para estos filtros')).toBeVisible();
  expect(consultas.length).toBeGreaterThan(0);
  expect(consultas.every((url) => new URL(url).searchParams.get('desde') === '2038-05-01')).toBe(true);
  consultas.length = 0;
  await fecha.fill('');
  expect(await fecha.evaluate((e: HTMLInputElement) => e.validity.badInput)).toBe(false);
  await page.getByRole('button', { name: 'Consultar estadísticas' }).click();
  await expect(page.getByText('No hay eventos cerrados para estos filtros')).toBeVisible();
  await expect.poll(() => consultas.length).toBeGreaterThan(0);
  expect(consultas.every((url) => !new URL(url).searchParams.has('desde'))).toBe(true);
});
}
