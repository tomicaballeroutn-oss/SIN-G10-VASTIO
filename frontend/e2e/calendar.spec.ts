import { expect, test } from '@playwright/test';
// Fixture aislado: el proyecto TS de Playwright no importa el bundle React.
function fichaDePrueba() {
  return { id: 5, codigo: 'EV-2026-00005', estado: 'SENADO', nombre: 'Evento sintético',
    tipo: { id: 1, nombre: 'Casamiento' }, salon: { id: 1, codigo: 'avril', nombre: 'Avril', capacidad: null },
    fecha: '2032-12-31', turno: { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00' },
    horaInicio: null, reprogramadoDesde: null, cliente: { id: 7, nombre: 'Cliente sintético', documento: null, telefono: null, email: null },
    contactos: [], vendedora: { id: 1, nombre: 'Vendedora sintética' }, planner: null, cantidadInvitados: 180,
    invitadosDefinitivos: false, observacionesInternas: null, sena: null, fechaFirmaContrato: null,
    cancelacion: null, documentos: null, servicios: [], requisitosConfirmacion: null,
    fechaCreacion: '2026-10-27T10:00:00-03:00', version: 1,
    acciones: { modificar: false, liberar: false, registrarSena: false, registrarFirma: false, asignarPlanner: false, confirmar: false, cancelar: false, reprogramar: false }, historial: [],
  };
}

/** E2E de la UI con API controlada: nunca necesita Google ni un backend productivo. */
test.use({ channel: process.env.E2E_BROWSER_CHANNEL });

test('Calendar: pendiente, publicación, actualización, retirada y avisos en escritorio/móvil', async ({ page }, testInfo) => {
  const ficha = fichaDePrueba();
  let e = { eventoId: 5, codigo: ficha.codigo, salon: 'Avril', fecha: '2032-12-31', turno: 'Noche',
    estado: 'PENDIENTE', pendiente: 'EXPORTAR', error: null, ultimoExito: null, configurado: true,
    puedeExportar: true, puedeRetirar: false };
  let publicaciones = 0;
  let avisos = true;
  await page.route('**/api/v1/**', async route => {
    const ruta = new URL(route.request().url()).pathname.replace('/api/v1', '');
    const metodo = route.request().method();
    let cuerpo: unknown = {};
    if (ruta === '/auth/refresh') cuerpo = {
      tokenAcceso: 'token-e2e-simulado', tokenAccesoVence: '2099-01-01T00:00:00Z', minutosExpiracionSesion: 60,
      minutosAvisoExpiracion: 5, usuario: { id: 1, nombreCompleto: 'Dirección de prueba', nombreUsuario: 'prueba', roles: ['DIRECCION'], debeCambiarContrasena: false },
    };
    else if (ruta === '/eventos/5') cuerpo = ficha;
    else if (ruta === '/notificaciones/sin-leer') cuerpo = { cantidad: avisos ? 1 : 0 };
    else if (ruta === '/notificaciones') cuerpo = { notificaciones: avisos ? [{ id: 9, tipo: 'CALENDAR_PENDIENTE', mensaje: `Exportar a Google Calendar: ${ficha.codigo}.`, eventoId: 5, fechaHora: '2026-10-27T10:00:00-03:00', leida: false }] : [], pagina: 0, hayMas: false, sinLeer: avisos ? 1 : 0 };
    else if (ruta.endsWith('/lectura')) { await route.fulfill({ status: 204 }); return; }
    else if (ruta === '/calendar/pendientes') cuerpo = e.pendiente === 'NINGUNA' ? [] : [e];
    else if (ruta === '/eventos/5/calendar') {
      if (metodo === 'POST' || metodo === 'PUT') { publicaciones++; e = { ...e, estado: 'EXPORTADO', pendiente: 'NINGUNA', puedeRetirar: true }; avisos = false; }
      if (metodo === 'DELETE') e = { ...e, estado: 'RETIRADO', pendiente: 'NINGUNA', puedeExportar: false, puedeRetirar: false };
      cuerpo = e;
    }
    await route.fulfill({ json: cuerpo });
  });
  await page.goto('/notificaciones');
  await page.getByRole('button').filter({ hasText: `Exportar a Google Calendar: ${ficha.codigo}.` }).click();
  await expect(page).toHaveURL(/eventos\/5$/);
  const exportar = page.getByRole('button', { name: 'Exportar a Google Calendar', exact: true });
  await expect(exportar).toHaveClass(/v-tone-danger/);
  await exportar.click();
  await expect(page.getByText('✓ Exportado', { exact: true })).toBeVisible();
  expect(publicaciones).toBe(1);
  await page.screenshot({ path: testInfo.outputPath('calendar-exportado.png'), fullPage: true });
  await page.goto('/calendar/pendientes');
  await expect(page.getByText('No hay pendientes de Google Calendar')).toBeVisible();
  await page.goto('/notificaciones');
  await expect(page.getByText('No tenés avisos sin leer')).toBeVisible();
  e = { ...e, pendiente: 'ACTUALIZAR' };
  await page.goto('/calendar/pendientes');
  await page.getByRole('button', { name: `Abrir ${ficha.codigo}` }).click();
  await page.getByRole('button', { name: 'Actualizar en Google Calendar' }).click();
  await expect(page.getByText('✓ Exportado', { exact: true })).toBeVisible();
  expect(publicaciones).toBe(2);
  e = { ...e, pendiente: 'RETIRAR', puedeExportar: false };
  await page.reload();
  await page.getByRole('button', { name: 'Retirar de Google Calendar' }).click();
  await expect(page.getByText('Publicación retirada', { exact: true })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('calendar-retirado.png'), fullPage: true });
});

test('Calendar: desactivado sin acciones y error incierto con reintento explícito', async ({ page }) => {
  let habilitado = false;
  let incierto = false;
  await page.route('**/api/v1/**', async route => {
    const ruta = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (ruta === '/auth/refresh') {
      await route.fulfill({ json: { tokenAcceso: 'simulado', tokenAccesoVence: '2099-01-01T00:00:00Z', minutosExpiracionSesion: 60, minutosAvisoExpiracion: 5, usuario: { id: 1, nombreCompleto: 'Coordinación', nombreUsuario: 'prueba', roles: ['COORDINACION'], debeCambiarContrasena: false } } }); return;
    }
    if (ruta === '/eventos/5') { await route.fulfill({ json: fichaDePrueba() }); return; }
    if (ruta === '/eventos/5/calendar') {
      if (route.request().method() === 'POST') { incierto = true; await route.fulfill({ status: 503, json: { status: 503, codigo: 'RED_GOOGLE', title: 'Google Calendar', detail: 'Resultado incierto. Verificá y reintentá.' } }); return; }
      await route.fulfill({ json: { eventoId: 5, codigo: 'EV-2026-00005', salon: 'Avril', fecha: '2032-12-31', turno: 'Noche', estado: incierto ? 'INCIERTO' : 'PENDIENTE', pendiente: 'EXPORTAR', error: incierto ? 'RED_GOOGLE' : null, ultimoExito: null, configurado: habilitado, puedeExportar: true, puedeRetirar: incierto } }); return;
    }
    await route.fulfill({ json: { cantidad: 0 } });
  });
  await page.goto('/eventos/5');
  await expect(page.getByText('Integración desactivada', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Exportar a Google Calendar' })).toHaveCount(0);
  habilitado = true;
  await page.reload();
  await page.getByRole('button', { name: 'Exportar a Google Calendar' }).click();
  await expect(page.getByText('Resultado incierto: requiere verificación')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Exportar a Google Calendar' })).toBeEnabled();
  await expect(page.getByText('✓ Exportado', { exact: true })).toHaveCount(0);
});
