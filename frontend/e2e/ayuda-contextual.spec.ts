import { expect, test, type Page } from '@playwright/test';

const salon = { id: 1, codigo: 'avril', nombre: 'Avril' };
const barra = { id: 2, nombre: 'Barra Avril', tipo: 'BARRA' };
const deposito = { id: 1, nombre: 'Depósito madre', tipo: 'DEPOSITO' };
const evento = { id: 10, codigo: 'EV-10', nombre: 'Evento de prueba', estado: 'REALIZADO' };
const bebida = { id: 1, nombre: 'Bebida de prueba', presentacion: '750 ml', tipo: 'Vino', unidad: 'Caja', unidadesPorBulto: 6, activo: true };

async function preparar(page: Page, roles = ['DIRECCION']) {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    const ruta = url.pathname.replace('/api/v1', '');
    let respuesta: unknown = { cantidad: 0 };
    if (ruta === '/auth/refresh') respuesta = {
      tokenAcceso: 'prueba', tokenAccesoVence: '2099-01-01T00:00:00Z', minutosExpiracionSesion: 60, minutosAvisoExpiracion: 5,
      usuario: { id: 1, nombreCompleto: 'Usuario de prueba', nombreUsuario: 'prueba', roles, debeCambiarContrasena: false },
    };
    if (ruta === '/agenda') respuesta = {
      mes: url.searchParams.get('mes'), salones: [{ ...salon, activo: true }], unidades: [],
      turnos: [
        { id: 1, codigo: 'mediodia', nombre: 'Mediodía', horaInicio: '12:00', horaFin: '18:00', cruzaMedianoche: false },
        { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00', horaFin: '06:00', cruzaMedianoche: true },
      ],
    };
    if (ruta === '/eventos/5') respuesta = {
      id: 5, codigo: 'EV-5', nombre: 'Evento de prueba', estado: 'CONTRATADO', tipo: { id: 1, nombre: 'Casamiento' },
      salon: { ...salon, capacidad: null }, fecha: '2030-03-09', turno: { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00', horaFin: '06:00' },
      horaInicio: null, reprogramadoDesde: null, cliente: { id: 1, nombre: 'Cliente de prueba', documento: null, telefono: null, email: null },
      contactos: [], vendedora: { id: 1, nombre: 'Usuario de prueba' }, planner: null, cantidadInvitados: 100, invitadosDefinitivos: false,
      observacionesInternas: null, sena: null, fechaFirmaContrato: null, cancelacion: null, documentos: null, servicios: [], asistencia: null,
      fechaCreacion: '2030-01-01T10:00:00-03:00', version: 1, historial: [],
      acciones: {
        modificar: false, liberar: false, registrarSena: false, registrarFirma: false, asignarPlanner: false, confirmar: true,
        cancelar: false, reprogramar: false, registrarAsistenciaPrevista: false, registrarAsistenciaReal: false,
      },
      requisitosConfirmacion: [{ codigo: 'PLANNER', titulo: 'Planner asignada', cumplido: false, detalle: 'Falta asignar la planner.' }],
    };
    const consultaGeneral = roles.some((rol) => ['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'].includes(rol));
    if (ruta === '/stock/ubicaciones') respuesta = [{ ubicacion: consultaGeneral ? deposito : barra, ...(consultaGeneral ? {} : { evento }) }];
    if (ruta === '/stock') respuesta = { ubicacion: consultaGeneral ? deposito : barra, renglones: [{ bebida, cantidad: 6, ...(consultaGeneral ? { estado: 'BAJO', stockMinimo: 12 } : {}) }] };
    if (ruta === '/entregas/jornada') respuesta = [{ evento, salon, barras: [barra] }];
    if (ruta === '/eventos/10/entregas/2') respuesta = {
      evento, salon, barra, origen: deposito, depositoMadre: null, estadoEntrega: 'ABIERTA', yaEntregado: [], retiros: [], bebidas: [], enTransicion: [],
      aDevolver: [{ ...bebida, codigos: [] }],
    };
    if (ruta === '/cierres/pendientes') respuesta = [];
    if (ruta === '/eventos/10/cierre') respuesta = {
      evento, salon, invitados: 100, usaSegmentos: false, asistenciaReal: null, pendientes: [], renglones: [], consumoTotal: 0, porAsistenteTotal: 0, cerrado: false,
    };
    if (ruta === '/reportes/ocupacion') respuesta = {
      desde: '2030-01-01', hasta: '2030-12-31', regimen: null, resumen: { unidadesOcupadas: 0, ocupacionPromedio: 0, eventosRealizados: 0 },
      porSalon: [], porVendedoraYTipo: [], filtros: { salones: [salon], tiposEvento: [], vendedoras: [] },
    };
    await route.fulfill({ json: respuesta });
  });
}

test('los ocho puntos abren su artículo y vuelven a la pantalla operativa', async ({ page }, testInfo) => {
  test.setTimeout(90_000);
  await preparar(page);
  const errores: string[] = [];
  const escrituras: string[] = [];
  page.on('pageerror', (error) => errores.push(error.message));
  page.on('request', (pedido) => { if (pedido.method() !== 'GET' && !pedido.url().includes('/auth/')) escrituras.push(pedido.url()); });
  const puntos = [
    { origen: '/agenda?mes=2030-03&salon=avril', titulo: 'Filtros de la agenda', destino: '/ayuda/agenda/filtros-estados' },
    { origen: '/agenda?mes=2030-03', titulo: 'Estados de los eventos', destino: '/ayuda/agenda/filtros-estados' },
    { origen: '/eventos/5?pestana=historial', titulo: 'Historial del evento', destino: '/ayuda/agenda/ficha-historial' },
    { origen: '/eventos/5', titulo: 'Requisitos de confirmación', destino: '/ayuda/agenda/confirmacion-reprogramacion' },
    { origen: '/existencias', titulo: 'Estado de las existencias', destino: '/ayuda/bebidas/existencias' },
    { origen: '/barra', titulo: 'Devoluciones y remanentes', destino: '/ayuda/barra/devoluciones' },
    { origen: '/barra/cierre/10', titulo: 'Cálculo del consumo', destino: '/ayuda/barra/cierre' },
    { origen: '/reportes', titulo: 'Cálculo de ocupación', destino: '/ayuda/reportes/ocupacion' },
  ];
  for (const punto of puntos) {
    await page.goto(punto.origen);
    if (punto.titulo === 'Requisitos de confirmación') await page.getByRole('button', { name: 'Confirmar evento', exact: true }).click();
    const boton = page.getByRole('button', { name: `Ayuda sobre ${punto.titulo.toLowerCase()}`, exact: true });
    await expect(boton).toBeVisible();
    const leyendaAbierta = await boton.evaluate((elemento) => elemento.closest('details')?.open);
    if (testInfo.project.name === 'telefono') await boton.tap();
    else await boton.click();
    const panel = page.getByRole('dialog', { name: punto.titulo, exact: true });
    await expect(panel).toBeVisible();
    const limites = await panel.boundingBox();
    const pantalla = page.viewportSize()!;
    expect(limites!.x).toBeGreaterThanOrEqual(0);
    expect(limites!.x + limites!.width).toBeLessThanOrEqual(pantalla.width);
    expect(limites!.y + limites!.height).toBeLessThanOrEqual(pantalla.height);
    expect(await boton.evaluate((elemento) => elemento.closest('details')?.open)).toBe(leyendaAbierta);
    if (punto.titulo === 'Requisitos de confirmación') {
      const acciones = await page.getByRole('dialog', { name: 'Confirmar evento', exact: true }).locator('.v-dialog__foot').boundingBox();
      const separadas = limites!.x + limites!.width <= acciones!.x || limites!.x >= acciones!.x + acciones!.width
        || limites!.y + limites!.height <= acciones!.y || limites!.y >= acciones!.y + acciones!.height;
      expect(separadas).toBe(true);
      await page.screenshot({ path: testInfo.outputPath('ayuda-confirmacion.png'), fullPage: true });
      await page.keyboard.press('Escape');
      await expect(page.getByRole('dialog', { name: 'Confirmar evento', exact: true })).toBeVisible();
      await expect(boton).toBeFocused();
      await boton.click();
    }
    await panel.getByRole('link', { name: 'Saber más' }).click();
    await expect(page).toHaveURL(new RegExp(`${punto.destino}$`));
    await page.getByRole('link', { name: 'Volver a la pantalla anterior' }).click();
    expect(new URL(page.url()).pathname + new URL(page.url()).search).toBe(punto.origen);
  }
  expect(escrituras).toEqual([]);
  expect(errores).toEqual([]);
});

test('en tablet se puede usar teclado, cerrar afuera y leer en oscuro', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 768, height: 1024 });
  await preparar(page);
  await page.goto('/agenda?mes=2030-03');
  const boton = page.getByRole('button', { name: 'Ayuda sobre filtros de la agenda' });
  await boton.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog', { name: 'Filtros de la agenda' })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button', { name: 'Cerrar ayuda' })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Saber más' })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(boton).toBeFocused();
  await boton.click();
  await page.getByRole('heading', { level: 1, name: 'Agenda', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Filtros de la agenda' })).toHaveCount(0);
  await page.evaluate(() => { document.documentElement.dataset.theme = 'dark'; });
  await boton.click();
  await page.screenshot({ path: testInfo.outputPath('ayuda-tablet-oscuro.png'), fullPage: true });
});

test('Barra recibe ayuda de devolución y mantiene ocultos los indicadores restringidos', async ({ page }) => {
  await preparar(page, ['BARRA']);
  await page.goto('/existencias');
  await expect(page.getByRole('list', { name: 'Existencias por bebida' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Ayuda sobre estado de las existencias' })).toHaveCount(0);
  await page.goto('/barra');
  await expect(page.getByRole('button', { name: 'Ayuda sobre devoluciones y remanentes' })).toBeVisible();
  await page.goto('/reportes');
  await expect(page.getByText('Tu perfil no tiene acceso a esta pantalla')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Ayuda sobre cálculo de ocupación' })).toHaveCount(0);
});
