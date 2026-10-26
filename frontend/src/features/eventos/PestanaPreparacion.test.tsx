import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import type { Bebida } from '../../api/bebidas';
import type { PantallaPreparacion, Preparacion } from '../../api/preparaciones';
import { backendFalso, json, montar, problema, sesionDe, type Pedido } from '../../test/backendFalso';
import { fichaDePrueba } from '../../test/fichas';

const camara = vi.hoisted(() => ({ leer: (() => undefined) as (codigo: string) => void }));
vi.mock('@zxing/browser', () => ({ BrowserMultiFormatReader: class {
  scan(_v: unknown, alLeer: (r: { getText: () => string }) => void) {
    camara.leer = (codigo) => alLeer({ getText: () => codigo });
    return { stop: () => undefined };
  }
} }));
const MADRE = { id: 1, nombre: 'Depósito madre', tipo: 'DEPOSITO' as const };
const DESTINO = { id: 7, nombre: 'Transición Avril', tipo: 'TRANSICION' as const };
const BARRA = { id: 2, nombre: 'Barra Avril', tipo: 'BARRA' as const };
const BEBIDA: Bebida = { id: 12, nombre: 'Fernet Branca', presentacion: '750 ml', tipo: { id: 3, nombre: 'Destilado' },
  unidad: { id: 1, nombre: 'Caja', esBotella: false }, unidadesPorBulto: 6, stockMinimo: null,
  codigos: [{ codigo: '17790000000016', unidades: 6 }], activo: true, fechaBaja: null };
const PANTALLA: PantallaPreparacion = { evento: { id: 5, codigo: 'EV-5', nombre: 'Bruno y Martina', estado: 'CONFIRMADO' }, madre: MADRE,
  circuitos: [{ barra: BARRA, abastecimiento: DESTINO, requierePreparacion: true }], historial: [], orden: null };
const PREPARACION: Preparacion = { id: 90, eventoId: 5, barra: BARRA, origen: MADRE, destino: DESTINO,
  usuario: { id: 1, nombre: 'Tomi Compras' }, fechaHora: '2026-10-19T15:00:00-03:00',
  renglones: [{ bebidaId: 12, nombre: 'Fernet Branca', presentacion: '750 ml', unidad: 'Caja', unidadesPorBulto: 6, cantidad: 13 }] };

function backend(roles: Rol[] = ['COMPRAS'], pantalla = PANTALLA, post?: (p: Pedido) => Response | Promise<Response>) {
  return backendFalso(sesionDe(roles), (p) => {
    if (p.ruta === '/eventos/5') return json(200, fichaDePrueba({ estado: 'CONFIRMADO' }));
    if (p.ruta === '/bebidas') return json(200, [BEBIDA, { ...BEBIDA, id: 13, nombre: 'Gin inactivo', activo: false }]);
    if (p.ruta === '/eventos/5/preparaciones') return p.metodo === 'POST' ? (post?.(p) ?? json(201, PREPARACION)) : json(200, pantalla);
    return undefined;
  });
}
beforeEach(() => _reiniciarCliente());

it('muestra la orden de preparación como referencia, sin precargarla', async () => {
  backend(['COMPRAS'], { ...PANTALLA, orden: { estado: 'CONFIRMADA', renglones: [
    { bebidaId: 12, nombre: 'Fernet Branca', presentacion: '750 ml', unidad: 'Caja', unidadesPorBulto: 6, aRetirar: 48 },
  ] } });
  montar('/eventos/5?pestana=preparacion');

  const tabla = await screen.findByRole('table', { name: 'Según la orden de preparación' });
  expect(within(tabla).getByText('8 cajas')).toBeInTheDocument();
  expect(screen.getByText(/Orden confirmada: es una referencia/)).toBeInTheDocument();
  expect(screen.getByText('Todavía no cargaste bebidas')).toBeInTheDocument();
});
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); Reflect.deleteProperty(navigator, 'mediaDevices'); });

async function cargar() {
  montar('/eventos/5?pestana=preparacion');
  const u = userEvent.setup();
  await u.type(await screen.findByLabelText('Agregar bebida'), 'fer');
  await u.click(await screen.findByRole('option', { name: /Fernet/ }));
  const grupo = screen.getByRole('group', { name: 'Cantidad de Fernet Branca' });
  await u.click(within(grupo).getAllByRole('button', { name: 'Sumar 1' })[0]);
  await u.click(within(grupo).getAllByRole('button', { name: 'Sumar 1' })[0]);
  await u.click(within(grupo).getAllByRole('button', { name: 'Sumar 1' })[1]);
  await u.click(screen.getByRole('button', { name: 'Revisar preparación' }));
  return u;
}
describe('B5-04 · preparación', () => {
  it.each<Rol>(['DIRECCION', 'COORDINACION', 'ADMINISTRACION', 'COMPRAS'])('permite %s y confirma cantidades, usuario e historial', async (rol) => {
    const fetchMock = backend([rol]); const u = await cargar();
    const dialogo = screen.getByRole('dialog', { name: 'Revisá la preparación' });
    expect(within(dialogo).getByText('2 cajas y 1 botella')).toBeVisible();
    expect(within(dialogo).getByText(/Depósito madre.*Transición Avril.*Bruno/)).toBeVisible();
    expect(fetchMock.mock.calls.filter(([ruta,init]) => ruta.endsWith('/preparaciones') && init?.method === 'POST')).toHaveLength(0);
    await u.click(within(dialogo).getByRole('button', { name: 'Registrar preparación' }));
    expect(await screen.findByText('Preparación registrada en Transición Avril.')).toBeVisible();
    expect(screen.getByText('Tomi Compras')).toBeVisible();
    const post = fetchMock.mock.calls.find(([ruta, init]) => ruta.endsWith('/preparaciones') && init?.method === 'POST')!;
    expect(JSON.parse(String(post[1]?.body))).toEqual({ barraId: 2, destinoId: 7, renglones: [{ bebidaId: 12, cantidad: 13 }] });
    expect((post[1]?.headers as Record<string, string>)['Idempotency-Key']).toBeTruthy();
  });
  it.each<Rol>(['VENDEDORA', 'PLANNER'])('oculta preparación para %s', async (rol) => {
    const f = backend([rol]); montar('/eventos/5');
    await screen.findByRole('heading', { name: 'Bruno y Martina' });
    expect(screen.queryByRole('tab', { name: 'Preparación' })).not.toBeInTheDocument();
    expect(f.mock.calls.some(([ruta]) => ruta.endsWith('/preparaciones'))).toBe(false);
  });
  it('conserva carga, clave y contenido si la respuesta se pierde', async () => {
    let numero = 0;
    const f = backend(['COMPRAS'], PANTALLA, () => { if (++numero === 1) throw new TypeError('conexión interrumpida'); return json(200, PREPARACION); });
    const u = await cargar();
    await u.click(screen.getByRole('button', { name: 'Registrar preparación' }));
    expect(await screen.findByText(/No sabemos si la operación se confirmó/)).toBeVisible();
    expect(screen.getByRole('button', { name: 'Volver a editar' })).toBeDisabled();
    await u.click(screen.getByRole('button', { name: 'Recuperar preparación' }));
    await screen.findByText('Preparación registrada en Transición Avril.');
    const llamadas = f.mock.calls.filter(([ruta, init]) => ruta.endsWith('/preparaciones') && init?.method === 'POST');
    expect(llamadas).toHaveLength(2);
    expect(llamadas[0][1]?.body).toEqual(llamadas[1][1]?.body);
    expect((llamadas[0][1]?.headers as Record<string,string>)['Idempotency-Key']).toEqual((llamadas[1][1]?.headers as Record<string,string>)['Idempotency-Key']);
  });
  it('permite volver a editar ante un rechazo confirmado', async () => {
    backend(['COMPRAS'], PANTALLA, () => problema(409, 'CIRCUITO_CAMBIADO', 'Cambió el circuito. Revisá la preparación.'));
    const u = await cargar(); await u.click(screen.getByRole('button', { name: 'Registrar preparación' }));
    expect(await screen.findByText('Cambió el circuito. Revisá la preparación.')).toBeVisible();
    await u.click(screen.getByRole('button', { name: 'Volver a editar' }));
    expect(screen.getByRole('group', { name: 'Cantidad de Fernet Branca' })).toBeVisible();
  });
  it.each([
    [0, 409, 'ESTADO_NO_PERMITE'],
    [500, 409, 'CIRCUITO_CAMBIADO'],
    [500, 422, 'BEBIDA_DADA_DE_BAJA'],
  ])('recupera la edición sin perder cantidades después de %s y %s %s', async (primero, status, codigo) => {
    let numero = 0;
    const f = backend(['COMPRAS'], PANTALLA, () => {
      if (++numero === 1) {
        if (!primero) throw new TypeError('conexión interrumpida');
        return problema(primero, 'ERROR_INTERNO', 'Respuesta incierta.');
      }
      return numero === 2 ? problema(status, codigo, 'La preparación no se registró.') : json(201, PREPARACION);
    });
    const u = await cargar();
    await u.click(screen.getByRole('button', { name: 'Registrar preparación' }));
    await screen.findByText(/No sabemos si la operación se confirmó/);
    await u.click(screen.getByRole('button', { name: 'Recuperar preparación' }));
    await screen.findByText('La preparación no se registró.');
    expect(screen.queryByText(/No sabemos si la operación se confirmó/)).not.toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: 'Volver a editar' }));
    const grupo = screen.getByRole('group', { name: 'Cantidad de Fernet Branca' });
    expect(within(grupo).getAllByRole('textbox').map((x) => (x as HTMLInputElement).value)).toEqual(['2', '1']);
    await u.click(within(grupo).getAllByRole('button', { name: 'Sumar 1' })[1]);
    await u.click(screen.getByRole('button', { name: 'Revisar preparación' }));
    await u.click(screen.getByRole('button', { name: 'Registrar preparación' }));
    await screen.findByText('Preparación registrada en Transición Avril.');
    const posts = f.mock.calls.filter(([ruta, init]) => ruta.endsWith('/preparaciones') && init?.method === 'POST');
    const clave = (i: number) => (posts[i][1]?.headers as Record<string, string>)['Idempotency-Key'];
    expect(posts).toHaveLength(3);
    expect(clave(0)).toBe(clave(1));
    expect(posts[0][1]?.body).toBe(posts[1][1]?.body);
    expect(clave(2)).not.toBe(clave(0));
    expect(JSON.parse(String(posts[2][1]?.body)).renglones[0].cantidad).toBe(14);
  });
  it.each([[403, 'SIN_PERMISO'], [409, 'CLAVE_REUTILIZADA'], [409, 'DESCONOCIDO'], [500, 'ERROR_INTERNO']])(
    'mantiene la operación incierta ante %s %s sin crear otra clave', async (status, codigo) => {
      let numero = 0;
      const f = backend(['COMPRAS'], PANTALLA, () => {
        if (++numero === 1) throw new TypeError('respuesta perdida');
        return numero === 2 ? problema(status, codigo, 'No pudimos recuperar la preparación.') : json(200, PREPARACION);
      });
      const u = await cargar();
      await u.click(screen.getByRole('button', { name: 'Registrar preparación' }));
      await screen.findByText(/No sabemos si la operación se confirmó/);
      await u.click(screen.getByRole('button', { name: 'Recuperar preparación' }));
      await screen.findByText('No pudimos recuperar la preparación.');
      expect(screen.getByRole('button', { name: 'Volver a editar' })).toBeDisabled();
      await u.click(screen.getByRole('button', { name: 'Recuperar preparación' }));
      await screen.findByText('Preparación registrada en Transición Avril.');
      const posts = f.mock.calls.filter(([ruta, init]) => ruta.endsWith('/preparaciones') && init?.method === 'POST');
      expect(posts).toHaveLength(3);
      expect(new Set(posts.map(([, init]) => init?.body)).size).toBe(1);
      expect(new Set(posts.map(([, init]) => (init?.headers as Record<string, string>)['Idempotency-Key'])).size).toBe(1);
    });
  it('confirma sin randomUUID usando bytes criptográficamente seguros', async () => {
    const getRandomValues = vi.fn(globalThis.crypto.getRandomValues.bind(globalThis.crypto));
    vi.stubGlobal('crypto', { getRandomValues });
    const f = backend(); const u = await cargar();
    await u.click(screen.getByRole('button', { name: 'Registrar preparación' }));
    await screen.findByText('Preparación registrada en Transición Avril.');
    expect(getRandomValues).toHaveBeenCalledOnce();
    const post = f.mock.calls.find(([ruta, init]) => ruta.endsWith('/preparaciones') && init?.method === 'POST')!;
    expect((post[1]?.headers as Record<string, string>)['Idempotency-Key']).toMatch(/^[a-f0-9]{32}$/);
  });
  it.each(['ausente', 'falla'])('no envía ni se bloquea si la fuente segura está %s', async (modo) => {
    const fuente = globalThis.crypto;
    vi.stubGlobal('crypto', modo === 'ausente' ? undefined : { getRandomValues: () => { throw new Error('fuente no disponible'); } });
    const f = backend(); const u = await cargar();
    await u.click(screen.getByRole('button', { name: 'Registrar preparación' }));
    await screen.findByText(/No pudimos preparar una confirmación segura/);
    expect(f.mock.calls.filter(([ruta, init]) => ruta.endsWith('/preparaciones') && init?.method === 'POST')).toHaveLength(0);
    expect(screen.getByRole('button', { name: 'Volver a editar' })).toBeEnabled();
    expect(screen.queryByText(/No sabemos si la operación se confirmó/)).not.toBeInTheDocument();
    vi.stubGlobal('crypto', fuente);
    await u.click(screen.getByRole('button', { name: 'Registrar preparación' }));
    await screen.findByText('Preparación registrada en Transición Avril.');
    expect(f.mock.calls.filter(([ruta, init]) => ruta.endsWith('/preparaciones') && init?.method === 'POST')).toHaveLength(1);
  });
  it('no duplica el envío mientras la primera respuesta está pendiente', async () => {
    let resolver!: (r: Response) => void;
    const respuesta = new Promise<Response>((resolve) => { resolver = resolve; });
    const f = backend(['COMPRAS'], PANTALLA, () => respuesta);
    await cargar();
    const boton = screen.getByRole('button', { name: 'Registrar preparación' });
    act(() => { fireEvent.click(boton); fireEvent.click(boton); });
    expect(f.mock.calls.filter(([ruta, init]) => ruta.endsWith('/preparaciones') && init?.method === 'POST')).toHaveLength(1);
    await act(async () => { resolver(json(201, PREPARACION)); });
    await screen.findByText('Preparación registrada en Transición Avril.');
  });
  it('selecciona entre varias barras y explica el circuito directo', async () => {
    backend(['COMPRAS'], { ...PANTALLA, circuitos: [...PANTALLA.circuitos,
      { barra: { ...BARRA, id: 8, nombre: 'Barra directa' }, abastecimiento: MADRE, requierePreparacion: false }] });
    montar('/eventos/5?pestana=preparacion'); const u = userEvent.setup();
    await u.selectOptions(await screen.findByLabelText('Barra'), '8');
    expect(screen.getByText(/no requiere preparación/)).toBeVisible();
    expect(screen.queryByLabelText('Agregar bebida')).not.toBeInTheDocument();
  });
  it.each(['REALIZADO', 'CERRADO', 'CANCELADO'])('conserva historial en %s y bloquea una nueva carga', async (estado) => {
    backend(['COMPRAS'], { ...PANTALLA, evento: { ...PANTALLA.evento, estado }, historial: [PREPARACION] });
    montar('/eventos/5?pestana=preparacion'); await screen.findByText('Tomi Compras');
    expect(screen.queryByLabelText('Agregar bebida')).not.toBeInTheDocument();
    expect(screen.getByText(/Solo podés preparar/)).toBeVisible();
  });
  it('muestra ausencia de barras y de depósito madre', async () => {
    backend(['COMPRAS'], { ...PANTALLA, madre: null, circuitos: [] }); montar('/eventos/5?pestana=preparacion');
    expect(await screen.findByText('No hay barras activas en el salón')).toBeVisible();
    expect(screen.getByText(/No hay un depósito madre activo/)).toBeVisible();
  });
  it('no ofrece bebidas inactivas ni confirma cantidades cero', async () => {
    backend(); montar('/eventos/5?pestana=preparacion'); const u=userEvent.setup();
    await u.type(await screen.findByLabelText('Agregar bebida'), 'gin');
    expect(screen.queryByRole('option', { name:/Gin inactivo/ })).not.toBeInTheDocument();
    await u.clear(screen.getByLabelText('Agregar bebida')); await u.type(screen.getByLabelText('Agregar bebida'), 'fer');
    await u.click(await screen.findByRole('option', { name:/Fernet/ }));
    await u.click(screen.getByRole('button', { name:'Revisar preparación' }));
    expect(screen.getByText(/Cargá cantidades positivas/)).toBeVisible();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
  it('escanea sin sumar cantidades ni duplicar renglones', async () => {
    vi.stubGlobal('isSecureContext', true);
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
    Object.defineProperty(navigator, 'mediaDevices', { configurable:true, value:{ getUserMedia:vi.fn().mockResolvedValue({ getTracks: () => [], getVideoTracks: () => [] }) } });
    backend(); montar('/eventos/5?pestana=preparacion'); const u=userEvent.setup();
    const lecturaAnterior = camara.leer;
    await u.click(await screen.findByRole('button', { name:'Leer código' }));
    await waitFor(() => expect(camara.leer).not.toBe(lecturaAnterior));
    await act(async () => camara.leer('17790000000016'));
    await act(async () => camara.leer('00000000'));
    await act(async () => camara.leer('17790000000016'));
    expect(screen.getByText(/ya está en la preparación/)).toBeVisible();
    await u.click(screen.getByRole('button', { name: 'Elegir a mano' }));
    expect(screen.getAllByRole('group', { name:'Cantidad de Fernet Branca' })).toHaveLength(1);
    expect(within(screen.getByRole('group', { name:'Cantidad de Fernet Branca' })).getAllByRole('textbox').every((x) => (x as HTMLInputElement).value === '0')).toBe(true);
  });
});
