import { expect, test } from '@playwright/test';

const usuario = process.env.E2E_USUARIO ?? 'direccion';
const contrasena = process.env.E2E_CONTRASENA ?? '';

test.skip(!contrasena, 'Definí E2E_CONTRASENA con la contraseña del usuario inicial de Dirección');

test('login incorrecto muestra el aviso y no entra', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Ingresá a tu cuenta' })).toBeVisible();

  await page.getByLabel('Usuario').fill(usuario);
  await page.getByLabel('Contraseña').fill('no-es-la-clave');
  await page.getByRole('button', { name: 'Ingresar' }).click();

  await expect(page.getByRole('alert')).toContainText('Usuario o contraseña incorrectos');
  await expect(page).toHaveURL(/\/login/);
});

test('Dirección inicia sesión, ve su menú, sigue con sesión al recargar y cierra sesión', async ({ page, isMobile }) => {
  await page.goto('/parametros');
  await page.getByLabel('Usuario').fill(usuario);
  await page.getByLabel('Contraseña').fill(contrasena);
  await page.getByRole('button', { name: 'Ingresar' }).click();

  await expect(page.getByRole('heading', { level: 1, name: 'Parámetros' })).toBeVisible();
  const menu = page.getByRole('navigation', { name: 'Navegación principal' }).locator('visible=true');
  await expect(menu.getByRole('button', { name: isMobile ? 'Más' : 'Usuarios y perfiles' })).toBeVisible();

  // El token de acceso vive en memoria: al recargar, la cookie HttpOnly renueva la sesión.
  await page.reload();
  await expect(page.getByRole('heading', { level: 1, name: 'Parámetros' })).toBeVisible();
  expect(await page.evaluate(() => JSON.stringify(localStorage) + document.cookie)).not.toMatch(/eyJ/);

  if (isMobile) {
    await page.getByRole('button', { name: 'Más' }).click();
  }
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Ingresá a tu cuenta' })).toBeVisible();

  await page.goto('/parametros');
  await expect(page).toHaveURL(/\/login\?volver=%2Fparametros/);
});
