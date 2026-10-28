import { expect, test } from '@playwright/test';
// Fixture autónomo: este proyecto de Node no debe importar componentes/JSX de la aplicación.
const ficha = {
  id: 5, codigo: 'EV-2032-00005', estado: 'SENADO', nombre: 'Bruno y Martina',
  tipo: { id: 2, nombre: 'Quince' }, salon: { id: 1, codigo: 'avril', nombre: 'Avril', capacidad: null }, fecha: '2032-07-10',
  turno: { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00' }, horaInicio: null,
  cliente: { id: 1, nombre: 'Cliente de Bruno y Martina', documento: null, telefono: null, email: null }, contactos: [],
  vendedora: { id: 1, nombre: 'Lucía Ferreyra' }, planner: null, cantidadInvitados: null, invitadosDefinitivos: false,
  observacionesInternas: null, reprogramadoDesde: null, fechaFirmaContrato: null, cancelacion: null,
  sena: { importe: 100000, moneda: 'ARS', fecha: '2032-06-10', firmanteNombre: 'Mariela Ruiz', firmanteDni: '30111222', firmanteContacto: null },
  documentos: null, servicios: [], requisitosConfirmacion: null, historial: [], fechaCreacion: '2032-06-01T10:00:00-03:00', version: 1,
  acciones: { modificar: false, liberar: false, registrarSena: false, registrarFirma: false, asignarPlanner: false,
    confirmar: false, cancelar: false, reprogramar: false },
};

test.use({ channel: process.env.E2E_BROWSER_CHANNEL });

test('GET no emite; POST emite una vez; descargas siguientes conservan el archivo', async ({ page }) => {
  let emitido = false;
  let emisiones = 0;
  const metodos: string[] = [];
  const pdf = '%PDF-1.4\nComprobante original de prueba';
  await page.route('**/api/v1/**', async (route) => {
    const ruta = new URL(route.request().url()).pathname;
    if (ruta.endsWith('/auth/refresh')) return route.fulfill({ json: {
      tokenAcceso: 'token-sintetico', tokenAccesoVence: '2099-01-01T00:00:00Z', minutosExpiracionSesion: 60, minutosAvisoExpiracion: 5,
      usuario: { id: 1, nombreCompleto: 'Lucía Ferreyra', nombreUsuario: 'lucia.prueba', roles: ['VENDEDORA'], debeCambiarContrasena: false },
    } });
    if (ruta.endsWith('/notificaciones/sin-leer')) return route.fulfill({ json: { cantidad: 0 } });
    if (ruta.endsWith('/eventos/5')) return route.fulfill({ json: ficha });
    if (ruta.endsWith('/sena/comprobante')) {
      const metodo = route.request().method();
      metodos.push(metodo);
      if (metodo === 'GET' && !emitido) return route.fulfill({ status: 404, json: { status: 404, codigo: 'COMPROBANTE_NO_EMITIDO', detail: 'Todavía no se emitió el comprobante.' } });
      const nuevo = metodo === 'POST' && !emitido;
      if (nuevo) { emisiones++; emitido = true; }
      return route.fulfill({ status: nuevo ? 201 : 200, contentType: 'application/pdf', body: pdf,
        headers: { 'Content-Disposition': 'attachment; filename="comprobante-sena-CS-2032-00001.pdf"' } });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto('/eventos/5');
  await page.getByRole('button', { name: 'Descargar comprobante' }).click();
  await expect(page.getByRole('alert')).toHaveText(/Todavía no se emitió/);
  expect(metodos).toEqual(['GET']);
  const primera = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Emitir comprobante' }).click();
  expect((await primera).suggestedFilename()).toBe('comprobante-sena-CS-2032-00001.pdf');
  const segunda = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar comprobante' }).click();
  expect((await segunda).suggestedFilename()).toBe('comprobante-sena-CS-2032-00001.pdf');
  expect(emisiones).toBe(1);
  expect(metodos).toEqual(['GET', 'POST', 'GET']);
});
