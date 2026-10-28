import { expect, test } from '@playwright/test';

test.use({ channel: process.env.E2E_BROWSER_CHANNEL });

const reporte = {
  desde: '2042-01-01', hasta: '2042-12-31', regimen: null,
  resumen: { unidadesOcupadas: 1, ocupacionPromedio: 50, eventosRealizados: 0 },
  porSalon: [{ salonId: 2, codigo: 'club', salon: 'Club', unidadesOcupadas: 1, unidadesDisponibles: 2, ocupacion: 50 }],
  porVendedoraYTipo: [{ vendedoraId: 7, vendedora: 'Lucía', tipoEventoId: 2, tipoEvento: 'Quince', eventosIncluidos: 1 }],
  filtros: { salones: [{ id: 2, nombre: 'Club' }], tiposEvento: [{ id: 2, nombre: 'Quince' }], vendedoras: [{ id: 7, nombre: 'Lucía' }] },
};

for (const rol of ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS']) {
  test('reporte, filtros e impresión con API simulada para ' + rol, async ({ page }) => {
    const consultas: string[] = [];
    await page.route('**/api/v1/**', async (route) => {
      const url = new URL(route.request().url());
      if (url.pathname.endsWith('/auth/refresh')) return route.fulfill({ json: {
        tokenAcceso: 'token-sintetico', tokenAccesoVence: '2099-01-01T00:00:00Z', minutosExpiracionSesion: 60, minutosAvisoExpiracion: 5,
        usuario: { id: 1, nombreCompleto: 'Usuario prueba', nombreUsuario: 'prueba', roles: [rol], debeCambiarContrasena: false },
      } });
      if (url.pathname.endsWith('/notificaciones/sin-leer')) return route.fulfill({ json: { cantidad: 0 } });
      if (url.pathname.endsWith('/reportes/ocupacion')) {
        consultas.push(url.search);
        return route.fulfill({ json: reporte });
      }
      return route.fulfill({ json: [] });
    });
    await page.goto('/reportes');
    if (rol === 'ADMINISTRACION' || rol === 'COMPRAS') {
      await expect(page.getByText('Tu perfil no tiene acceso a esta pantalla')).toBeVisible();
      expect(consultas).toEqual([]);
      await expect(page.locator('a[href="/reportes"]')).toHaveCount(0);
      return;
    }
    await expect(page.getByRole('img', { name: 'Club: 50 por ciento' })).toBeVisible();
    await page.getByLabel('Salón', { exact: true }).selectOption('2');
    await expect.poll(() => consultas.at(-1)).toContain('salonId=2');
    await page.evaluate(() => { window.print = () => { document.body.dataset.impreso = 'si'; }; });
    await page.getByRole('button', { name: 'Imprimir' }).click();
    await expect(page.locator('body')).toHaveAttribute('data-impreso', 'si');
    await page.emulateMedia({ media: 'print' });
    await expect(page.getByRole('form', { name: 'Filtros del reporte' })).toBeHidden();
    await expect(page.locator('.reporte-ocupacion__impresion')).toBeVisible();
    await expect(page.getByRole('table', { name: 'Ocupación por salón' })).toBeVisible();
    await expect(page.locator('.app__lateral')).toBeHidden();
  });
}
