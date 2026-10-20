import { expect, request, test } from '@playwright/test';

/** Solo base de demostración aislada: configura temporalmente el circuito y registra movimientos reales. */
const contrasena = process.env.E2E_DEMO_CONTRASENA ?? '';
test.skip(!contrasena || process.env.E2E_PREPARACION_AISLADO !== '1', 'Requiere demo aislada y E2E_PREPARACION_AISLADO=1');
test.setTimeout(90_000);

test('preparación manual, respuesta perdida e historial sin duplicación', async ({ page, baseURL }, info) => {
  const conexion = await request.newContext({ baseURL });
  const login = await conexion.post('/api/v1/auth/login', { data: { nombreUsuario: 'direccion', contrasena } });
  expect(login.ok()).toBe(true);
  const { tokenAcceso } = await login.json();
  const api = await request.newContext({ baseURL, extraHTTPHeaders: { Authorization: `Bearer ${tokenAcceso}` } });
  const eventos = await (await api.get('/api/v1/eventos')).json() as { id: number; estado: string; salon: { id: number } }[];
  const evento = eventos.find((e) => e.estado === 'CONFIRMADO' && e.salon.id === 1)!;
  expect(evento).toBeTruthy();
  const ubicaciones = await (await api.get('/api/v1/ubicaciones')).json() as { id: number; nombre: string; tipo: string; salon: { id: number } | null; abastecimiento: { id: number } | null; permiteRetiroDirecto: boolean }[];
  const barra = ubicaciones.find((u) => u.tipo === 'BARRA' && u.salon?.id === 1)!;
  const madre = ubicaciones.find((u) => u.tipo === 'DEPOSITO')!;
  const transicionRespuesta = await api.post('/api/v1/ubicaciones', { data: { nombre: `Transición E2E ${info.project.name} ${Date.now()}`, tipo: 'TRANSICION', salonId: null } });
  expect(transicionRespuesta.ok()).toBe(true);
  const transicion = await transicionRespuesta.json();
  const configuracion = { nombre: barra.nombre, salonId: 1, abastecimientoId: transicion.id, permiteRetiroDirecto: false };
  expect((await api.put(`/api/v1/ubicaciones/${barra.id}`, { data: configuracion })).ok()).toBe(true);
  try {
    const bebidas = await (await api.get('/api/v1/bebidas')).json() as { id: number; nombre: string; activo: boolean; unidadesPorBulto: number }[];
    const bebida = bebidas.find((b) => b.activo && b.unidadesPorBulto === 6)!;
    const ruta = `/api/v1/eventos/${evento.id}/preparaciones`;
    const antes = await (await api.get(ruta)).json();
    const saldo = async (id: number) => {
      const s = await (await api.get(`/api/v1/stock?ubicacionId=${id}`)).json();
      return s.renglones.find((r: { bebida: { id: number } }) => r.bebida.id === bebida.id)?.cantidad ?? 0;
    };
    const origenAntes = await saldo(madre.id);
    await page.goto('/login');
    await page.getByLabel('Usuario').fill('direccion'); await page.getByLabel('Contraseña').fill(contrasena);
    await page.getByRole('button', { name: 'Ingresar' }).click(); await expect(page).not.toHaveURL(/\/login/);
    await page.goto(`/eventos/${evento.id}?pestana=preparacion`);
    await expect(page.getByLabel('Sale de')).toBeVisible();
    await page.getByLabel('Agregar bebida').fill(bebida.nombre);
    await page.getByRole('option', { name: new RegExp(bebida.nombre) }).click();
    const grupo = page.getByRole('group', { name: `Cantidad de ${bebida.nombre}` });
    await grupo.getByRole('button', { name: 'Sumar 1' }).first().click();
    await grupo.getByRole('button', { name: 'Sumar 1' }).first().click();
    await grupo.getByRole('button', { name: 'Sumar 1' }).last().click();
    await page.getByRole('button', { name: 'Revisar preparación' }).click();
    await expect(page.getByRole('dialog')).toContainText('2 cajas y 1 botella');
    let clave = ''; let perdida = false;
    await page.route(`**${ruta}`, async (route) => {
      if (route.request().method() !== 'POST') return route.continue();
      const actual = route.request().headers()['idempotency-key'];
      if (!perdida) {
        clave = actual;
        const respuesta = await route.fetch(); expect(respuesta.status()).toBe(201);
        perdida = true;
        await route.abort('failed');
      } else {
        expect(actual).toBe(clave);
        await route.continue();
      }
    });
    await page.getByRole('button', { name: 'Registrar preparación', exact: true }).click();
    await expect(page.getByText(/No sabemos si la operación se confirmó/)).toBeVisible();
    await page.getByRole('button', { name: 'Recuperar preparación' }).click();
    await expect(page.getByText(`Preparación registrada en ${transicion.nombre}.`)).toBeVisible();
    const despues = await (await api.get(ruta)).json();
    expect(despues.historial).toHaveLength(antes.historial.length + 1);
    expect(despues.historial[0].renglones).toEqual(expect.arrayContaining([expect.objectContaining({ bebidaId: bebida.id, cantidad: 13 })]));
    expect(await saldo(madre.id)).toBe(origenAntes - 13);
    expect(await saldo(transicion.id)).toBe(13);
    await page.reload();
    const registro = page.getByText(`${madre.nombre} → ${transicion.nombre} · ${barra.nombre}`, { exact: true }).locator('..');
    await expect(registro.getByText('2 cajas y 1 botella')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: `test-results/preparacion-${info.project.name}.png`, fullPage: true });
  } finally {
    await api.put(`/api/v1/ubicaciones/${barra.id}`, { data: { nombre: barra.nombre, salonId: 1, abastecimientoId: barra.abastecimiento?.id, permiteRetiroDirecto: barra.permiteRetiroDirecto } });
    await api.dispose(); await conexion.dispose();
  }
});
