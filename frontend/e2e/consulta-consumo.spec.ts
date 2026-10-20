import { expect, test } from '@playwright/test';

/** Consulta de solo lectura contra el backend real. Requiere demo y un evento cerrado preparado en la base de prueba. */
const contrasena = process.env.E2E_DEMO_CONTRASENA ?? '';
const eventoId = process.env.E2E_CONSUMO_EVENTO ?? '';
test.use({ channel: process.env.E2E_BROWSER_CHANNEL });
test.skip(!contrasena || !eventoId, 'Definí E2E_DEMO_CONTRASENA y E2E_CONSUMO_EVENTO (evento cerrado en la base de prueba)');

test('Administración consulta desde la ficha y el detalle coincide con el backend', async ({ page }, testInfo) => {
  await page.goto('/login');
  await page.getByLabel('Usuario').fill('gabriela.paz');
  await page.getByLabel('Contraseña').fill(contrasena);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page).not.toHaveURL(/\/login/);
  await page.goto(`/eventos/${eventoId}`);
  const respuesta = page.waitForResponse((r) => r.url().endsWith(`/eventos/${eventoId}/consumo`) && r.request().method() === 'GET');
  await page.getByRole('tab', { name: 'Consumo de bebida' }).click();
  const consulta = await respuesta;
  expect(consulta.status()).toBe(200);
  const datos = await consulta.json();
  expect(datos.denominador).toBe('INVITADOS');
  await expect(page.getByText('Consumo consolidado al cierre')).toBeVisible();
  const tabla = page.getByRole('table', { name: 'Consumo por bebida' });
  for (const renglon of datos.renglones) {
    await expect(tabla.getByRole('row').filter({ hasText: `${renglon.nombre} ${renglon.presentacion}` })).toBeVisible();
  }
  const numero = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 4 });
  const total = page.getByRole('heading', { name: 'Consumo total', exact: true }).locator('xpath=ancestor::section[1]');
  await expect(total).toContainText(numero.format(datos.consumoTotal));
  await expect(page.getByText(`Calculado sobre ${datos.invitados} invitados, no sobre asistencia real.`)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cerrar evento', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Guardar', exact: true })).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath('consumo.png'), fullPage: true });
});
