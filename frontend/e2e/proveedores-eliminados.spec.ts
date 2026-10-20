import { expect, test } from '@playwright/test';

const contrasena = process.env.E2E_DEMO_CONTRASENA ?? '';
test.skip(!contrasena, 'Requiere datos de demostración en una base aislada');

test('catálogo e inventario funcionan sin pantalla ni solicitudes de proveedores', async ({ page }) => {
  const solicitudes: string[] = [];
  page.on('request', (r) => solicitudes.push(r.url()));
  await page.goto('/login');
  await page.getByLabel('Usuario').fill('gabriela.paz');
  await page.getByLabel('Contraseña').fill(contrasena);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page).not.toHaveURL(/\/login/);
  await page.goto('/catalogo?pestana=proveedores');
  await expect(page.getByRole('table', { name: 'Bebidas' })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Proveedores' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Nueva bebida' }).click();
  await expect(page.getByRole('dialog').getByLabel(/Proveedor/)).toHaveCount(0);
  await page.getByRole('dialog').getByRole('button', { name: 'Cancelar' }).click();
  await page.getByRole('tab', { name: 'Carga inicial' }).click();
  await page.getByLabel('Ubicación').selectOption('2');
  const fernet = page.getByRole('group', { name: 'Cantidad inicial de Fernet Branca' });
  const sumar = fernet.getByRole('button', { name: 'Sumar 1' }).last();
  await sumar.click();
  await page.getByRole('button', { name: 'Guardar carga inicial' }).click();
  await expect(page.getByText('Carga inicial de Barra Avril guardada.')).toBeVisible();
  // Compensar el cambio mediante el propio inventario, conservando su registro de corrección.
  await fernet.getByRole('button', { name: 'Restar 1' }).last().click();
  await page.getByRole('button', { name: 'Guardar carga inicial' }).click();
  await expect(page.getByRole('button', { name: 'Guardar carga inicial' })).toBeDisabled();
  await page.goto('/ingreso');
  await expect(page.getByLabel('Número de remito')).toBeVisible();
  await expect(page.getByLabel(/Proveedor/)).toHaveCount(0);
  expect(solicitudes.filter((url) => url.includes('/api/v1/proveedores'))).toEqual([]);
});
