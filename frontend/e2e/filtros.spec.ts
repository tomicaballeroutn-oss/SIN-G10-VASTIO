import { expect, test, type Page } from '@playwright/test';
import { AGENDA_FILTROS, EVENTOS_FILTROS } from '../src/test/filtrosFixtures';

test.use({ channel: process.env.E2E_BROWSER_CHANNEL });

async function backend(page: Page, vendedora = false) {
  const consultas: string[] = [];
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const ruta = url.pathname.replace('/api/v1', '');
    if (ruta === '/auth/refresh') return route.fulfill({ json: {
      tokenAcceso: 'token-sintetico', tokenAccesoVence: '2099-01-01T00:00:00Z',
      minutosExpiracionSesion: 60, minutosAvisoExpiracion: 5,
      usuario: { id: 1, nombreCompleto: 'Prueba de filtros', nombreUsuario: 'prueba',
        roles: vendedora ? ['VENDEDORA'] : ['DIRECCION'], debeCambiarContrasena: false },
    } });
    consultas.push(ruta + url.search);
    if (ruta === '/agenda') return route.fulfill({ json: AGENDA_FILTROS });
    if (ruta === '/eventos') return route.fulfill({ json: vendedora ? [EVENTOS_FILTROS[0]] : EVENTOS_FILTROS });
    if (ruta === '/notificaciones/sin-leer') return route.fulfill({ json: { cantidad: 0 } });
    return route.fulfill({ json: [] });
  });
  return consultas;
}

async function elegir(page: Page, nombre: string, opcion: string) {
  const disparador = page.getByRole('button', { name: new RegExp(`^${nombre}`) });
  await disparador.click();
  const grupo = page.getByRole('group', { name: nombre, exact: true });
  await expect(grupo).toBeVisible();
  const panel = grupo.locator('..');
  const caja = await panel.boundingBox();
  const viewport = page.viewportSize()!;
  expect(caja).not.toBeNull();
  expect(caja!.x).toBeGreaterThanOrEqual(0);
  expect(caja!.x + caja!.width).toBeLessThanOrEqual(viewport.width + 1);
  expect(caja!.y).toBeGreaterThanOrEqual(0);
  expect(caja!.y + caja!.height).toBeLessThanOrEqual(viewport.height + 1);
  const casilla = grupo.getByRole('checkbox', { name: opcion, exact: true });
  // Agenda confirma la selección al completar la transición de la URL.
  await casilla.click();
  await expect(casilla).toBeChecked();
  await page.getByRole('button', { name: 'Listo', exact: true }).click();
  await expect(disparador).toBeFocused();
}

test('Agenda resalta sin ocultar ocupación ni ampliar el detalle ajeno', async ({ page }, info) => {
  await backend(page, true);
  await page.goto('/agenda?mes=2030-03');
  const dia = page.getByRole('button', { name: /^sábado 9 de marzo/ });
  await expect(dia.locator('.v-pip')).toHaveCount(6);
  const descripcion = await dia.getAttribute('aria-label');
  await page.getByRole('button', { name: 'Mostrar filtros' }).click();
  await elegir(page, 'Resaltar estados', 'Disponible');
  await expect(dia.locator('.is-match')).toHaveCount(3);
  await expect(dia.locator('.is-dim')).toHaveCount(3);
  await expect(dia).toHaveAttribute('aria-label', descripcion!);
  await page.screenshot({ path: info.outputPath('agenda-resaltada.png'), fullPage: true });
  await page.getByRole('button', { name: 'Ocultar filtros' }).click();
  await dia.click();
  await expect(page.getByRole('tabpanel', { name: 'Avril' })).toContainText('Quince de Delfina');
  await page.getByRole('tab', { name: /^Club de Campo/ }).click();
  const detalle = page.getByRole('tabpanel', { name: 'Club de Campo' });
  await expect(detalle).toContainText('La tiene Sofía Méndez.');
  await expect(detalle).toContainText('Mantenimiento');
  await expect(detalle.getByRole('button')).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('Eventos combina búsqueda, estado y fecha; recupera el vacío y conserva navegación', async ({ page }, info) => {
  await backend(page);
  await page.goto('/eventos');
  await expect(page.locator('.eventos__lista > li')).toHaveCount(3);
  await page.getByRole('button', { name: 'Mostrar filtros' }).click();
  await page.getByLabel('Buscar eventos').fill('RIOS delfina');
  await elegir(page, 'Estados', 'Señado');
  await page.getByLabel('Desde', { exact: true }).fill('2030-03-09');
  await page.getByLabel('Hasta', { exact: true }).fill('2030-03-09');
  await expect(page.locator('.eventos__lista > li')).toHaveCount(1);
  await expect(page.getByRole('heading', { name: 'Quince de Delfina' })).toBeVisible();
  await page.getByLabel('Desde', { exact: true }).fill('2030-03-10');
  await expect(page.getByRole('alert')).toContainText('La fecha Desde no puede ser posterior a Hasta.');
  await expect(page.getByText('No hay eventos que coincidan')).toBeVisible();
  await page.getByRole('button', { name: 'Limpiar filtros', exact: true }).first().click();
  await expect(page.locator('.eventos__lista > li')).toHaveCount(3);
  await page.screenshot({ path: info.outputPath('eventos-filtros.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Ocultar filtros' }).click();
  await page.getByRole('heading', { name: 'Quince de Delfina' }).click();
  await expect(page).toHaveURL(/\/eventos\/1$/);
});

test('las opciones de filtros se limitan a los datos autorizados recibidos', async ({ page }) => {
  const consultas = await backend(page, true);
  await page.goto('/eventos');
  await expect(page.locator('.eventos__lista > li')).toHaveCount(1);
  await page.getByRole('button', { name: 'Mostrar filtros' }).click();
  await page.getByRole('button', { name: 'Vendedoras', exact: true }).click();
  const opciones = page.getByRole('group', { name: 'Vendedoras', exact: true });
  await expect(opciones.getByRole('checkbox')).toHaveCount(1);
  await expect(opciones.getByRole('checkbox', { name: 'Lucía Ferreyra' })).toBeVisible();
  await expect(page.getByText('Sofía Méndez', { exact: true })).toHaveCount(0);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button', { name: 'Vendedoras', exact: true })).toBeFocused();
  expect(consultas.every((ruta) => ruta === '/eventos' || ruta.startsWith('/notificaciones'))).toBe(true);
});
