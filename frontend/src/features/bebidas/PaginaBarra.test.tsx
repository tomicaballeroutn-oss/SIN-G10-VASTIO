import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import type { BebidaParaRetiro, Entrega, EventoDeLaJornada, Retiro } from '../../api/entregas';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';
import { resumenDeSalida } from './salida';

/** La cámara: el test decide qué código se lee. */
const sinCamara = vi.hoisted(() => () => undefined);
const camara = vi.hoisted(() => ({ leer: sinCamara as (codigo: string) => void }));
vi.mock('@zxing/browser', () => ({
  BrowserMultiFormatReader: class {
    decodeFromConstraints(_c: unknown, _v: unknown, alLeer: (r: { getText: () => string }) => void) {
      camara.leer = (codigo) => alLeer({ getText: () => codigo });
      return Promise.resolve({ stop: () => undefined });
    }
  },
}));

const bebida = (id: number, nombre: string, extra: Partial<BebidaParaRetiro> = {}): BebidaParaRetiro => ({
  id, nombre, presentacion: '750 ml', unidad: 'Caja', unidadesPorBulto: 6, codigos: [], ...extra,
});
const BEBIDAS = [
  bebida(1, 'Fernet Branca', { codigos: [{ codigo: '17790000000016', unidades: 6 }] }),
  bebida(2, 'Cerveza Quilmes', { presentacion: '1 l', unidadesPorBulto: 12 }),
  bebida(3, 'Vodka Smirnoff', { presentacion: '700 ml', unidad: 'Botella', unidadesPorBulto: 1 }),
];
const BARRA = { id: 2, nombre: 'Barra Avril', tipo: 'BARRA' as const };
const DEPOSITO = { id: 1, nombre: 'Depósito principal', tipo: 'DEPOSITO' as const };
const TRANSICION = { id: 5, nombre: 'Transición Avril', tipo: 'TRANSICION' as const };
const EVENTO = { id: 10, codigo: 'EV-2026-00001', nombre: 'Quince de Martina', estado: 'EN_CURSO' };
const JORNADA: EventoDeLaJornada[] = [{ evento: EVENTO, salon: { id: 1, codigo: 'avril', nombre: 'Avril' }, barras: [BARRA] }];

const SIN_RETIROS: Entrega = {
  evento: EVENTO, salon: JORNADA[0].salon, barra: BARRA, origen: DEPOSITO, depositoMadre: null, estadoEntrega: null,
  yaEntregado: [], retiros: [], bebidas: BEBIDAS,
};
const SALIDA: Retiro = {
  tipo: 'SALIDA_BARRA', origen: DEPOSITO, usuario: { id: 7, nombre: 'Rocío Blanco' }, fechaHora: '2026-10-18T21:10:00-03:00',
  renglones: [{ bebidaId: 1, nombre: 'Fernet Branca', presentacion: '750 ml', unidad: 'Caja', unidadesPorBulto: 6, cantidad: 12 }],
};
const CON_ENTREGA: Entrega = {
  ...SIN_RETIROS, estadoEntrega: 'ABIERTA', retiros: [SALIDA], yaEntregado: SALIDA.renglones,
};

function backend(pantalla: Entrega = SIN_RETIROS, jornada: EventoDeLaJornada[] = JORNADA) {
  return ({ metodo, ruta, cuerpo }: Pedido) => {
    if (metodo === 'GET' && ruta === '/entregas/jornada') return json(200, jornada);
    if (metodo === 'GET' && ruta === '/eventos/10/entregas/2') return json(200, pantalla);
    if (metodo === 'POST' && ruta === '/eventos/10/entregas/2/retiros') {
      const datos = cuerpo as { renglones: { bebidaId: number; cantidad: number }[] };
      if (datos.renglones.some((r) => r.bebidaId === 3)) {
        return problema(409, 'ENTREGA_ABIERTA_EN_LA_BARRA', 'Primero registrá la devolución de Almuerzo de Arcor.');
      }
      const nuevo: Retiro = {
        ...SALIDA, tipo: pantalla.estadoEntrega ? 'RETIRO_ADICIONAL' : 'SALIDA_BARRA', fechaHora: '2026-10-18T23:40:00-03:00',
        renglones: datos.renglones.map((r) => ({ ...SALIDA.renglones[0], ...BEBIDAS.find((b) => b.id === r.bebidaId), bebidaId: r.bebidaId, cantidad: r.cantidad })),
      };
      return json(201, { ...pantalla, estadoEntrega: 'ABIERTA', retiros: [...pantalla.retiros, nuevo], yaEntregado: nuevo.renglones });
    }
    return undefined;
  };
}

beforeEach(() => {
  _reiniciarCliente();
  camara.leer = sinCamara;
});

/** Los recorridos con muchos clics, en máquinas lentas. */
const LARGO = 20_000;

afterEach(() => {
  vi.unstubAllGlobals();
  Reflect.deleteProperty(navigator, 'mediaDevices');
});

async function agregarPorNombre(persona: ReturnType<typeof userEvent.setup>, texto: string, opcion: string) {
  await persona.type(await screen.findByLabelText('Agregar bebida'), texto);
  await persona.click(await screen.findByRole('option', { name: new RegExp(opcion) }));
}

async function sumar(persona: ReturnType<typeof userEvent.setup>, bebidaNombre: string, cajas: number, botellas = 0) {
  const tarjeta = screen.getByRole('group', { name: `Cantidad de ${bebidaNombre}` });
  const botones = within(tarjeta).getAllByRole('button', { name: 'Sumar 1' });
  for (let i = 0; i < cajas; i++) await persona.click(botones[0]);
  for (let i = 0; i < botellas; i++) await persona.click(botones[botones.length - 1]);
}

describe('UI-31 · salida a barra', () => {
  it('el primer retiro entrega a la barra: se carga en cajas, se resume y se registra en botellas', async () => {
    const fetchMock = backendFalso(sesionDe(['BARRA'], 'Rocío Blanco'), backend());
    const persona = userEvent.setup();
    montar('/barra');

    expect(await screen.findByText('Quince de Martina')).toBeInTheDocument();
    expect(screen.getByText('Barra Avril · sale de Depósito principal')).toBeInTheDocument();
    expect(screen.getByText('Todavía no se entregó nada a Barra Avril para este evento.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Entrega a la barra' })).toBeInTheDocument();

    await agregarPorNombre(persona, 'fer', 'Fernet Branca');
    await sumar(persona, 'Fernet Branca', 2);
    await agregarPorNombre(persona, 'quil', 'Cerveza Quilmes');
    await sumar(persona, 'Cerveza Quilmes', 1, 2);
    expect(screen.getByText('Salen 3 cajas y 2 botellas a Barra Avril.')).toBeInTheDocument();

    await persona.click(screen.getByRole('button', { name: 'Revisar retiro' }));
    const revision = screen.getByRole('dialog', { name: 'Revisá el retiro' });
    expect(within(revision).getByText('1 caja y 2 botellas')).toBeInTheDocument();
    expect(within(revision).getByText(/Sale de Depósito principal, para Quince de Martina/)).toBeInTheDocument();
    await persona.click(within(revision).getByRole('button', { name: 'Entregar a barra' }));

    expect(await screen.findByText('Entrega registrada: Salen 3 cajas y 2 botellas a Barra Avril.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toEqual({
      directo: false, renglones: [{ bebidaId: 1, cantidad: 12 }, { bebidaId: 2, cantidad: 14 }],
    });
    // Ya hay entrega: lo siguiente es un retiro adicional.
    expect(screen.getByRole('heading', { name: 'Retiro adicional' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Ya entregado' })).toBeInTheDocument();
  }, LARGO);

  it('muestra lo ya entregado con quién y cuándo, y lo siguiente es un retiro adicional', async () => {
    backendFalso(sesionDe(['BARRA']), backend(CON_ENTREGA));
    const persona = userEvent.setup();
    montar('/barra');

    const entregado = await screen.findByRole('region', { name: 'Ya entregado' });
    expect(within(entregado).getAllByText('2 cajas').length).toBeGreaterThan(0);
    expect(within(entregado).getByText('Entrega a barra')).toBeInTheDocument();
    expect(within(entregado).getByText('Rocío Blanco')).toBeInTheDocument();

    await agregarPorNombre(persona, 'vod', 'Vodka Smirnoff');
    await sumar(persona, 'Vodka Smirnoff', 1);
    expect(screen.getByText('Sale 1 botella a Barra Avril.')).toBeInTheDocument();
    await persona.click(screen.getByRole('button', { name: 'Revisar retiro' }));
    expect(within(screen.getByRole('dialog', { name: 'Revisá el retiro' })).getByRole('button', { name: 'Registrar retiro adicional' }))
      .toBeInTheDocument();
  }, LARGO);

  it('muestra el error del backend sin perder lo cargado', async () => {
    backendFalso(sesionDe(['BARRA']), backend(CON_ENTREGA));
    const persona = userEvent.setup();
    montar('/barra');

    await agregarPorNombre(persona, 'vod', 'Vodka Smirnoff');
    await sumar(persona, 'Vodka Smirnoff', 2);
    await persona.click(screen.getByRole('button', { name: 'Revisar retiro' }));
    await persona.click(within(screen.getByRole('dialog', { name: 'Revisá el retiro' })).getByRole('button', { name: 'Registrar retiro adicional' }));

    expect(await screen.findByText('Primero registrá la devolución de Almuerzo de Arcor.')).toBeInTheDocument();
    await persona.click(screen.getByRole('button', { name: 'Volver a editar' }));
    expect(screen.getByRole('group', { name: 'Cantidad de Vodka Smirnoff' })).toBeInTheDocument();
  }, LARGO);

  it('no deja revisar con una bebida sin cantidad', async () => {
    backendFalso(sesionDe(['COMPRAS']), backend());
    const persona = userEvent.setup();
    montar('/barra');

    await agregarPorNombre(persona, 'fer', 'Fernet Branca');
    await persona.click(screen.getByRole('button', { name: 'Revisar retiro' }));

    expect(screen.getByText('Cargá la cantidad de Fernet Branca o sacala del retiro.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: 'Revisá el retiro' })).not.toBeInTheDocument();
  });

  it('el lector agrega la bebida del código y avisa si el código no está en el catálogo', async () => {
    vi.stubGlobal('isSecureContext', true);
    Object.defineProperty(navigator, 'mediaDevices', { value: { getUserMedia: vi.fn() }, configurable: true });
    backendFalso(sesionDe(['BARRA']), backend());
    const persona = userEvent.setup();
    montar('/barra');

    await persona.click(await screen.findByRole('button', { name: 'Escanear caja' }));
    const lector = await screen.findByRole('dialog', { name: 'Leer código de barras' });
    // La librería de la cámara se carga aparte: se espera a que empiece a leer.
    await waitFor(() => expect(camara.leer).not.toBe(sinCamara));
    act(() => camara.leer('17790000000016'));
    expect(within(lector).getByText('Fernet Branca · 750 ml · Caja de 6 agregada.')).toBeInTheDocument();
    act(() => camara.leer('1234567890123'));
    expect(within(lector).getByText('El código 1234567890123 no está en el catálogo. Elegí la bebida a mano.')).toBeInTheDocument();
    await persona.click(within(lector).getByRole('button', { name: 'Elegir a mano' }));

    expect(screen.getByRole('group', { name: 'Cantidad de Fernet Branca' })).toBeInTheDocument();
  });

  it('ofrece retirar del depósito madre solo si la barra lo permite', async () => {
    const fetchMock = backendFalso(sesionDe(['BARRA']), backend({ ...SIN_RETIROS, origen: TRANSICION, depositoMadre: DEPOSITO }));
    const persona = userEvent.setup();
    montar('/barra');

    const contingencia = await screen.findByRole('switch', { name: /Retirar del depósito madre/ });
    await persona.click(contingencia);
    await agregarPorNombre(persona, 'fer', 'Fernet Branca');
    await sumar(persona, 'Fernet Branca', 1);
    await persona.click(screen.getByRole('button', { name: 'Revisar retiro' }));
    expect(within(screen.getByRole('dialog', { name: 'Revisá el retiro' })).getByText(/Sale de Depósito principal/)).toBeInTheDocument();
    await persona.click(within(screen.getByRole('dialog', { name: 'Revisá el retiro' })).getByRole('button', { name: 'Entregar a barra' }));

    await screen.findByText(/Entrega registrada/);
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toMatchObject({ directo: true });
  }, LARGO);

  it('sin retiro directo no ofrece el depósito madre', async () => {
    backendFalso(sesionDe(['BARRA']), backend());
    montar('/barra');

    await screen.findByLabelText('Agregar bebida');
    expect(screen.queryByRole('switch', { name: /Retirar del depósito madre/ })).not.toBeInTheDocument();
  });

  it('con el evento todavía confirmado lo dice y no deja retirar', async () => {
    const confirmado = { ...EVENTO, estado: 'CONFIRMADO' };
    backendFalso(sesionDe(['BARRA']), backend({ ...SIN_RETIROS, evento: confirmado }, [{ ...JORNADA[0], evento: confirmado }]));
    montar('/barra');

    expect(await screen.findByText('El evento está confirmado')).toBeInTheDocument();
    expect(screen.queryByLabelText('Agregar bebida')).not.toBeInTheDocument();
  });

  it('sin eventos en la jornada lo dice', async () => {
    backendFalso(sesionDe(['BARRA']), backend(SIN_RETIROS, []));
    montar('/barra');

    expect(await screen.findByText('Hoy no hay eventos en tus barras')).toBeInTheDocument();
  });
});

describe('resumen de la salida', () => {
  const r = (b: BebidaParaRetiro, cantidad: number) => ({ bebida: b, cantidad });

  it('suma cajas por unidad y botellas sueltas', () => {
    expect(resumenDeSalida([r(BEBIDAS[0], 36)], 'Barra Avril')).toBe('Salen 6 cajas a Barra Avril.');
    expect(resumenDeSalida([r(BEBIDAS[0], 6)], 'Barra Avril')).toBe('Sale 1 caja a Barra Avril.');
    expect(resumenDeSalida([r(BEBIDAS[0], 13), r(BEBIDAS[2], 2), r(bebida(4, 'Gin', { unidad: 'Pack' }), 6)], 'Barra Club'))
      .toBe('Salen 2 cajas, 1 pack y 3 botellas a Barra Club.');
  });
});
