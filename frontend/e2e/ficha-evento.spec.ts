import { expect, test, type Page } from '@playwright/test';
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

/** E2E de la UI con API controlada: usa datos sintéticos para la ficha y los avisos. */
test.use({ channel: process.env.E2E_BROWSER_CHANNEL });

test('Hora particular: primer guardado del control nativo y limpieza sin consultar Calendar', async ({ page }) => {
  const inicial = fichaDePrueba();
  let ficha = { ...inicial, horaInicio: null as string | null,
    acciones: { ...inicial.acciones, modificar: true } };
  const guardados: Array<{ horaInicio: string | null }> = [];
  const pedidosCalendar = registrarPedidosCalendar(page);
  await page.route('**/api/v1/**', async route => {
    const ruta = new URL(route.request().url()).pathname.replace('/api/v1', '');
    const metodo = route.request().method();
    let cuerpo: unknown = { cantidad: 0 };
    if (ruta === '/auth/refresh') cuerpo = {
      tokenAcceso: 'simulado', tokenAccesoVence: '2099-01-01T00:00:00Z', minutosExpiracionSesion: 60,
      minutosAvisoExpiracion: 5, usuario: { id: 1, nombreCompleto: 'Dirección de prueba', nombreUsuario: 'prueba', roles: ['DIRECCION'], debeCambiarContrasena: false },
    };
    else if (ruta === '/eventos/5') {
      if (metodo === 'PUT') {
        const pedido = route.request().postDataJSON() as { horaInicio: string | null };
        guardados.push(pedido);
        ficha = { ...ficha, horaInicio: pedido.horaInicio, version: ficha.version + 1 };
      }
      cuerpo = ficha;
    } else if (ruta === '/tipos-evento') cuerpo = [{ id: 1, nombre: 'Casamiento', usaSegmentos: false, activo: true }];
    await route.fulfill({ json: cuerpo });
  });
  await page.goto('/eventos/5/datos');
  await page.getByLabel(/^Hora de inicio/).fill('12:30');
  await page.getByLabel(/^Hora de inicio/).press('Tab');
  await page.getByRole('button', { name: 'Guardar datos del evento' }).click();
  await expect(page).toHaveURL(/eventos\/5$/);
  expect(guardados).toHaveLength(1);
  expect(guardados[0].horaInicio).toBe('12:30');
  await page.goto('/eventos/5/datos');
  await expect(page.getByLabel(/^Hora de inicio/)).toHaveValue('12:30');
  await page.getByLabel(/^Hora de inicio/).fill('');
  await page.getByRole('button', { name: 'Guardar datos del evento' }).click();
  await expect(page).toHaveURL(/eventos\/5$/);
  expect(guardados).toHaveLength(2);
  expect(guardados[1].horaInicio).toBeNull();
  expect(pedidosCalendar).toEqual([]);
});

test('un aviso de reprogramación se puede leer y abre la ficha interna', async ({ page }) => {
  const ficha = fichaDePrueba();
  const mensaje = `Evento reprogramado: ${ficha.codigo}.`;
  const pedidosCalendar = registrarPedidosCalendar(page);

  await page.route('**/api/v1/**', async (route) => {
    const ruta = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (ruta === '/auth/refresh') return route.fulfill({ json: {
      tokenAcceso: 'simulado', tokenAccesoVence: '2099-01-01T00:00:00Z', minutosExpiracionSesion: 60,
      minutosAvisoExpiracion: 5, usuario: { id: 1, nombreCompleto: 'Dirección de prueba', nombreUsuario: 'prueba', roles: ['DIRECCION'], debeCambiarContrasena: false },
    } });
    if (ruta === '/notificaciones/sin-leer') return route.fulfill({ json: { cantidad: 1 } });
    if (ruta === '/notificaciones') return route.fulfill({ json: {
      notificaciones: [{ id: 9, tipo: 'REPROGRAMACION', mensaje, eventoId: ficha.id, fechaHora: ficha.fechaCreacion, leida: false }],
      pagina: 0, hayMas: false, sinLeer: 1,
    } });
    if (ruta === '/notificaciones/9/lectura') return route.fulfill({ status: 204 });
    if (ruta === '/eventos/5') return route.fulfill({ json: ficha });
    return route.fulfill({ status: 404, json: { detail: 'No encontramos lo que buscás' } });
  });

  await page.goto('/notificaciones');
  const aviso = page.getByRole('button').filter({ hasText: mensaje });
  await expect(aviso).toHaveClass(/\bavisos__aviso--reprogramacion\b/);
  await expect(aviso).toContainText('Sin leer:');
  const lectura = page.waitForRequest((pedido) =>
    new URL(pedido.url()).pathname === '/api/v1/notificaciones/9/lectura' && pedido.method() === 'POST');
  await aviso.click();
  await lectura;

  await expect(page).toHaveURL(/\/eventos\/5$/);
  await expect(page.getByRole('heading', { name: ficha.nombre, exact: true })).toBeVisible();
  await expect(page.getByText(/Google Calendar/i)).toHaveCount(0);
  expect(pedidosCalendar).toEqual([]);
});

function registrarPedidosCalendar(page: Page): string[] {
  const pedidos: string[] = [];
  page.on('request', (pedido) => {
    const ruta = new URL(pedido.url()).pathname;
    if (ruta.startsWith('/api/v1/') && /\/calendar(?:\/|$)/.test(ruta)) pedidos.push(ruta);
  });
  return pedidos;
}
