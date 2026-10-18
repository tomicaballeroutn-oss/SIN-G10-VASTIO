import { expect, test, type Browser, type Page } from '@playwright/test';

/**
 * Objetivo del Sprint 3: la mercadería entra al sistema y cada ubicación sabe cuánto tiene.
 * Compras carga una bebida, registra su ingreso al depósito madre, ve el saldo en Existencias y lo corrige con un
 * recuento. La encargada de barra, sin evento en la jornada, no ve stock. Usa los datos de demostración (perfil dev con
 * VASTIO_DEMO_CONTRASENA): nicolas.herrera (Compras) y rocio.blanco (Barra).
 *   $env:E2E_DEMO_CONTRASENA='...'; npm run e2e
 */
const contrasena = process.env.E2E_DEMO_CONTRASENA ?? '';

test.skip(!contrasena, 'Definí E2E_DEMO_CONTRASENA con la contraseña de los datos de demostración');

async function ingresar(browser: Browser, usuario: string, opciones: Parameters<Browser['newContext']>[0]): Promise<Page> {
  const pagina = await (await browser.newContext(opciones)).newPage();
  await pagina.goto('/login');
  await pagina.getByLabel('Usuario').fill(usuario);
  await pagina.getByLabel('Contraseña').fill(contrasena);
  await pagina.getByRole('button', { name: 'Ingresar' }).click();
  // La pantalla de ingreso también tiene un h1: hay que esperar a salir de ella.
  await expect(pagina).not.toHaveURL(/\/login/);
  await expect(pagina.getByRole('heading', { level: 1 })).toBeVisible();
  return pagina;
}

test('una bebida nueva entra al depósito, se ve en Existencias y se corrige con un recuento', async ({ browser, isMobile, viewport, userAgent }) => {
  test.slow();
  const contexto = { viewport, userAgent, isMobile, hasTouch: isMobile, ignoreHTTPSErrors: process.env.E2E_IGNORAR_CERTIFICADO === '1' };
  const compras = await ingresar(browser, 'nicolas.herrera', contexto);
  const marca = Date.now();
  const nombre = `Ron E2E ${marca}`;
  // DUN-14 inventado y distinto en cada corrida.
  const codigo = `99${String(marca).slice(-12)}`;

  // 1. Catálogo: la bebida con su código de caja.
  await compras.goto('/catalogo');
  await compras.getByRole('button', { name: 'Nueva bebida' }).click();
  const alta = compras.getByRole('dialog', { name: 'Nueva bebida' });
  await alta.getByLabel('Nombre').fill(nombre);
  await alta.getByLabel('Presentación').fill('700 ml');
  await alta.getByLabel('Tipo').selectOption({ label: 'Destilado' });
  await alta.getByLabel('Se mueve en').selectOption({ label: 'Caja' });
  await alta.getByLabel('Botellas por bulto').fill('6');
  await alta.getByLabel('Agregar código').fill(codigo);
  await alta.getByRole('button', { name: 'Agregar código' }).click();
  await alta.getByRole('button', { name: 'Cargar bebida' }).click();
  await expect(compras.getByText(`Bebida cargada: ${nombre} 700 ml.`)).toBeVisible();

  // 2. Ingreso: dos cajas al depósito madre, revisadas antes de registrar.
  await compras.goto('/ingreso');
  await compras.getByRole('combobox', { name: 'Agregar bebida' }).fill(codigo);
  await compras.getByRole('option', { name: new RegExp(nombre) }).click();
  const cantidad = compras.getByRole('group', { name: `Cantidad de ${nombre}` });
  await cantidad.getByRole('button', { name: 'Sumar 1' }).first().click();
  await cantidad.getByRole('button', { name: 'Sumar 1' }).first().click();
  await compras.getByRole('button', { name: 'Revisar ingreso' }).click();
  const revision = compras.getByRole('dialog', { name: 'Revisá el ingreso' });
  await expect(revision).toContainText('2 cajas');
  await revision.getByRole('button', { name: 'Registrar ingreso' }).click();
  await expect(compras.getByText(/Ingreso registrado: 1 bebida al /)).toBeVisible();

  // 3. Existencias: el depósito madre la tiene.
  await compras.goto('/existencias');
  const tarjeta = compras.locator('li').filter({ hasText: nombre });
  await expect(tarjeta).toContainText('2 cajas');

  // 4. Recuento: había una botella suelta más; queda el ajuste y el saldo corregido.
  await tarjeta.getByRole('button', { name: 'Registrar recuento' }).click();
  const recuento = compras.getByRole('dialog', { name: 'Registrar recuento' });
  await recuento.getByRole('button', { name: 'Sumar 1' }).nth(1).click();
  await expect(recuento.getByText('Se registra un ajuste de +1 botella.')).toBeVisible();
  await recuento.getByRole('button', { name: 'Registrar recuento' }).click();
  await expect(compras.getByText(`Recuento de ${nombre} 700 ml registrado`, { exact: false })).toBeVisible();
  await expect(tarjeta).toContainText('2 cajas y 1 botella');

  // 5. La encargada de barra no ve stock si no tiene evento en la jornada (los de demostración son de otros días).
  const barra = await ingresar(browser, 'rocio.blanco', contexto);
  await barra.goto('/existencias');
  await expect(barra.getByText('Hoy no hay eventos en tus barras')).toBeVisible();
});
