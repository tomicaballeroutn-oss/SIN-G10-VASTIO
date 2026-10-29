import { expect, test } from '@playwright/test';

test.use({ channel: process.env.E2E_BROWSER_CHANNEL });

test('Mi cuenta: primer ingreso, fallo de renovación, recuperación, tema y salida con API simulada', async ({ page }, info) => {
  let debeCambiar = true;
  let fallaRefresco = false;
  let cambios = 0;
  let notificaciones = 0;
  await page.route('**/api/v1/**', async (route) => {
    const ruta = new URL(route.request().url()).pathname;
    if (ruta.endsWith('/auth/refresh')) {
      if (fallaRefresco) return route.fulfill({ status: 503, json: { status: 503, detail: 'Servicio temporalmente no disponible' } });
      return route.fulfill({ json: {
        tokenAcceso: 'token-sintetico', tokenAccesoVence: '2099-01-01T00:00:00Z',
        minutosExpiracionSesion: 60, minutosAvisoExpiracion: 5,
        usuario: { id: 1, nombreCompleto: 'Cuenta de prueba', nombreUsuario: 'prueba', roles: ['DIRECCION', 'COMPRAS'], debeCambiarContrasena: debeCambiar },
      } });
    }
    if (ruta.endsWith('/cuenta/contrasena')) {
      expect(route.request().postDataJSON()).toEqual({ contrasenaActual: 'inicial-prueba', contrasenaNueva: 'nueva-contrasena' });
      cambios++;
      debeCambiar = false;
      fallaRefresco = true;
      return route.fulfill({ status: 204 });
    }
    if (ruta.endsWith('/auth/logout')) return route.fulfill({ status: 204 });
    if (ruta.endsWith('/notificaciones/sin-leer')) {
      notificaciones++;
      return route.fulfill({ json: { cantidad: 0 } });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto('/mas');
  await expect(page).toHaveURL(/\/mi-cuenta$/);
  await expect(page.getByText('Elegí tu contraseña')).toBeVisible();
  await expect(page.locator('nav[aria-label="Navegación principal"]')).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Notificaciones/ })).toHaveCount(0);
  expect(notificaciones).toBe(0);
  await page.getByLabel('Contraseña actual', { exact: true }).fill('inicial-prueba');
  await page.getByLabel('Contraseña nueva', { exact: true }).fill('nueva-contrasena');
  await page.getByLabel('Repetí la contraseña nueva', { exact: true }).fill('nueva-contrasena');
  await page.getByRole('button', { name: 'Actualizar contraseña', exact: true }).click();
  await expect(page.getByText('Contraseña guardada; sesión sin actualizar')).toBeVisible();
  await expect(page.getByText('Contraseña actualizada', { exact: true })).toHaveCount(0);
  await expect(page.locator('nav[aria-label="Navegación principal"]')).toHaveCount(0);
  expect(notificaciones).toBe(0);
  await page.screenshot({ path: info.outputPath('recuperacion.png'), fullPage: true });
  fallaRefresco = false;
  await page.getByRole('button', { name: 'Reintentar actualizar sesión' }).click();
  await expect(page.getByText('Contraseña actualizada', { exact: true })).toBeVisible();
  await expect(page.getByText('Elegí tu contraseña')).toHaveCount(0);
  expect(cambios).toBe(1);
  await expect(page.locator('nav[aria-label="Navegación principal"]')).toHaveCount(2);
  await expect.poll(() => notificaciones).toBeGreaterThan(0);
  await page.getByRole('tab', { name: 'Oscuro', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.reload();
  await expect(page.getByRole('tab', { name: 'Oscuro', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByText('Elegí tu contraseña')).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('mi-cuenta.png'), fullPage: true });
  await page.goto('/mas');
  await expect(page.getByRole('heading', { name: 'Más', exact: true })).toBeVisible();
  await page.goto('/mi-cuenta');
  await page.locator('section.pantalla').getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Ingresar', exact: true })).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});

test('con contraseña inicial permite cerrar sesión sin montar navegación ni notificaciones', async ({ page }) => {
  const comerciales: string[] = [];
  await page.route('**/api/v1/**', async (route) => {
    const ruta = new URL(route.request().url()).pathname;
    if (ruta.endsWith('/auth/refresh')) return route.fulfill({ json: {
      tokenAcceso: 'token-sintetico', tokenAccesoVence: '2099-01-01T00:00:00Z',
      minutosExpiracionSesion: 60, minutosAvisoExpiracion: 5,
      usuario: { id: 1, nombreCompleto: 'Cuenta inicial', nombreUsuario: 'inicial', roles: ['DIRECCION'], debeCambiarContrasena: true },
    } });
    if (ruta.endsWith('/auth/logout')) return route.fulfill({ status: 204 });
    comerciales.push(ruta);
    return route.fulfill({ json: [] });
  });
  await page.goto('/eventos');
  await expect(page).toHaveURL(/\/mi-cuenta$/);
  await expect(page.locator('nav[aria-label="Navegación principal"]')).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Notificaciones/ })).toHaveCount(0);
  await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Ingresar', exact: true })).toBeVisible();
  expect(comerciales).toEqual([]);
});
