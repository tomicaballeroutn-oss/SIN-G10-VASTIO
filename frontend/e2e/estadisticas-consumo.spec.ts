import { expect, test, type Page } from '@playwright/test';

// Requiere exclusivamente la base descartable con fixtures/estadisticas-consumo.sql y usuarios demo.
const contrasena = process.env.E2E_DEMO_CONTRASENA ?? '';
test.skip(process.env.E2E_ESTADISTICAS_FIXTURE !== '1' || !contrasena, 'Prepará el fixture sintético B501 en una base aislada.');
test.use({ channel: process.env.E2E_BROWSER_CHANNEL });

async function ingresar(page: Page, usuario = 'gabriela.paz') {
  await page.goto('/login');
  await page.getByLabel('Usuario').fill(usuario);
  await page.getByLabel('Contraseña').fill(contrasena);
  await page.getByRole('button', { name: 'Ingresar' }).click();
  await expect(page).not.toHaveURL(/\/login/);
  await page.goto('/bebidas/estadisticas');
}

test('consulta real, filtros, ceros, período inválido y tablas adaptables', async ({ page }, testInfo) => {
  await ingresar(page);
  await expect(page.getByText('Consultá el consumo histórico')).toBeVisible();
  const escrituras: string[] = [];
  page.on('request', (r) => {
    if (r.url().includes('/api/v1/') && !r.url().includes('/auth/') && r.method() !== 'GET') escrituras.push(r.url());
  });
  await page.getByLabel('Fecha desde').fill('2038-05-01');
  await page.getByLabel('Fecha hasta').fill('2038-05-31');
  const respuesta = page.waitForResponse((r) => r.url().includes('/estadisticas/consumo') && r.request().method() === 'GET');
  await page.getByRole('button', { name: 'Consultar estadísticas' }).click();
  const datos = await (await respuesta).json();
  expect(datos.denominador).toBe('ASISTENTES');
  expect(datos.resumen).toMatchObject({ eventos: 3, consumoTotal: 21, promedioPorEvento: 7,
    eventosConInvitadosValidos: 2, invitadosRegistrados: 300, consumoConInvitadosValidos: 12, porInvitadoRegistrado: 0.04 });
  await expect(page.getByRole('table', { name: 'Consumo por bebida' })).toBeVisible();
  await expect(page.getByText(/El cálculo por asistente usa 2 de 3 eventos/)).toBeVisible();
  const bebida = datos.porBebida.find((b: { nombre: string }) => b.nombre === 'E2E B501 Fernet');
  await page.getByLabel('Tipo de evento').selectOption('2');
  await page.getByLabel('Salón', { exact: true }).selectOption('1');
  await page.getByLabel('Bebida', { exact: true }).selectOption(String(bebida.bebidaId));
  const filtrada = page.waitForResponse((r) => r.url().includes('bebidaId=') && r.url().includes('/estadisticas/consumo'));
  await page.getByRole('button', { name: 'Consultar estadísticas' }).click();
  expect((await (await filtrada).json()).resumen).toMatchObject({ eventos: 2, consumoTotal: 10, promedioPorEvento: 5, porInvitadoRegistrado: 0.0333 });
  await expect(page.getByRole('table', { name: 'Consumo por bebida' }).getByRole('row', { name: /E2E B501 Fernet/ })).toContainText('0,0333');
  for (const ancho of testInfo.project.name === 'escritorio' ? [390, 768, 1280] : [390]) {
    await page.setViewportSize({ width: ancho, height: 850 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath(`estadisticas-${ancho}.png`), fullPage: true });
  }
  await page.getByLabel('Fecha desde').fill('2038-05-02');
  await page.getByLabel('Fecha hasta').fill('2038-05-02');
  await page.getByRole('button', { name: 'Consultar estadísticas' }).click();
  await expect(page.getByText('Eventos cerrados sin registros de consumo')).toBeVisible();
  await page.getByLabel('Fecha desde').fill('2039-01-01');
  await page.getByRole('button', { name: 'Consultar estadísticas' }).click();
  await expect(page.getByText(/Revisá el período:/)).toBeVisible();
  await page.getByLabel('Fecha hasta').fill('2039-12-31');
  await page.getByRole('button', { name: 'Consultar estadísticas' }).click();
  await expect(page.getByText('No hay eventos cerrados para estos filtros')).toBeVisible();
  expect(escrituras).toEqual([]);
});

test('error de red permite reintentar contra el backend real', async ({ page }) => {
  await ingresar(page, 'nicolas.herrera');
  await page.getByLabel('Fecha desde').fill('2038-05-01');
  await page.getByLabel('Fecha hasta').fill('2038-05-31');
  // Vite usa StrictMode: ambos efectos iniciales deben ver la falla de red.
  await page.route('**/api/v1/estadisticas/consumo*', (route) => route.abort());
  await page.getByRole('button', { name: 'Consultar estadísticas' }).click();
  await expect(page.getByText('No pudimos traer los datos')).toBeVisible();
  await page.unroute('**/api/v1/estadisticas/consumo*');
  await page.getByRole('button', { name: 'Reintentar' }).click();
  await expect(page.getByRole('table', { name: 'Consumo por bebida' })).toBeVisible();
});

test('la vendedora no accede por enlace directo', async ({ page }) => {
  let consultas = 0;
  page.on('request', (r) => { if (r.url().includes('/estadisticas/consumo')) consultas++; });
  await ingresar(page, 'lucia.ferreyra');
  await expect(page.getByText('Tu perfil no tiene acceso a esta pantalla')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Consultar estadísticas' })).toHaveCount(0);
  expect(consultas).toBe(0);
});
