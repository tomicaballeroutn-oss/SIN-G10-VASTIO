import { expect, test, type Page } from '@playwright/test';

test.use({ channel: process.env.E2E_BROWSER_CHANNEL });

const tarjeta = { id: 5, nombre: 'Preparación de la jornada anterior', tipo: 'Casamiento', fecha: '2032-05-31',
  salon: { codigo: 'avril', nombre: 'Avril' }, turno: 'noche', turnoNombre: 'Noche', hora: '21:30:00', invitados: 180,
  servicios: [{ categoria: 'Menú', renglones: [{ texto: 'Celíaco', cantidad: 2 }], detalle: 'Lomo braseado' }] };

async function preparar(page: Page, roles = ['COCINA'], hoy = '2032-06-01', fecha = '2032-05-31') {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const ruta = url.pathname;
    let cuerpo: unknown = [];
    if (ruta.endsWith('/auth/refresh')) cuerpo = {
      tokenAcceso: 'token-sintetico', tokenAccesoVence: '2099-01-01T00:00:00Z', minutosExpiracionSesion: 60, minutosAvisoExpiracion: 5,
      usuario: { id: 2, nombreCompleto: 'Prueba cocina', nombreUsuario: 'prueba.cocina', roles, debeCambiarContrasena: false },
    };
    else if (ruta.endsWith('/notificaciones/sin-leer')) cuerpo = { cantidad: 1 };
    else if (ruta.endsWith('/notificaciones')) cuerpo = { notificaciones: [
      { id: 30, tipo: 'CONFIRMACION', mensaje: 'Evento confirmado: preparación', eventoId: 5, fechaHora: '2032-06-01T01:00:00-03:00', leida: true },
    ], pagina: 0, hayMas: false, sinLeer: 1 };
    else if (ruta.endsWith('/cocina/eventos/5')) cuerpo = { ...tarjeta, fecha };
    else if (ruta.endsWith('/cocina')) cuerpo = {
      mes: url.searchParams.get('mes'), hoy, hasta: hoy, salones: [tarjeta.salon], eventos: [], jornadasAnteriores: [{ ...tarjeta, fecha }],
    };
    await route.fulfill({ json: cuerpo });
  });
}

for (const [hoy, fecha] of [['2032-06-01', '2032-05-31'], ['2033-01-01', '2032-12-31'], ['2032-05-10', '2032-05-09']]) {
  test('al ingresar el ' + hoy + ' muestra la preparación de ' + fecha + ' sin duplicados', async ({ page }) => {
    await preparar(page, ['COCINA'], hoy, fecha);
    await page.goto('/cocina?mes=' + hoy.slice(0, 7));
    await expect(page.getByRole('heading', { name: 'Jornadas anteriores' })).toBeVisible();
    await expect(page.getByRole('article')).toHaveCount(1);
    await expect(page.getByRole('heading', { name: tarjeta.nombre })).toBeVisible();
    await expect(page.getByText('En curso', { exact: true })).toHaveCount(0);
    await page.goto('/cocina?evento=5');
    await expect(page).toHaveURL(new RegExp('dia=' + fecha));
    await expect(page.getByRole('article')).toHaveCount(1);
    await expect(page.locator('article[aria-current="true"]')).toBeVisible();
  });
}

test('Cocina + Vendedora abre la tarjeta desde la campana sin consultar una ficha comercial', async ({ page }) => {
  const fichas: string[] = [];
  page.on('request', (r) => { if (/api\/v1\/eventos\/5/.test(r.url())) fichas.push(r.url()); });
  await preparar(page, ['COCINA', 'VENDEDORA']);
  await page.goto('/mi-cuenta');
  await page.getByRole('button', { name: 'Notificaciones, 1 sin leer' }).click();
  await page.getByRole('dialog', { name: 'Notificaciones' }).getByRole('button', { name: /Evento confirmado/ }).click();
  await expect(page).toHaveURL(/\/cocina\?evento=5/);
  await expect(page.locator('article[aria-current="true"]')).toBeVisible();
  expect(fichas).toEqual([]);
});
