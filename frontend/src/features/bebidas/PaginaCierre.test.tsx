import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Cierre, EventoPorCerrar } from '../../api/cierres';
import { _reiniciarCliente } from '../../api/cliente';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';

const EVENTO = { id: 10, codigo: 'EV-2026-00001', nombre: 'Quince de Martina', estado: 'REALIZADO' };
const SALON = { id: 1, codigo: 'avril', nombre: 'Avril' };
const fila = (bebidaId: number, nombre: string, entregado: number, devuelto: number, remanente: number, porAsistente: number | null) => ({
  bebidaId, nombre, presentacion: '750 ml', unidad: 'Caja', unidadesPorBulto: 6,
  entregado, devuelto, remanente, consumo: entregado - devuelto - remanente, porAsistente,
});
const PARA_CERRAR: Cierre = {
  evento: EVENTO, salon: SALON, invitados: 180, usaSegmentos: false, pendientes: [],
  // Cuenta de control: salieron 12, volvieron 6, quedaron 2 → se consumieron 4.
  renglones: [fila(1, 'Fernet Branca', 12, 6, 2, 0.0222), fila(2, 'Gin Bombay', 6, 0, 0, 0.0333)],
  consumoTotal: 10, porAsistenteTotal: 0.0556, cerrado: false,
};

function backend(cierre: Cierre = PARA_CERRAR) {
  return ({ metodo, ruta }: Pedido) => {
    if (metodo === 'GET' && ruta === '/eventos/10/cierre') return json(200, cierre);
    if (metodo === 'POST' && ruta === '/eventos/10/cierre') {
      if (cierre.pendientes.length) return problema(409, 'DEVOLUCION_PENDIENTE', 'Falta registrar la devolución de Barra Avril.');
      return json(200, { ...cierre, cerrado: true, evento: { ...EVENTO, estado: 'CERRADO' } });
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

describe('UI-33 · cierre de evento', () => {
  it('muestra la conciliación sin importes y al cerrar fija el consumo', async () => {
    const fetchMock = backendFalso(sesionDe(['BARRA'], 'Rocío Blanco'), backend());
    const persona = userEvent.setup();
    montar('/barra/cierre/10');

    const tabla = await screen.findByRole('table', { name: 'Consumo por bebida' });
    const fernet = within(tabla).getByRole('row', { name: /Fernet Branca/ });
    expect(within(fernet).getByText('2 cajas')).toBeInTheDocument(); // entregado
    expect(within(fernet).getByText('1 caja')).toBeInTheDocument(); // devuelto
    expect(within(fernet).getByText('2 botellas')).toBeInTheDocument(); // remanente
    expect(within(fernet).getByText('4 botellas')).toBeInTheDocument(); // consumo
    expect(within(fernet).getByText('0,022 botellas')).toBeInTheDocument(); // por asistente
    expect(screen.getByText(/por asistente, sobre 180 invitados/)).toBeInTheDocument();
    // Sin dinero: ni importes ni «valorizado».
    expect(screen.queryByText(/\$|valorizado/i)).not.toBeInTheDocument();

    await persona.click(screen.getByRole('button', { name: 'Cerrar evento' }));
    await persona.click(within(screen.getByRole('dialog', { name: '¿Cerrás el evento?' })).getByRole('button', { name: 'Registrar cierre' }));

    expect(await screen.findByText('Evento cerrado')).toBeInTheDocument();
    expect(pedidos(fetchMock).some((p) => p.metodo === 'POST' && p.ruta === '/eventos/10/cierre')).toBe(true);
    expect(screen.queryByRole('button', { name: 'Cerrar evento' })).not.toBeInTheDocument();
  });

  it('si falta una devolución lo dice y no deja cerrar', async () => {
    backendFalso(sesionDe(['COMPRAS']), backend({ ...PARA_CERRAR, pendientes: [{ id: 2, nombre: 'Barra Avril', tipo: 'BARRA' }] }));
    montar('/barra/cierre/10');

    expect(await screen.findByText(/Falta la devolución de Barra Avril/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cerrar evento' })).toBeDisabled();
  });

  it('un evento en curso todavía no se cierra', async () => {
    backendFalso(sesionDe(['BARRA']), backend({ ...PARA_CERRAR, evento: { ...EVENTO, estado: 'EN_CURSO' } }));
    montar('/barra/cierre/10');

    expect(await screen.findByText('Todavía no se puede cerrar')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cerrar evento' })).toBeDisabled();
  });

  it('con un tipo de evento con segmentos aclara que divide por los invitados', async () => {
    backendFalso(sesionDe(['BARRA']), backend({ ...PARA_CERRAR, usaSegmentos: true }));
    montar('/barra/cierre/10');

    expect(await screen.findByText(/usa asistencia por segmento, que todavía no se registra/)).toBeInTheDocument();
  });

  it('un evento cerrado es solo lectura', async () => {
    backendFalso(sesionDe(['BARRA']), backend({ ...PARA_CERRAR, cerrado: true, evento: { ...EVENTO, estado: 'CERRADO' } }));
    montar('/barra/cierre/10');

    expect(await screen.findByText('Este evento ya está cerrado')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cerrar evento' })).not.toBeInTheDocument();
  });

  it('desde Barra se llega a los eventos para cerrar', async () => {
    const porCerrar: EventoPorCerrar[] = [{ evento: EVENTO, salon: SALON }];
    backendFalso(sesionDe(['BARRA']), (p) => {
      if (p.ruta === '/entregas/jornada') return json(200, []);
      if (p.ruta === '/cierres/pendientes') return json(200, porCerrar);
      return backend()(p);
    });
    const persona = userEvent.setup();
    const router = montar('/barra');

    const seccion = await screen.findByRole('region', { name: 'Eventos para cerrar' });
    expect(screen.queryByText('Hoy no hay eventos en tus barras')).not.toBeInTheDocument();
    await persona.click(within(seccion).getByRole('button', { name: 'Ir al cierre' }));

    expect(router.state.location.pathname).toBe('/barra/cierre/10');
    expect(await screen.findByRole('heading', { level: 1, name: 'Cierre de evento' })).toBeInTheDocument();
  });

  it('Cocina no entra al cierre', async () => {
    backendFalso(sesionDe(['COCINA']), backend());
    montar('/barra/cierre/10');

    expect(await screen.findByText('Tu perfil no tiene acceso a esta pantalla')).toBeInTheDocument();
  });
});
