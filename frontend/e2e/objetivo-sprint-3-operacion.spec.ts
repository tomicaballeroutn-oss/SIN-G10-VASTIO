import { expect, request, test, type APIRequestContext, type Browser, type Page } from '@playwright/test';

/**
 * Objetivo del Sprint 3, la noche del evento (docs/sprint-3-operacion.md): el evento confirmado de hoy entra solo en
 * curso, la barra retira (salida y retiro adicional), registra la devolución y el remanente, el evento pasa solo a
 * Realizado y al cerrarlo se ve el consumo real y por asistente. El remanente queda en la barra para el evento siguiente.
 *
 * Usa el evento de demostración de hoy («Quince de Martina Ortiz», Avril, noche) con la Barra Avril, que arranca con
 * 9 botellas de Fernet y 30 de cerveza: se corre una vez por carga de los datos de demostración (para repetir:
 * docker compose down -v y volver a levantar). Para no esperar a las 20:00, Coordinación corre el horario del turno
 * noche por la API y lo deja como estaba al final. Tarda unos minutos: espera al proceso automático, que corre por minuto.
 *   $env:E2E_DEMO_CONTRASENA='...'; npm run e2e
 */
const contrasena = process.env.E2E_DEMO_CONTRASENA ?? '';
const EVENTO = 'Quince de Martina Ortiz';
const ZONA = 'America/Argentina/Cordoba';

test.skip(!contrasena, 'Definí E2E_DEMO_CONTRASENA con la contraseña de los datos de demostración');

interface Turno { id: number; codigo: string; nombre: string; horaInicio: string; horaFin: string }

async function ingresar(browser: Browser, usuario: string, opciones: Parameters<Browser['newContext']>[0]): Promise<Page> {
  const pagina = await (await browser.newContext(opciones)).newPage();
  await pagina.goto('/login');
  await pagina.getByLabel('Usuario').fill(usuario);
  await pagina.getByLabel('Contraseña').fill(contrasena);
  await pagina.getByRole('button', { name: 'Ingresar' }).click();
  await expect(pagina).not.toHaveURL(/\/login/);
  await expect(pagina.getByRole('heading', { level: 1 })).toBeVisible();
  return pagina;
}

/** La API con el token de esa persona (para lo que en la vida real hace el reloj). */
async function api(baseURL: string, usuario: string): Promise<APIRequestContext> {
  const sinSesion = await request.newContext({ baseURL });
  const login = await sinSesion.post('/api/v1/auth/login', { data: { nombreUsuario: usuario, contrasena } });
  expect(login.ok()).toBe(true);
  const { tokenAcceso } = await login.json();
  return request.newContext({ baseURL, extraHTTPHeaders: { Authorization: `Bearer ${tokenAcceso}` } });
}

/** «HH:mm» en Córdoba, corrido esos minutos. */
function hora(minutos: number): string {
  return new Intl.DateTimeFormat('es-AR', { timeZone: ZONA, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
    .format(new Date(Date.now() + minutos * 60_000));
}

async function estadoDelEvento(barra: APIRequestContext): Promise<string | undefined> {
  const jornada: { evento: { nombre: string; estado: string } }[] = await (await barra.get('/api/v1/entregas/jornada')).json();
  const enJornada = jornada.find((j) => j.evento.nombre === EVENTO)?.evento.estado;
  if (enJornada) return enJornada;
  const porCerrar: { evento: { nombre: string; estado: string } }[] = await (await barra.get('/api/v1/cierres/pendientes')).json();
  return porCerrar.find((j) => j.evento.nombre === EVENTO)?.evento.estado;
}

/** Suma en el contador de una tarjeta: el primero es de cajas y el último de botellas sueltas. */
async function sumar(grupo: ReturnType<Page['getByRole']>, cajas: number, botellas = 0) {
  const botones = grupo.getByRole('button', { name: 'Sumar 1' });
  for (let i = 0; i < cajas; i++) await botones.first().click();
  for (let i = 0; i < botellas; i++) await botones.last().click();
}

test('la noche del evento: en curso, salida y retiro, devolución, realizado, cierre y remanente en la barra', async (
  { browser, isMobile, viewport, userAgent, baseURL }, info) => {
  // Consume el evento de hoy: una sola vez, en escritorio.
  test.skip(info.project.name !== 'escritorio', 'Usa el evento de demostración de hoy: corre solo en escritorio');
  test.setTimeout(8 * 60_000);
  const contexto = { viewport, userAgent, isMobile, hasTouch: isMobile, ignoreHTTPSErrors: process.env.E2E_IGNORAR_CERTIFICADO === '1' };
  const coordinacion = await api(baseURL!, 'melina.sifon');
  const barraApi = await api(baseURL!, 'rocio.blanco');
  const turnos: Turno[] = await (await coordinacion.get('/api/v1/turnos')).json();
  const noche = turnos.find((t) => t.codigo === 'noche')!;
  const turno = (horaInicio: string, horaFin: string) =>
    coordinacion.put(`/api/v1/turnos/${noche.id}`, { data: { nombre: noche.nombre, horaInicio, horaFin } });

  try {
    // 1. El turno noche empieza hace dos minutos: el evento confirmado de hoy pasa solo a En curso.
    expect((await turno(hora(-2), noche.horaFin.slice(0, 5))).ok()).toBe(true);
    await expect.poll(() => estadoDelEvento(barraApi), { timeout: 150_000, intervals: [5_000] }).toBe('EN_CURSO');

    // 2. Salida a barra: 2 cajas de Fernet. Después, un retiro adicional de 1 caja de cerveza.
    const barra = await ingresar(browser, 'rocio.blanco', contexto);
    await barra.goto('/barra');
    await expect(barra.getByText(EVENTO)).toBeVisible();
    await barra.getByRole('combobox', { name: 'Agregar bebida' }).fill('Fernet');
    await barra.getByRole('option', { name: /Fernet Branca/ }).click();
    await sumar(barra.getByRole('group', { name: 'Cantidad de Fernet Branca' }), 2);
    await expect(barra.getByText('Salen 2 cajas a Barra Avril.')).toBeVisible();
    await barra.getByRole('button', { name: 'Revisar retiro' }).click();
    await barra.getByRole('dialog', { name: 'Revisá el retiro' }).getByRole('button', { name: 'Entregar a barra' }).click();
    await expect(barra.getByText(/Entrega registrada/)).toBeVisible();

    await barra.getByRole('combobox', { name: 'Agregar bebida' }).fill('Quilmes');
    await barra.getByRole('option', { name: /Cerveza Quilmes/ }).click();
    await sumar(barra.getByRole('group', { name: 'Cantidad de Cerveza Quilmes Clásica' }), 1);
    await barra.getByRole('button', { name: 'Revisar retiro' }).click();
    await barra.getByRole('dialog', { name: 'Revisá el retiro' }).getByRole('button', { name: 'Registrar retiro adicional' }).click();
    await expect(barra.getByText(/Retiro adicional registrado/)).toBeVisible();
    const entregado = barra.getByRole('region', { name: 'Ya entregado' });
    await expect(entregado.getByText('Retiro adicional')).toBeVisible();
    // A ciegas: nunca el saldo del depósito (le quedan 87 − 12 = 75 botellas de Fernet: 12 cajas y 3 botellas).
    await expect(barra.getByText('12 cajas y 3 botellas')).toHaveCount(0);

    // 3. Devolución a ciegas. Fernet: había 9 + 12; vuelve 1 caja y quedan 2 botellas. Cerveza: había 30 + 12; vuelven 3 cajas.
    await barra.getByRole('button', { name: 'Registrar devolución' }).click();
    const devolucion = barra.getByRole('region', { name: 'Devolución' });
    await expect(devolucion.getByText(/Entregado|Consumo/)).toHaveCount(0);
    const fernet = devolucion.getByRole('listitem', { name: 'Fernet Branca 750 ml' });
    await sumar(fernet.getByRole('group', { name: 'Vuelve al depósito' }), 1);
    await sumar(fernet.getByRole('group', { name: 'Queda en la barra (remanente)' }), 0, 2);
    await sumar(devolucion.getByRole('listitem', { name: 'Cerveza Quilmes Clásica 1 l' }).getByRole('group', { name: 'Vuelve al depósito' }), 3);
    await barra.getByRole('button', { name: 'Revisar devolución' }).click();
    await barra.getByRole('dialog', { name: 'Revisá la devolución' }).getByRole('button', { name: 'Registrar devolución' }).click();
    await expect(barra.getByText('Devolución registrada')).toBeVisible();
    // Recién ahora, el consumo real: Fernet 21 − 6 − 2 = 13 (2 cajas y 1 botella); cerveza 42 − 36 − 0 = 6.
    const consumo = barra.getByRole('table', { name: 'Consumo real por bebida' });
    await expect(consumo.getByRole('row', { name: /Fernet Branca/ })).toContainText('2 cajas y 1 botella');
    await expect(consumo.getByRole('row', { name: /Cerveza Quilmes/ })).toContainText('6 botellas');

    // 4. El turno termina ahora: el evento pasa solo a Realizado.
    expect((await turno(hora(-3), hora(0))).ok()).toBe(true);
    await expect.poll(() => estadoDelEvento(barraApi), { timeout: 150_000, intervals: [5_000] }).toBe('REALIZADO');

    // 5. Cierre: el consumo real y por asistente (180 invitados), sin importes.
    await barra.goto('/barra');
    await barra.getByRole('region', { name: 'Eventos para cerrar' }).locator('li').filter({ hasText: EVENTO })
      .getByRole('button', { name: 'Ir al cierre' }).click();
    const cierre = barra.getByRole('table', { name: 'Consumo por bebida' });
    await expect(cierre.getByRole('row', { name: /Fernet Branca/ })).toContainText('2 cajas y 1 botella');
    await expect(barra.getByText(/por asistente, sobre 180 invitados/)).toBeVisible();
    await expect(barra.getByText(/\$|valorizado/i)).toHaveCount(0);
    await barra.getByRole('button', { name: 'Cerrar evento' }).click();
    await barra.getByRole('dialog', { name: '¿Cerrás el evento?' }).getByRole('button', { name: 'Registrar cierre' }).click();
    await expect(barra.getByText('Evento cerrado')).toBeVisible();
    await expect(barra.getByRole('button', { name: 'Cerrar evento' })).toHaveCount(0);

    // 6. El remanente queda en la barra: es con lo que arranca el evento siguiente (y la cerveza, toda consumida o devuelta).
    const compras = await ingresar(browser, 'nicolas.herrera', contexto);
    await compras.goto('/existencias');
    await compras.getByLabel('Ubicación').selectOption({ label: 'Barra Avril (barra)' });
    await expect(compras.locator('li').filter({ hasText: 'Fernet Branca' })).toContainText('2 botellas');
    await expect(compras.locator('li').filter({ hasText: 'Cerveza Quilmes' })).toHaveCount(0);
  } finally {
    await turno(noche.horaInicio.slice(0, 5), noche.horaFin.slice(0, 5));
  }
});
