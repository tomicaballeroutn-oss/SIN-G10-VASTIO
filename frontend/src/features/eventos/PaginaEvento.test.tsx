import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import type { EventoResumen, Ficha } from '../../api/eventos';
import { backendFalso, json, montar, problema, sesionDe } from '../../test/backendFalso';
import { fechaCorta, fechaHora, pesos } from '../comun/formato';
import { SIN_ACCIONES } from '../../test/fichas';

const FICHA: Ficha = {
  id: 5,
  codigo: 'EV-2026-00005',
  estado: 'SENADO',
  nombre: 'Quince de Delfina Ríos',
  tipo: { id: 2, nombre: 'Quince' },
  salon: { id: 2, codigo: 'club', nombre: 'Club de Campo', capacidad: null },
  fecha: '2026-09-26',
  turno: { id: 1, codigo: 'mediodia', nombre: 'Mediodía', horaInicio: '12:00:00', horaFin: '18:00:00' },
  cliente: { id: 7, nombre: 'Delfina Ríos', documento: '40123456', telefono: '351 555-1234', email: null },
  contactos: [{ id: 3, nombre: 'Mariela Ríos', vinculo: 'madre', telefono: '351 444-0000', email: null }],
  vendedora: { id: 1, nombre: 'Lucía Ferreyra' },
  planner: { id: 3, nombre: 'Ana Sosa' },
  cantidadInvitados: 180,
  invitadosDefinitivos: false,
  observacionesInternas: 'La madre prefiere que la llamen después de las 18.',
  sena: { importe: 150000, fecha: '2026-09-23', firmanteNombre: 'Mariela Ríos', firmanteDni: '22333444', firmanteContacto: null },
  fechaFirmaContrato: null,
  documentos: null,
  servicios: [],
  requisitosConfirmacion: null,
  fechaCreacion: '2026-09-21T10:15:00-03:00',
  version: 2,
  acciones: { ...SIN_ACCIONES, modificar: true, liberar: false, registrarSena: false },
  historial: [
    { tipo: 'ESTADO', estadoNuevo: 'PRE_RESERVA', usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-21T10:15:00-03:00' },
    { tipo: 'ESTADO', estadoAnterior: 'PRE_RESERVA', estadoNuevo: 'SENADO', usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-23T16:40:00-03:00' },
  ],
};

function backend(roles: Rol[], ficha: Ficha | null = FICHA, lista: EventoResumen[] = []) {
  return backendFalso(sesionDe(roles), ({ ruta }) => {
    if (ruta === '/eventos/5') {
      return ficha ? json(200, ficha) : problema(403, 'EVENTO_DE_OTRA_VENDEDORA', 'Ese evento es de otra vendedora: solo podés ver quién tiene la fecha en la agenda.');
    }
    if (ruta === '/eventos') return json(200, lista);
    return undefined;
  });
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-09 · ficha del evento', () => {
  it('muestra estado, salón, fecha y los datos del evento', async () => {
    backend(['VENDEDORA']);
    montar('/eventos/5');

    expect(await screen.findByRole('heading', { level: 1, name: 'Quince de Delfina Ríos' })).toBeInTheDocument();
    expect(screen.getByText('Señado')).toBeInTheDocument();
    expect(screen.getByText('sáb 26 sep 2026 · Mediodía (12:00 a 18:00)')).toBeInTheDocument();
    expect(screen.getByText('EV-2026-00005 · Quince · Planner: Ana Sosa')).toBeInTheDocument();

    const cliente = screen.getByRole('heading', { name: 'Cliente' }).closest('section') as HTMLElement;
    expect(within(cliente).getByText('40123456')).toBeInTheDocument();
    expect(within(cliente).getByText('—')).toBeInTheDocument();
    expect(screen.getByText('180 (previstos)')).toBeInTheDocument();
    const sena = screen.getByRole('heading', { name: 'Seña' }).closest('section') as HTMLElement;
    expect(within(sena).getByText('$ 150.000,00')).toBeInTheDocument();
    expect(within(sena).getByText('Mariela Ríos · DNI 22333444')).toBeInTheDocument();
    expect(screen.getByText('La madre prefiere que la llamen después de las 18.')).toBeInTheDocument();
  });

  it('sin permiso para datos económicos, la seña se muestra sin importe', async () => {
    backend(['PLANNER'], { ...FICHA, sena: { ...FICHA.sena!, importe: undefined } });
    montar('/eventos/5');

    const sena = (await screen.findByRole('heading', { name: 'Seña' })).closest('section') as HTMLElement;
    expect(within(sena).queryByText('Importe')).not.toBeInTheDocument();
    expect(within(sena).getByText('mié 23 sep 2026')).toBeInTheDocument();
  });

  it('el historial dice quién hizo cada cambio y cuándo', async () => {
    backend(['VENDEDORA']);
    const usuario = userEvent.setup();
    const router = montar('/eventos/5');

    await usuario.click(await screen.findByRole('tab', { name: /Historial/ }));

    expect(router.state.location.search).toBe('?pestana=historial');
    const items = screen.getAllByRole('listitem').filter((li) => li.classList.contains('v-tl__item'));
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByText('Pre-reserva registrada')).toBeInTheDocument();
    expect(within(items[0]).getByText('apartó la fecha · lun 21 sep 10:15')).toBeInTheDocument();
    expect(within(items[1]).getByText('Seña registrada')).toBeInTheDocument();
    expect(within(items[1]).getByText('Lucía Ferreyra')).toBeInTheDocument();
  });

  it('un evento de otra vendedora muestra el motivo', async () => {
    backend(['VENDEDORA'], null);
    montar('/eventos/5');

    expect(await screen.findByRole('alert')).toHaveTextContent('Ese evento es de otra vendedora');
  });
});

describe('lista de eventos', () => {
  const RESUMEN: EventoResumen = {
    id: 5, codigo: 'EV-2026-00005', estado: 'PRE_RESERVA', nombre: 'Quince de Delfina Ríos', tipo: 'Quince',
    salon: FICHA.salon, fecha: '2026-10-10', turno: FICHA.turno, cliente: 'Delfina Ríos',
    vendedora: FICHA.vendedora, planner: null, cantidadInvitados: null,
  };

  it('para la vendedora es «Mis eventos» y cada tarjeta abre la ficha', async () => {
    backend(['VENDEDORA'], FICHA, [RESUMEN]);
    const usuario = userEvent.setup();
    const router = montar('/eventos');

    expect(await screen.findByRole('heading', { level: 1, name: 'Mis eventos' })).toBeInTheDocument();
    expect(screen.getByText('sáb 10 oct 2026')).toBeInTheDocument();
    expect(screen.getByText('Sin planner')).toBeInTheDocument();
    await usuario.click(screen.getByRole('heading', { name: 'Quince de Delfina Ríos' }));

    expect(router.state.location.pathname).toBe('/eventos/5');
  });

  it('sin eventos próximos, invita a ir a la agenda', async () => {
    backend(['ADMINISTRACION']);
    montar('/eventos');

    expect(await screen.findByText('No hay eventos próximos')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ir a la agenda' })).toBeInTheDocument();
  });
});

describe('formatos', () => {
  it('fechas en castellano y en hora de Córdoba', () => {
    expect(fechaCorta('2026-09-26')).toBe('sáb 26 sep');
    expect(fechaHora('2026-09-22T01:15:00Z')).toBe('lun 21 sep 22:15');
  });

  it('importes en pesos', () => {
    expect(pesos(150000)).toBe('$ 150.000,00');
    expect(pesos(1234.5)).toBe('$ 1.234,50');
  });
});
