import { expect, test } from '@playwright/test';

test.use({ channel: process.env.E2E_BROWSER_CHANNEL });

test('bloqueos: conflicto, registro con responsable, agenda y desbloqueo con API simulada', async ({ page }, info) => {
  const salones = [
    { id: 1, codigo: 'avril', nombre: 'Avril', capacidad: null, activo: true },
    { id: 2, codigo: 'club', nombre: 'Club de Campo', capacidad: null, activo: true },
    { id: 3, codigo: 'santa-barbara', nombre: 'Santa Bárbara', capacidad: null, activo: true },
  ];
  const turnos = [
    { id: 1, codigo: 'mediodia', nombre: 'Mediodía', horaInicio: '12:00:00', horaFin: '18:00:00', cruzaMedianoche: false },
    { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00', cruzaMedianoche: true },
  ];
  const b = { id: 1, motivo: { id: 7, nombre: 'Mantenimiento' }, detalle: 'Pintura',
    salones: [{ id: 1, nombre: 'Avril' }], desde: '2056-03-06', hasta: '2056-03-06',
    turnos: [{ id: 2, nombre: 'Noche' }], cantidadUnidades: 1, registradoPorId: 1,
    registradoPor: 'Gabriela Soria', fechaRegistro: '2033-03-01T10:00:00-03:00' };
  let activo = false, rechazar = true, desbloqueos = 0;
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const metodo = route.request().method(), ruta = url.pathname;
    if (ruta.endsWith('/auth/refresh')) return route.fulfill({ json: {
      tokenAcceso: 'token-sintetico', tokenAccesoVence: '2099-01-01T00:00:00Z',
      minutosExpiracionSesion: 60, minutosAvisoExpiracion: 5,
      usuario: { id: 1, nombreCompleto: 'Gabriela Soria', nombreUsuario: 'prueba', roles: ['ADMINISTRACION'], debeCambiarContrasena: false },
    } });
    if (ruta.endsWith('/salones')) return route.fulfill({ json: salones });
    if (ruta.endsWith('/turnos')) return route.fulfill({ json: turnos });
    if (ruta.endsWith('/motivos')) return route.fulfill({ json: [{ id: 7, ambito: 'BLOQUEO', nombre: 'Mantenimiento', activo: true }] });
    if (ruta.endsWith('/notificaciones/sin-leer')) return route.fulfill({ json: { cantidad: 0 } });
    if (ruta.endsWith('/agenda')) return route.fulfill({ json: {
      mes: '2056-03', salones, turnos, unidades: activo ? [
        { fecha: b.desde, salonId: 1, turnoId: 2, estado: 'BLOQUEADO', evento: null, bloqueo: { motivo: 'Mantenimiento', detalle: 'Pintura' } },
      ] : [],
    } });
    if (ruta.endsWith('/bloqueos') && metodo === 'GET') return route.fulfill({ json: activo ? [b] : [] });
    if (ruta.endsWith('/bloqueos') && metodo === 'POST') {
      expect(route.request().postDataJSON()).toEqual({ salonIds: [1], desde: b.desde, hasta: null, turnoIds: [2], motivoId: 7, detalle: 'Pintura' });
      if (rechazar) return route.fulfill({ status: 409, json: { status: 409, codigo: 'UNIDAD_OCUPADA', detail: 'Avril ya tiene el evento EV-2056-00001. Elegí otra fecha.' } });
      activo = true; return route.fulfill({ status: 201, json: b });
    }
    if (ruta.endsWith('/bloqueos/1/desbloqueo')) {
      desbloqueos++; activo = false; return route.fulfill({ status: 204 });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto('/bloqueos');
  await page.getByRole('checkbox', { name: 'Avril' }).check();
  await page.getByLabel('Desde', { exact: true }).fill(b.desde);
  await page.getByLabel('Turnos', { exact: true }).selectOption('2');
  await page.getByLabel('Motivo', { exact: true }).selectOption('7');
  await page.getByLabel('Detalle (opcional)', { exact: true }).fill('Pintura');
  await page.getByRole('button', { name: 'Registrar bloqueo' }).click();
  await expect(page.getByText(/Avril ya tiene el evento/)).toBeVisible();
  await expect(page.getByLabel('Desde', { exact: true })).toHaveValue(b.desde);
  rechazar = false;
  await page.getByRole('button', { name: 'Registrar bloqueo' }).click();
  const fila = page.getByRole('table', { name: 'Bloqueos activos' }).getByRole('row').filter({ hasText: 'Pintura' });
  await expect(fila).toContainText('Gabriela Soria');
  await expect(fila).toContainText('mar 1 mar 10:00');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('bloqueos.png'), fullPage: true });
  await page.goto('/agenda?mes=2056-03&dia=2056-03-06');
  await expect(page.getByRole('tabpanel', { name: 'Avril' })).toContainText('Mantenimiento · Pintura');
  await page.goto('/bloqueos');
  await page.getByRole('button', { name: 'Desbloquear', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Cancelar' }).click();
  expect(desbloqueos).toBe(0);
  await page.getByRole('button', { name: 'Desbloquear', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Desbloquear', exact: true }).click();
  await expect(page.getByText('No hay bloqueos activos.')).toBeVisible();
  expect(desbloqueos).toBe(1);
  await page.goto('/agenda?mes=2056-03&dia=2056-03-06');
  await expect(page.getByRole('tabpanel', { name: 'Avril' }).getByText('Libre para ofrecer.')).toHaveCount(2);
});
