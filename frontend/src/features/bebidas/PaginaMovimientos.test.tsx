import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import type { OperacionDeStock, PaginaDeMovimientos } from '../../api/movimientos';
import { backendFalso, json, montar, pedidos, sesionDe } from '../../test/backendFalso';

const DEPOSITO = { id: 1, nombre: 'Depósito principal', tipo: 'DEPOSITO' as const };
const BARRA = { id: 2, nombre: 'Barra Avril', tipo: 'BARRA' as const };
const FERNET = { bebidaId: 10, nombre: 'Fernet Branca', presentacion: '750 ml', unidad: 'Caja', unidadesPorBulto: 6, corrige: null };

const ENTREGA: OperacionDeStock = {
  tipo: 'SALIDA_BARRA', fechaHora: '2026-09-26T21:10:00-03:00', usuario: { id: 4, nombre: 'Rocío Paz' },
  evento: { id: 5, codigo: 'EV-2026-00005', nombre: 'Bruno y Martina', fecha: '2026-09-26', salon: 'avril' },
  origen: DEPOSITO, destino: BARRA, motivo: null, observacion: null,
  asientos: [{ ...FERNET, movimientoId: 31, cantidad: 36 }],
};
const AJUSTE: OperacionDeStock = {
  tipo: 'AJUSTE', fechaHora: '2026-09-25T10:00:00-03:00', usuario: { id: 3, nombre: 'Tomás Caballero' }, evento: null,
  origen: DEPOSITO, destino: null, motivo: 'Recuento físico', observacion: 'Faltaban dos', asientos: [{ ...FERNET, movimientoId: 20, cantidad: 2, corrige: 7 }],
};
const INGRESO: OperacionDeStock = {
  tipo: 'INGRESO', fechaHora: '2026-09-24T10:00:00-03:00', usuario: { id: 3, nombre: 'Tomás Caballero' }, evento: null,
  origen: null, destino: DEPOSITO, motivo: null, observacion: null, asientos: [{ ...FERNET, movimientoId: 5, cantidad: 147 }],
};

function backend(primera: PaginaDeMovimientos, segunda: PaginaDeMovimientos = { operaciones: [], hayMas: false }) {
  return backendFalso(sesionDe(['COMPRAS']), (p) => {
    if (p.ruta === '/ubicaciones') return json(200, [DEPOSITO, BARRA]);
    if (p.ruta === '/bebidas') return json(200, []);
    if (p.ruta === '/movimientos/eventos') return json(200, [ENTREGA.evento]);
    if (p.ruta.startsWith('/movimientos?')) return json(200, p.ruta.includes('pagina=1') ? segunda : primera);
    return undefined;
  });
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-37 · movimientos de stock', () => {
  it('muestra cada operación con su ruta, sus bebidas en cajas y quién la registró', async () => {
    backend({ operaciones: [ENTREGA, AJUSTE], hayMas: false });
    montar('/movimientos');

    const lista = await screen.findByLabelText('Movimientos');
    expect(within(lista).getByText('Entrega a barra')).toBeInTheDocument();
    expect(within(lista).getByText('Bruno y Martina · EV-2026-00005')).toBeInTheDocument();
    expect(within(lista).getByText('6 cajas')).toBeInTheDocument();
    expect(within(lista).getByText('Rocío Paz')).toBeInTheDocument();
    expect(within(lista).getByText('Ajuste por recuento')).toBeInTheDocument();
    expect(within(lista).getByText('Motivo: Recuento físico · Faltaban dos · Corrige el movimiento 7')).toBeInTheDocument();
    expect(within(lista).getByText(/Sale del stock/)).toBeInTheDocument();
  });

  it('filtra y pide la página siguiente', async () => {
    const fetchMock = backend({ operaciones: [ENTREGA], hayMas: true }, { operaciones: [INGRESO], hayMas: false });
    const usuario = userEvent.setup();
    montar('/movimientos');

    await screen.findByLabelText('Movimientos');
    await usuario.selectOptions(screen.getByLabelText('Ubicación'), '1');
    await usuario.selectOptions(screen.getByLabelText('Tipo'), 'SALIDA_BARRA');
    await usuario.click(screen.getByRole('button', { name: 'Consultar movimientos' }));
    await usuario.click(await screen.findByRole('button', { name: 'Ver más movimientos' }));

    expect(await within(screen.getByLabelText('Movimientos')).findByText('Ingreso al depósito')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Ver más movimientos' })).not.toBeInTheDocument();
    const rutas = pedidos(fetchMock).map((p) => p.ruta).filter((r) => r.startsWith('/movimientos?'));
    expect(rutas).toContain('/movimientos?ubicacionId=1&tipo=SALIDA_BARRA&pagina=0');
    expect(rutas).toContain('/movimientos?ubicacionId=1&tipo=SALIDA_BARRA&pagina=1');
  }, 20000);

  it('sin movimientos lo dice', async () => {
    backend({ operaciones: [], hayMas: false });
    montar('/movimientos');

    expect(await screen.findByText('No hay movimientos con estos filtros.')).toBeInTheDocument();
  });

  it('la barra no tiene la pantalla en el menú', async () => {
    backendFalso(sesionDe(['BARRA']), () => json(200, []));
    montar('/movimientos');

    expect(screen.queryByRole('heading', { name: 'Movimientos de stock' })).not.toBeInTheDocument();
  });
});
