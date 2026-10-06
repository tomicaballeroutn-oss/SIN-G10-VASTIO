import { expect, test, type Browser, type Page } from '@playwright/test';

/**
 * Objetivo del Sprint 2: un evento señado llega a Confirmado con su legajo completo (contrato, invitados, servicios y
 * planner), y cada área se entera de los cambios sin revisar el calendario.
 * Usa los datos de demostración (perfil dev con VASTIO_DEMO_CONTRASENA): lucia.ferreyra, melina.sifon, ana.sosa y
 * nicolas.herrera. Arma su propio evento en un día libre, así se puede repetir.
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
  // El login también tiene un h1: se espera a salir de /login, no solo a ver un título.
  await expect(pagina).not.toHaveURL(/\/login/);
  await expect(pagina.getByRole('heading', { level: 1 })).toBeVisible();
  return pagina;
}

/** Avril a la noche de ese día, en el detalle de la agenda. */
function unidad(pagina: Page) {
  return pagina.locator('.agenda__turno').filter({ hasText: 'Noche' }).locator('li').filter({ hasText: 'Avril' });
}

/** Un día libre lejos de los datos de demostración; cada corrida prueba otros hasta encontrar uno. */
async function diaLibre(pagina: Page): Promise<string> {
  for (let intento = 0; intento < 10; intento++) {
    const fecha = new Date(Date.now() + (60 + Math.floor(Math.random() * 600)) * 86_400_000).toISOString().slice(0, 10);
    await pagina.goto(`/agenda?mes=${fecha.slice(0, 7)}&dia=${fecha}`);
    await expect(pagina.getByText('Día elegido')).toBeVisible();
    if (await unidad(pagina).getByRole('button', { name: 'Registrar pre-reserva' }).isVisible()) return fecha;
  }
  throw new Error('No encontré un día libre para la prueba');
}

test('un evento señado llega a Confirmado con su legajo y Compras se entera', async ({ browser, isMobile, viewport, userAgent }) => {
  test.slow();
  const contexto = { viewport, userAgent, isMobile, hasTouch: isMobile, ignoreHTTPSErrors: process.env.E2E_IGNORAR_CERTIFICADO === '1' };
  const cliente = `Cliente E2E ${Date.now()}`;
  const nombre = `Casamiento de ${cliente}`;

  // La vendedora aparta la fecha y registra la seña.
  const lucia = await ingresar(browser, 'lucia.ferreyra', contexto);
  await diaLibre(lucia);
  await unidad(lucia).getByRole('button', { name: 'Registrar pre-reserva' }).click();
  const preReserva = lucia.getByRole('dialog', { name: 'Registrar pre-reserva' });
  await preReserva.getByRole('combobox', { name: 'Cliente' }).fill(cliente);
  await preReserva.getByRole('option', { name: /como cliente nuevo/ }).click();
  await preReserva.getByLabel('Tipo de evento').selectOption({ label: 'Casamiento' });
  await preReserva.getByRole('button', { name: 'Registrar pre-reserva' }).click();
  await lucia.getByRole('button', { name: 'Ver ficha' }).first().click();
  await expect(lucia.getByRole('heading', { level: 1, name: nombre })).toBeVisible();
  const ficha = new URL(lucia.url()).pathname;
  await lucia.getByRole('button', { name: 'Registrar seña' }).click();
  const sena = lucia.getByRole('dialog', { name: 'Registrar seña' });
  await sena.getByLabel('Importe de la seña').fill('300.000');
  await sena.getByLabel('DNI').fill('30111222');
  await sena.getByRole('button', { name: 'Confirmar seña' }).click();
  await expect(lucia.getByText('Seña registrada. El evento pasó a Señado.')).toBeVisible();

  // Coordinación registra la firma con el contrato digitalizado y asigna la planner.
  const melina = await ingresar(browser, 'melina.sifon', contexto);
  await melina.goto(ficha);
  await melina.getByRole('button', { name: 'Registrar firma' }).click();
  const firma = melina.getByRole('dialog', { name: 'Registrar firma de contrato' });
  await firma.getByLabel('Contrato digitalizado').setInputFiles({
    name: 'contrato firmado.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n% contrato de la prueba\n%%EOF\n'),
  });
  await expect(firma.getByText('contrato firmado.pdf')).toBeVisible();
  await firma.getByRole('button', { name: 'Registrar firma' }).click();
  await expect(melina.getByText('Firma registrada. El evento pasó a Contratado.')).toBeVisible();
  await melina.getByRole('button', { name: 'Asignar planner' }).click();
  const planner = melina.getByRole('dialog', { name: 'Asignar planner' });
  await planner.getByLabel('Planner').selectOption({ label: 'Ana Sosa' });
  await planner.getByRole('button', { name: 'Asignar planner' }).click();
  await expect(melina.getByText('Planner asignada: Ana Sosa.')).toBeVisible();

  // La planner cierra invitados y servicios, y confirma.
  const ana = await ingresar(browser, 'ana.sosa', contexto);
  await ana.goto(ficha);
  await ana.getByRole('button', { name: 'Cantidad de invitados' }).click();
  const invitados = ana.getByRole('dialog', { name: 'Cantidad de invitados' });
  await invitados.getByLabel('Cantidad de invitados').fill('180');
  await invitados.getByLabel(/Cantidad definitiva/).check();
  await invitados.getByRole('button', { name: 'Guardar cantidad' }).click();
  await expect(ana.getByText('Cantidad de invitados guardada.')).toBeVisible();
  await ana.getByRole('tab', { name: /Servicios/ }).click();
  await ana.getByLabel('Plato principal').fill('Lomo braseado con papas rústicas');
  await ana.getByLabel('Tipo de barra').fill('Barra libre premium hasta las 5');
  await ana.getByRole('button', { name: 'Guardar servicios' }).click();
  await expect(ana.getByText('Servicios guardados.')).toBeVisible();
  await ana.getByRole('button', { name: 'Confirmar evento' }).click();
  const confirmar = ana.getByRole('dialog', { name: 'Confirmar evento' });
  await expect(confirmar.getByText('Ana Sosa')).toBeVisible();
  await confirmar.getByRole('button', { name: 'Confirmar evento' }).click();
  await expect(ana.getByText('Evento confirmado. Se avisó a Compras y Cocina.')).toBeVisible();

  // Compras se entera por la campana y abre la ficha.
  const nicolas = await ingresar(browser, 'nicolas.herrera', contexto);
  await nicolas.getByTitle(/^Notificaciones/).click();
  const panel = nicolas.getByRole('dialog', { name: 'Notificaciones' });
  await panel.getByRole('button', { name: new RegExp(`Evento confirmado: ${nombre}`) }).click();
  await expect(nicolas.getByRole('heading', { level: 1, name: nombre })).toBeVisible();
  await expect(nicolas.getByText('Confirmado', { exact: true }).first()).toBeVisible();
});
