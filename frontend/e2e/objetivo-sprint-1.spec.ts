import { expect, test, type Browser, type Page } from '@playwright/test';

/**
 * Objetivo del Sprint 1: una vendedora ve la agenda, aparta una fecha y registra la seña sin pisar a otra.
 * Usa los datos de demostración (perfil dev con VASTIO_DEMO_CONTRASENA): lucia.ferreyra y sofia.mendez.
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
  await expect(pagina.getByRole('heading', { level: 1 })).toBeVisible();
  return pagina;
}

/** Santa Bárbara al mediodía de ese día, en el detalle de la agenda. */
function unidad(pagina: Page) {
  return pagina.locator('.agenda__turno').filter({ hasText: 'Mediodía' }).locator('li').filter({ hasText: 'Santa Bárbara' });
}

/** Un día libre lejos de los datos de demostración; cada corrida prueba otros hasta encontrar uno. */
async function diaLibre(pagina: Page): Promise<string> {
  for (let intento = 0; intento < 10; intento++) {
    const fecha = new Date(Date.now() + (90 + Math.floor(Math.random() * 600)) * 86_400_000).toISOString().slice(0, 10);
    await pagina.goto(`/agenda?mes=${fecha.slice(0, 7)}&dia=${fecha}`);
    await expect(pagina.getByText('Día elegido')).toBeVisible();
    if (await unidad(pagina).getByRole('button', { name: 'Registrar pre-reserva' }).isVisible()) return fecha;
  }
  throw new Error('No encontré un día libre para la prueba');
}

test('dos vendedoras van por la misma fecha: entra una sola y la seña la deja firme', async ({ browser, isMobile, viewport, userAgent }) => {
  test.slow();
  const contexto = { viewport, userAgent, isMobile, hasTouch: isMobile, ignoreHTTPSErrors: process.env.E2E_IGNORAR_CERTIFICADO === '1' };
  const lucia = await ingresar(browser, 'lucia.ferreyra', contexto);
  const sofia = await ingresar(browser, 'sofia.mendez', contexto);
  const cliente = `Cliente E2E ${Date.now()}`;

  // Las dos abren la misma unidad libre.
  const fecha = await diaLibre(lucia);
  await sofia.goto(`/agenda?mes=${fecha.slice(0, 7)}&dia=${fecha}`);
  for (const pagina of [lucia, sofia]) {
    await unidad(pagina).getByRole('button', { name: 'Registrar pre-reserva' }).click();
    const dialogo = pagina.getByRole('dialog', { name: 'Registrar pre-reserva' });
    await dialogo.getByRole('combobox', { name: 'Cliente' }).fill(pagina === lucia ? cliente : `${cliente} (Sofía)`);
    await dialogo.getByRole('option', { name: /como cliente nuevo/ }).click();
    await dialogo.getByLabel('Tipo de evento').selectOption({ label: 'Quince' });
  }

  // Lucía confirma primero.
  await lucia.getByRole('dialog').getByRole('button', { name: 'Registrar pre-reserva' }).click();
  await expect(lucia.getByText('Pre-reserva registrada')).toBeVisible();
  await expect(unidad(lucia).getByText('Pre-reserva')).toBeVisible();

  // Sofía llega tarde: no pisa a Lucía.
  await sofia.getByRole('dialog').getByRole('button', { name: 'Registrar pre-reserva' }).click();
  await expect(sofia.getByRole('dialog').getByRole('alert')).toContainText('Esa fecha ya está tomada. Elegí otro salón, otra fecha u otro turno.');
  await sofia.getByRole('button', { name: 'Volver a la agenda' }).click();
  await expect(unidad(sofia)).toContainText('La tiene Lucía Ferreyra.');

  // Lucía registra la seña desde la ficha.
  await lucia.getByRole('button', { name: 'Ver ficha' }).first().click();
  await expect(lucia.getByRole('heading', { level: 1, name: `Quince de ${cliente}` })).toBeVisible();
  await lucia.getByRole('button', { name: 'Registrar seña' }).click();
  const sena = lucia.getByRole('dialog', { name: 'Registrar seña' });
  await sena.getByLabel('Importe de la seña').fill('150.000');
  await sena.getByLabel('DNI').fill('30111222');
  await sena.getByRole('button', { name: 'Confirmar seña' }).click();
  await expect(lucia.getByText('Seña registrada. El evento pasó a Señado.')).toBeVisible();

  // La agenda muestra la unidad señada.
  await lucia.goto(`/agenda?mes=${fecha.slice(0, 7)}&dia=${fecha}`);
  await expect(unidad(lucia).getByText('Señado')).toBeVisible();
  await expect(unidad(lucia)).toContainText(`Quince de ${cliente}`);
});
