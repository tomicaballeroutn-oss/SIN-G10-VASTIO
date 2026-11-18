import { expect, test, type Page } from '@playwright/test';
import { urlInsercionYoutube } from '../src/features/ayuda/video';

const videosEsperados: Record<string, string> = {
  'utilizar-agenda': 'HaqrhVjUWCg', 'ficha-historial': '_IvVyLp1BkI',
  'pre-reservas-senas': 'I7zpEWDAP-I', 'confirmacion-reprogramacion': 'kpOcrk667Ic',
  existencias: 'CnYJ7_p4fmg', 'ingresos-ajustes': 'CnYJ7_p4fmg',
  retiros: 'NVNaKzVUP3w', devoluciones: 'NVNaKzVUP3w', cierre: 'qygweTi5ZvQ',
  ocupacion: 'bWLLdKbdjXw', 'estadisticas-consumo': 'bWLLdKbdjXw',
};

// Estas pruebas verifican la integración visual, no la reproducción de YouTube.
test.beforeEach(async ({ page }) => {
  await page.route('https://www.youtube-nocookie.com/**', (route) => route.fulfill({ body: '<html><body>Reproductor simulado</body></html>', contentType: 'text/html' }));
});

// La ayuda usa contenido local. Se simula únicamente la sesión y el contador de avisos.
async function sesion(page: Page, roles: string[] = ['DIRECCION'], debeCambiarContrasena = false) {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    let cuerpo: unknown = { cantidad: 0 };
    if (url.pathname === '/api/v1/auth/refresh') {
      cuerpo = {
        tokenAcceso: 'sesion-de-prueba', tokenAccesoVence: '2099-01-01T00:00:00Z',
        minutosExpiracionSesion: 60, minutosAvisoExpiracion: 5,
        usuario: { id: 1, nombreCompleto: 'Usuario de prueba', nombreUsuario: 'prueba', roles, debeCambiarContrasena },
      };
    }
    await route.fulfill({ json: cuerpo });
  });
}

test('categorías, artículos, enlaces directos y regreso en ambos tamaños', async ({ page }, testInfo) => {
  test.setTimeout(60_000);
  await sesion(page);
  const errores: string[] = [];
  const pedidos: string[] = [];
  page.on('pageerror', (error) => errores.push(error.message));
  page.on('request', (pedido) => pedidos.push(pedido.url()));
  await page.goto('/ayuda');
  await expect(page.getByRole('heading', { level: 1, name: 'Centro de ayuda' })).toBeVisible();
  await expect(page.locator('.ayuda__categorias h2')).toHaveCount(6);
  const enlaces = await page.locator('main a[href^="/ayuda/"]').evaluateAll((lista) =>
    lista.map((enlace) => ({ href: enlace.getAttribute('href')!, titulo: enlace.textContent! })),
  );
  expect(enlaces).toHaveLength(20);
  await page.screenshot({ path: testInfo.outputPath('ayuda-claro.png'), fullPage: true });
  for (const enlace of enlaces) {
    await page.goto(enlace.href);
    await expect(page.getByRole('heading', { level: 1, name: enlace.titulo, exact: true })).toBeVisible();
    const id = videosEsperados[enlace.href.split('/').at(-1)!];
    await expect(page.locator('video, iframe')).toHaveCount(id ? 1 : 0);
    if (id) {
      const iframe = page.getByTitle(`Video tutorial de ${enlace.titulo}`);
      await iframe.scrollIntoViewIfNeeded();
      await expect(iframe).toHaveAttribute('src', urlInsercionYoutube(`https://youtu.be/${id}`)!);
      await expect(page.getByRole('link', { name: 'Abrir video en YouTube' })).toBeVisible();
      const caja = await iframe.boundingBox();
      const aspecto = id === 'NVNaKzVUP3w' ? 1080 / 2162 : id === 'qygweTi5ZvQ' ? 1080 / 1368 : 1898 / 1170;
      expect(caja!.width / caja!.height).toBeCloseTo(aspecto, 1);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.getByRole('link', { name: /^Volver a / }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Centro de ayuda' })).toBeVisible();
    await expect(page.locator('h2:focus')).toHaveCount(1);
  }
  await page.goto('/ayuda/agenda/filtros-estados');
  await page.reload();
  await expect(page.getByRole('heading', { level: 1, name: 'Filtros y estados de la agenda' })).toBeVisible();
  await page.evaluate(() => { document.documentElement.dataset.theme = 'dark'; });
  await page.screenshot({ path: testInfo.outputPath('articulo-oscuro.png'), fullPage: true });
  await page.getByRole('link', { name: 'Centro de ayuda', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { level: 1, name: 'Centro de ayuda' })).toBeVisible();
  expect(errores).toEqual([]);
  expect(pedidos.filter((url) => /\/api\/.*calendar/i.test(url))).toEqual([]);
});

test('videos verticales, tema oscuro y alternativa accesible con teclado', async ({ page }, testInfo) => {
  await sesion(page);
  for (const articulo of ['retiros', 'cierre']) {
    await page.goto(`/ayuda/barra/${articulo}`);
    await page.evaluate(() => { document.documentElement.dataset.theme = 'dark'; });
    const iframe = page.locator('iframe');
    await iframe.scrollIntoViewIfNeeded();
    await expect(iframe).toBeVisible();
    const enlace = page.getByRole('link', { name: 'Abrir video en YouTube' });
    await iframe.focus();
    await page.keyboard.press('Tab');
    await expect(enlace).toBeFocused();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath(`${articulo}-oscuro.png`), fullPage: true });
  }
});

test('acceso desde cuenta y Más para todos los perfiles y protección directa', async ({ page }, testInfo) => {
  test.setTimeout(60_000);
  const roles = ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'VENDEDORA', 'PLANNER', 'COMPRAS', 'BARRA', 'COCINA'];
  for (const rol of roles) {
    await page.unrouteAll();
    await sesion(page, [rol]);
    await page.goto('/mas');
    await expect(page.getByRole('heading', { name: 'Más', level: 1 })).toBeVisible();
    const movil = testInfo.project.name === 'telefono';
    if (movil) {
      await page.locator('.app__inferior').getByRole('button', { name: /Más/ }).click();
      await page.locator('main').getByRole('button', { name: 'Centro de ayuda', exact: true }).click();
    } else {
      const acceso = page.locator('.app__lateral').getByRole('button', { name: 'Centro de ayuda', exact: true });
      await page.locator('.app__lateral').hover();
      await acceso.click();
    }
    await expect(page.getByRole('heading', { name: 'Centro de ayuda', level: 1 })).toBeVisible();
    await page.goto('/ayuda/gestion/usuarios-perfiles');
    if (['DIRECCION', 'COORDINACION'].includes(rol)) {
      await expect(page.getByRole('heading', { level: 1, name: 'Usuarios y perfiles' })).toBeVisible();
    } else {
      await expect(page.getByText('Este artículo no está disponible')).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Registrar un usuario' })).toHaveCount(0);
    }
  }
  await page.unrouteAll();
  await sesion(page, ['BARRA', 'COCINA']);
  await page.goto('/ayuda');
  await expect(page.getByRole('link', { name: 'Devoluciones y remanentes' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Vista de cocina' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Órdenes y preparación de bebidas' })).toHaveCount(0);
});

test('mantiene la lectura y la navegación en tablet', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 768, height: 1024 });
  await sesion(page, ['BARRA', 'COCINA']);
  await page.goto('/ayuda');
  await expect(page.getByRole('heading', { level: 1, name: 'Centro de ayuda' })).toBeVisible();
  await expect(page.locator('.app__inferior')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('link', { name: 'Devoluciones y remanentes' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Devoluciones y remanentes' })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('articulo-tablet.png'), fullPage: true });
});

test('exige contraseña propia y sesión para abrir enlaces de ayuda', async ({ page }) => {
  await sesion(page, ['COCINA'], true);
  await page.goto('/ayuda/primeros-pasos/glosario');
  await expect(page.getByRole('form', { name: 'Cambiar contraseña' })).toBeVisible();
  await expect(page).toHaveURL(/\/mi-cuenta$/);
  await page.unrouteAll();
  await page.route('**/api/v1/auth/refresh', (route) => route.fulfill({ status: 401, json: { status: 401, detail: 'Sesión vencida' } }));
  await page.goto('/ayuda/barra/devoluciones');
  await expect(page.getByRole('button', { name: 'Ingresar', exact: true })).toBeVisible();
  await expect(page).toHaveURL(/\/login\?volver=/);
});

test('busca en tiempo real, abre resultados y respeta permisos', async ({ page }, testInfo) => {
  test.setTimeout(60_000);
  await sesion(page);
  await page.goto('/ayuda');
  const campo = page.getByRole('searchbox', { name: 'Buscar en la ayuda' });
  await expect(campo).toBeVisible();
  await campo.fill('OCUPACION');
  const resultados = page.getByRole('region', { name: 'Resultados de búsqueda' });
  const articulo = resultados.getByRole('link', { name: 'Reporte de ocupación' });
  await expect(articulo).toHaveAttribute('href', '/ayuda/reportes/ocupacion');
  await expect(resultados.getByText('Reportes y estadísticas')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('buscador-ayuda.png'), fullPage: true });
  if (testInfo.project.name === 'telefono') await articulo.tap();
  else await articulo.click();
  await expect(page.getByRole('heading', { level: 1, name: 'Reporte de ocupación' })).toBeVisible();
  await page.getByRole('link', { name: 'Centro de ayuda', exact: true }).click();
  await campo.fill('inexistentezz');
  await expect(page.getByText('No encontramos artículos')).toBeVisible();
  await page.getByRole('button', { name: 'Limpiar búsqueda' }).click();
  await expect(campo).toHaveValue('');
  await expect(campo).toBeFocused();
  await expect(page.locator('.ayuda__categorias h2')).toHaveCount(6);
  await campo.fill('remanente heredado');
  await expect(resultados.getByRole('link', { name: 'Cerrar un evento' })).toBeVisible();
  await page.unrouteAll();
  await sesion(page, ['ADMINISTRACION']);
  await page.goto('/ayuda');
  await campo.fill('reabrir');
  await expect(page.getByText('No encontramos artículos')).toBeVisible();
  await page.unrouteAll();
  await sesion(page, ['BARRA']);
  await page.goto('/ayuda');
  await campo.fill('ocupacion');
  await expect(page.getByText('No encontramos artículos')).toBeVisible();
  await expect(resultados.getByRole('link', { name: 'Reporte de ocupación' })).toHaveCount(0);
});
