import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Agenda } from '../../api/agenda';
import type { Rol } from '../../api/cliente';
import { _reiniciarCliente } from '../../api/cliente';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';

const MARZO: Agenda = {
  mes: '2030-03',
  salones: [
    { id: 1, codigo: 'avril', nombre: 'Avril', activo: true },
    { id: 2, codigo: 'club', nombre: 'Club de Campo', activo: true },
    { id: 3, codigo: 'santa-barbara', nombre: 'Santa Bárbara', activo: true },
  ],
  turnos: [
    { id: 1, codigo: 'mediodia', nombre: 'Mediodía', horaInicio: '12:00:00', horaFin: '18:00:00', cruzaMedianoche: false },
    { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00', cruzaMedianoche: true },
  ],
  unidades: [
    { fecha: '2030-03-09', salonId: 2, turnoId: 1, estado: 'SENADO', evento: { vendedora: { id: 8, nombre: 'Sofía Méndez' }, detalle: false } },
    { fecha: '2030-03-09', salonId: 3, turnoId: 1, estado: 'BLOQUEADO', bloqueo: { motivo: 'Mantenimiento', detalle: null } },
    { fecha: '2030-03-09', salonId: 1, turnoId: 2, estado: 'PRE_RESERVA', evento: { vendedora: { id: 8, nombre: 'Sofía Méndez' }, detalle: false } },
    { fecha: '2030-03-09', salonId: 2, turnoId: 2, estado: 'PRE_RESERVA', evento: { vendedora: { id: 8, nombre: 'Sofía Méndez' }, detalle: false } },
    { fecha: '2030-03-09', salonId: 3, turnoId: 2, estado: 'PRE_RESERVA', evento: { vendedora: { id: 8, nombre: 'Sofía Méndez' }, detalle: false } },
  ],
};

/** El 9 de marzo solo queda libre Avril al mediodía. */
function backend(roles: Rol[], respuestaAlta: (p: Pedido) => Response = () =>
  json(201, { id: 40, codigo: 'EV-2026-00040', nombre: 'Quince de Delfina Ríos', estado: 'PRE_RESERVA' })) {
  return backendFalso(sesionDe(roles, 'Lucía Ferreyra', 1), (p) => {
    if (p.ruta === '/agenda?mes=2030-03') return json(200, MARZO);
    if (p.ruta === '/tipos-evento') {
      return json(200, [
        { id: 1, nombre: 'Casamiento', usaSegmentos: false, activo: true },
        { id: 2, nombre: 'Quince', usaSegmentos: false, activo: true },
        { id: 5, nombre: 'Cumpleaños', usaSegmentos: false, activo: false },
      ]);
    }
    if (p.ruta.startsWith('/clientes?buscar=')) {
      return json(200, [{ id: 7, nombre: 'Delfina Ríos', documento: '40123456', telefono: '351 555-1234', email: null }]);
    }
    if (p.ruta === '/usuarios/personas?perfil=VENDEDORA') {
      return json(200, [{ id: 1, nombreCompleto: 'Lucía Ferreyra' }, { id: 8, nombreCompleto: 'Sofía Méndez' }]);
    }
    if (p.metodo === 'POST' && p.ruta === '/eventos') return respuestaAlta(p);
    return undefined;
  });
}

async function abrirPreReserva(ruta = '/agenda?mes=2030-03&dia=2030-03-09') {
  const usuario = userEvent.setup();
  montar(ruta);
  const boton = await screen.findByRole('button', { name: 'Registrar pre-reserva' });
  await usuario.click(boton);
  return { usuario, dialogo: screen.getByRole('dialog', { name: 'Registrar pre-reserva' }) };
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-08 · registrar pre-reserva', () => {
  it('la vendedora elige un cliente existente y el nombre del evento se arma solo', async () => {
    const fetchMock = backend(['VENDEDORA']);
    const { usuario, dialogo } = await abrirPreReserva();

    expect(within(dialogo).getByText('sábado 9 de marzo de 2030')).toBeInTheDocument();
    expect(within(dialogo).queryByLabelText('Vendedora interviniente')).not.toBeInTheDocument();
    await usuario.type(within(dialogo).getByRole('combobox', { name: 'Cliente' }), 'Delf');
    await usuario.click(await within(dialogo).findByRole('option', { name: /Delfina Ríos/ }));
    expect(within(dialogo).getByLabelText(/Teléfono/)).toHaveValue('351 555-1234');
    // Los tipos dados de baja no se ofrecen.
    expect(within(dialogo).queryByRole('option', { name: 'Cumpleaños' })).not.toBeInTheDocument();
    await usuario.selectOptions(within(dialogo).getByLabelText('Tipo de evento'), '2');
    expect(within(dialogo).getByLabelText('Nombre del evento')).toHaveValue('Quince de Delfina Ríos');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Registrar pre-reserva' }));

    expect(await screen.findByText(/EV-2026-00040 · Quince de Delfina Ríos\./)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(pedidos(fetchMock)).toContainEqual({
      metodo: 'POST',
      ruta: '/eventos',
      cuerpo: {
        fecha: '2030-03-09', salonId: 1, turnoId: 1, tipoEventoId: 2, nombre: 'Quince de Delfina Ríos',
        cliente: { id: 7, telefono: '351 555-1234', email: '' },
      },
    });
    // Después de registrar, la agenda se vuelve a pedir.
    expect(pedidos(fetchMock).filter((p) => p.ruta === '/agenda?mes=2030-03').length).toBeGreaterThanOrEqual(2);
  });

  it('carga un cliente nuevo con documento y respeta el nombre que escribió la vendedora', async () => {
    const fetchMock = backend(['VENDEDORA']);
    const { usuario, dialogo } = await abrirPreReserva();

    await usuario.type(within(dialogo).getByRole('combobox', { name: 'Cliente' }), 'Paula Gómez');
    await usuario.click(await within(dialogo).findByRole('option', { name: 'Cargar «Paula Gómez» como cliente nuevo' }));
    await usuario.type(within(dialogo).getByLabelText(/Documento/), '30.111.222');
    await usuario.selectOptions(within(dialogo).getByLabelText('Tipo de evento'), '1');
    const nombre = within(dialogo).getByLabelText('Nombre del evento');
    expect(nombre).toHaveValue('Casamiento de Paula Gómez');
    await usuario.clear(nombre);
    await usuario.type(nombre, 'Boda Gómez-Paz');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Registrar pre-reserva' }));

    await screen.findByText('Pre-reserva registrada');
    const alta = pedidos(fetchMock).find((p) => p.metodo === 'POST');
    expect(alta?.cuerpo).toMatchObject({
      tipoEventoId: 1,
      nombre: 'Boda Gómez-Paz',
      cliente: { nombre: 'Paula Gómez', documento: '30111222', telefono: '', email: '' },
    });
  });

  it('si otra vendedora la tomó antes, avisa y vuelve a la agenda actualizada', async () => {
    const fetchMock = backend(['VENDEDORA'], () =>
      problema(409, 'FECHA_TOMADA', 'Esa fecha ya está tomada. Elegí otro salón, otra fecha u otro turno.'));
    const { usuario, dialogo } = await abrirPreReserva();

    await usuario.type(within(dialogo).getByRole('combobox', { name: 'Cliente' }), 'Paula');
    await usuario.click(await within(dialogo).findByRole('option', { name: /como cliente nuevo/ }));
    await usuario.selectOptions(within(dialogo).getByLabelText('Tipo de evento'), '2');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Registrar pre-reserva' }));

    expect(await within(dialogo).findByRole('alert')).toHaveTextContent('Esa fecha ya está tomada. Elegí otro salón, otra fecha u otro turno.');
    const pedidasAntes = pedidos(fetchMock).filter((p) => p.ruta === '/agenda?mes=2030-03').length;
    await usuario.click(within(dialogo).getByRole('button', { name: 'Volver a la agenda' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(pedidos(fetchMock).filter((p) => p.ruta === '/agenda?mes=2030-03').length).toBe(pedidasAntes + 1);
  });

  it('Coordinación elige la vendedora interviniente', async () => {
    const fetchMock = backend(['COORDINACION']);
    const { usuario, dialogo } = await abrirPreReserva();

    await usuario.type(within(dialogo).getByRole('combobox', { name: 'Cliente' }), 'Delf');
    await usuario.click(await within(dialogo).findByRole('option', { name: /Delfina Ríos/ }));
    await usuario.selectOptions(within(dialogo).getByLabelText('Tipo de evento'), '2');
    await usuario.selectOptions(await within(dialogo).findByLabelText('Vendedora interviniente'), '8');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Registrar pre-reserva' }));

    await screen.findByText('Pre-reserva registrada');
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toMatchObject({ vendedoraId: 8 });
  });

  it('la planner consulta la agenda pero no pre-reserva', async () => {
    backend(['PLANNER']);
    montar('/agenda?mes=2030-03&dia=2030-03-09');

    expect(await screen.findByText('Libre para ofrecer.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Registrar pre-reserva' })).not.toBeInTheDocument();
  });
});
