import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import type { BebidaParaRetiro, Devolucion, Entrega, EventoDeLaJornada } from '../../api/entregas';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';

const bebida = (id: number, nombre: string, extra: Partial<BebidaParaRetiro> = {}): BebidaParaRetiro => ({
  id, nombre, presentacion: '750 ml', unidad: 'Caja', unidadesPorBulto: 6, codigos: [], ...extra,
});
const FERNET = bebida(1, 'Fernet Branca');
const VODKA = bebida(3, 'Vodka Smirnoff', { presentacion: '700 ml', unidad: 'Botella', unidadesPorBulto: 1 });
const BARRA = { id: 2, nombre: 'Barra Avril', tipo: 'BARRA' as const };
const DEPOSITO = { id: 1, nombre: 'Depósito principal', tipo: 'DEPOSITO' as const };
const TRANSICION = { id: 5, nombre: 'Transición Avril', tipo: 'TRANSICION' as const };
const EVENTO = { id: 10, codigo: 'EV-2026-00001', nombre: 'Quince de Martina', estado: 'EN_CURSO' };
const SALON = { id: 1, codigo: 'avril', nombre: 'Avril' };

const ABIERTA: Entrega = {
  evento: EVENTO, salon: SALON, barra: BARRA, origen: DEPOSITO, depositoMadre: null, estadoEntrega: 'ABIERTA',
  yaEntregado: [{ bebidaId: 1, nombre: 'Fernet Branca', presentacion: '750 ml', unidad: 'Caja', unidadesPorBulto: 6, cantidad: 12 }],
  retiros: [{
    tipo: 'SALIDA_BARRA', origen: DEPOSITO, usuario: { id: 7, nombre: 'Rocío Blanco' }, fechaHora: '2026-10-18T21:10:00-03:00',
    renglones: [{ bebidaId: 1, nombre: 'Fernet Branca', presentacion: '750 ml', unidad: 'Caja', unidadesPorBulto: 6, cantidad: 12 }],
  }],
  bebidas: [FERNET, VODKA], aDevolver: [FERNET, VODKA], enTransicion: [],
};

const consumo = (b: BebidaParaRetiro, entregado: number, devuelto: number, remanente: number) => ({
  bebidaId: b.id, nombre: b.nombre, presentacion: b.presentacion, unidad: b.unidad, unidadesPorBulto: b.unidadesPorBulto,
  entregado, devuelto, remanente, consumo: entregado - devuelto - remanente,
});

function backend(pantalla: Entrega = ABIERTA) {
  const jornada: EventoDeLaJornada[] = [{ evento: pantalla.evento, salon: SALON, barras: [BARRA] }];
  return ({ metodo, ruta, cuerpo }: Pedido) => {
    if (metodo === 'GET' && ruta === '/entregas/jornada') return json(200, jornada);
    if (metodo === 'GET' && ruta === '/eventos/10/entregas/2') return json(200, pantalla);
    if (metodo === 'POST' && ruta === '/eventos/10/entregas/2/devolucion') {
      const datos = cuerpo as { renglones: { bebidaId: number; devuelto: number; remanente: number }[] };
      const fernet = datos.renglones.find((r) => r.bebidaId === 1)!;
      if (fernet.devuelto + fernet.remanente > 12) {
        return problema(422, 'SUPERA_LO_DE_LA_BARRA',
          'Fernet Branca 750 ml: lo que vuelve y lo que queda suman más de lo que hay en la barra. Revisá el conteo.');
      }
      const respuesta: Devolucion = {
        entrega: { ...pantalla, estadoEntrega: 'CERRADA', aDevolver: [], enTransicion: [] },
        consumo: [consumo(FERNET, 12, fernet.devuelto, fernet.remanente), consumo(VODKA, 2, 0, 0)],
        desdeTransicion: [],
      };
      return json(201, respuesta);
    }
    return undefined;
  };
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

/** Los recorridos con muchos clics, en máquinas lentas. */
const LARGO = 20_000;

async function cargar(persona: ReturnType<typeof userEvent.setup>, bebidaNombre: string, campo: string, cajas: number, botellas = 0) {
  const tarjeta = screen.getByRole('listitem', { name: bebidaNombre });
  const botones = within(within(tarjeta).getByRole('group', { name: campo })).getAllByRole('button', { name: 'Sumar 1' });
  for (let i = 0; i < cajas; i++) await persona.click(botones[0]);
  for (let i = 0; i < botellas; i++) await persona.click(botones[botones.length - 1]);
}

describe('UI-32 · devolución de bebida', () => {
  it('se declara a ciegas y recién después de registrar muestra el consumo real', async () => {
    const fetchMock = backendFalso(sesionDe(['BARRA'], 'Rocío Blanco'), backend());
    const persona = userEvent.setup();
    montar('/barra');

    await persona.click(await screen.findByRole('button', { name: 'Registrar devolución' }));
    const devolucion = screen.getByRole('region', { name: 'Devolución' });
    // A ciegas: ni lo entregado ni lo esperado.
    expect(within(devolucion).queryByText(/Entregado/)).not.toBeInTheDocument();
    expect(within(devolucion).queryByText(/2 cajas/)).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Ya entregado' })).not.toBeInTheDocument();

    // Cuenta de control: salieron 12, vuelven 6 (1 caja), quedan 2 → consumo 4.
    await cargar(persona, 'Fernet Branca 750 ml', 'Vuelve al depósito', 1);
    await cargar(persona, 'Fernet Branca 750 ml', 'Queda en la barra (remanente)', 0, 2);
    await persona.click(screen.getByRole('button', { name: 'Revisar devolución' }));
    const revision = screen.getByRole('dialog', { name: 'Revisá la devolución' });
    expect(within(revision).getByText('vuelve 1 caja · queda 2 botellas')).toBeInTheDocument();
    await persona.click(within(revision).getByRole('button', { name: 'Registrar devolución' }));

    expect(await screen.findByText('Devolución registrada')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toEqual({
      renglones: [{ bebidaId: 1, devuelto: 6, remanente: 2 }, { bebidaId: 3, devuelto: 0, remanente: 0 }],
      transicion: [],
    });
    const tabla = screen.getByRole('table', { name: 'Consumo real por bebida' });
    const fila = within(tabla).getByRole('row', { name: /Fernet Branca/ });
    expect(within(fila).getByText('2 cajas')).toBeInTheDocument(); // entregado
    expect(within(fila).getByText('4 botellas')).toBeInTheDocument(); // consumo
    expect(screen.queryByRole('button', { name: 'Registrar devolución' })).not.toBeInTheDocument();
  }, LARGO);

  it('si supera lo de la barra muestra el error nombrando la bebida, sin perder lo cargado', async () => {
    backendFalso(sesionDe(['BARRA']), backend());
    const persona = userEvent.setup();
    montar('/barra');

    await persona.click(await screen.findByRole('button', { name: 'Registrar devolución' }));
    await cargar(persona, 'Fernet Branca 750 ml', 'Vuelve al depósito', 2, 1);
    await persona.click(screen.getByRole('button', { name: 'Revisar devolución' }));
    await persona.click(within(screen.getByRole('dialog', { name: 'Revisá la devolución' })).getByRole('button', { name: 'Registrar devolución' }));

    expect(await screen.findByText(/Fernet Branca 750 ml: lo que vuelve y lo que queda suman más/)).toBeInTheDocument();
    await persona.click(screen.getByRole('button', { name: 'Volver a editar' }));
    expect(screen.getByRole('region', { name: 'Devolución' })).toBeInTheDocument();
  }, LARGO);

  it('desde la devolución se puede volver a retirar mientras el evento sigue en curso', async () => {
    backendFalso(sesionDe(['BARRA']), backend());
    const persona = userEvent.setup();
    montar('/barra');

    await persona.click(await screen.findByRole('button', { name: 'Registrar devolución' }));
    await persona.click(screen.getByRole('button', { name: 'Seguir retirando' }));
    expect(screen.getByRole('region', { name: 'Ya entregado' })).toBeInTheDocument();
    expect(screen.getByLabelText('Agregar bebida')).toBeInTheDocument();
  });

  it('con el evento realizado va directo a la devolución', async () => {
    backendFalso(sesionDe(['BARRA']), backend({ ...ABIERTA, evento: { ...EVENTO, estado: 'REALIZADO' } }));
    montar('/barra');

    expect(await screen.findByRole('region', { name: 'Devolución' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Seguir retirando' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Agregar bebida')).not.toBeInTheDocument();
  });

  it('si la barra se abastece de una transición pide lo que queda ahí', async () => {
    const fetchMock = backendFalso(sesionDe(['BARRA']), backend({ ...ABIERTA, origen: TRANSICION, enTransicion: [FERNET] }));
    const persona = userEvent.setup();
    montar('/barra');

    await persona.click(await screen.findByRole('button', { name: 'Registrar devolución' }));
    expect(screen.getByRole('heading', { name: 'Queda en Transición Avril' })).toBeInTheDocument();
    await cargar(persona, 'Fernet Branca 750 ml en la transición', 'Queda en la transición', 1);
    await persona.click(screen.getByRole('button', { name: 'Revisar devolución' }));
    await persona.click(within(screen.getByRole('dialog', { name: 'Revisá la devolución' })).getByRole('button', { name: 'Registrar devolución' }));

    await screen.findByText('Devolución registrada');
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toMatchObject({ transicion: [{ bebidaId: 1, cantidad: 6 }] });
  }, LARGO);

  it('con la entrega cerrada no ofrece devolver ni retirar', async () => {
    backendFalso(sesionDe(['BARRA']), backend({ ...ABIERTA, estadoEntrega: 'CERRADA', aDevolver: [] }));
    montar('/barra');

    expect(await screen.findByText('La entrega a Barra Avril ya se cerró')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Registrar devolución' })).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Agregar bebida')).not.toBeInTheDocument();
  });
});
