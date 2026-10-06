import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { hoyEnCordoba } from '../../api/agenda';
import { _reiniciarCliente } from '../../api/cliente';
import type { Ficha } from '../../api/eventos';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';
import { leerImporte } from '../comun/formato';
import { SIN_ACCIONES } from '../../test/fichas';

const PRE_RESERVA: Ficha = {
  id: 5,
  codigo: 'EV-2026-00005',
  estado: 'PRE_RESERVA',
  nombre: 'Casamiento de Camila Ruiz',
  tipo: { id: 1, nombre: 'Casamiento' },
  salon: { id: 3, codigo: 'santa-barbara', nombre: 'Santa Bárbara', capacidad: null },
  fecha: '2026-11-14',
  turno: { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00' },
  cliente: { id: 7, nombre: 'Camila Ruiz', documento: null, telefono: null, email: null },
  contactos: [],
  vendedora: { id: 1, nombre: 'Lucía Ferreyra' },
  planner: null,
  cantidadInvitados: null,
  invitadosDefinitivos: false,
  observacionesInternas: null,
  sena: null,
  fechaFirmaContrato: null,
  documentos: null,
  servicios: [],
  requisitosConfirmacion: null,
  fechaCreacion: '2026-09-21T10:15:00-03:00',
  version: 0,
  acciones: { ...SIN_ACCIONES, modificar: true, liberar: true, registrarSena: true },
  historial: [{ tipo: 'ESTADO', estadoNuevo: 'PRE_RESERVA', usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-21T10:15:00-03:00' }],
};

const SENADO: Ficha = {
  ...PRE_RESERVA,
  estado: 'SENADO',
  version: 1,
  sena: { importe: 150000.5, fecha: '2026-09-29', firmanteNombre: 'Mariela Ruiz', firmanteDni: '30111222', firmanteContacto: null },
  acciones: { ...SIN_ACCIONES, modificar: true, liberar: false, registrarSena: false },
  historial: [
    ...PRE_RESERVA.historial,
    { tipo: 'ESTADO', estadoAnterior: 'PRE_RESERVA', estadoNuevo: 'SENADO', usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-29T16:40:00-03:00' },
  ],
};

function backend(alRegistrar: (p: Pedido) => Response = () => json(200, SENADO)) {
  return backendFalso(sesionDe(['VENDEDORA']), (p) => {
    if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, PRE_RESERVA);
    if (p.metodo === 'POST' && p.ruta === '/eventos/5/sena') return alRegistrar(p);
    return undefined;
  });
}

async function abrir() {
  const usuario = userEvent.setup();
  montar('/eventos/5');
  await usuario.click(await screen.findByRole('button', { name: 'Registrar seña' }));
  return { usuario, dialogo: screen.getByRole('dialog', { name: 'Registrar seña' }) };
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-11 · registrar seña', () => {
  it('registra la seña y la ficha pasa a Señado', async () => {
    const fetchMock = backend();
    const { usuario, dialogo } = await abrir();

    expect(within(dialogo).getByLabelText('Fecha del pago')).toHaveValue(hoyEnCordoba());
    await usuario.type(within(dialogo).getByLabelText('Importe de la seña'), '150.000,50');
    await usuario.type(within(dialogo).getByLabelText(/Nombre y apellido/), 'Mariela Ruiz');
    await usuario.type(within(dialogo).getByLabelText('DNI'), '30.111.222');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Confirmar seña' }));

    expect(await screen.findByText('Seña registrada. El evento pasó a Señado.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByText('Señado')).toBeInTheDocument();
    expect(screen.getByText('$ 150.000,50')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Registrar seña' })).not.toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toEqual({
      importe: 150000.5,
      fechaPago: hoyEnCordoba(),
      firmanteNombre: 'Mariela Ruiz',
      firmanteDni: '30111222',
      firmanteContacto: '',
    });
  });

  it('un importe ilegible se marca sin llamar al sistema', async () => {
    const fetchMock = backend();
    const { usuario, dialogo } = await abrir();

    await usuario.type(within(dialogo).getByLabelText('Importe de la seña'), 'ciento cincuenta');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Confirmar seña' }));

    expect(within(dialogo).getByLabelText('Importe de la seña')).toHaveAccessibleDescription('Escribí el importe con números, p. ej. 150.000.');
    expect(pedidos(fetchMock).some((p) => p.metodo === 'POST')).toBe(false);
  });

  it('muestra en cada campo lo que rechaza el sistema', async () => {
    backend(() => problema(400, 'DATOS_INVALIDOS', 'Revisá los datos marcados.', [
      { campo: 'importe', mensaje: 'El importe tiene que ser mayor a 0.' },
      { campo: 'firmanteDni', mensaje: 'Escribí el DNI del firmante.' },
    ]));
    const { usuario, dialogo } = await abrir();

    await usuario.type(within(dialogo).getByLabelText('Importe de la seña'), '0');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Confirmar seña' }));

    expect(await within(dialogo).findByText('El importe tiene que ser mayor a 0.')).toBeInTheDocument();
    expect(within(dialogo).getByLabelText('DNI')).toHaveAccessibleDescription('Escribí el DNI del firmante.');
  });

  it('una fecha de pago futura se marca en el campo', async () => {
    backend(() => problema(422, 'FECHA_PAGO_FUTURA', 'La fecha del pago no puede ser posterior a hoy. Registrá la seña cuando esté paga.'));
    const { usuario, dialogo } = await abrir();

    await usuario.type(within(dialogo).getByLabelText('Importe de la seña'), '100000');
    await usuario.type(within(dialogo).getByLabelText('DNI'), '30111222');
    await usuario.click(within(dialogo).getByRole('button', { name: 'Confirmar seña' }));

    expect(await within(dialogo).findByText(/no puede ser posterior a hoy/)).toBeInTheDocument();
  });
});

describe('leerImporte', () => {
  it.each([
    ['150.000,50', 150000.5],
    ['150000,5', 150000.5],
    ['150000.50', 150000.5],
    ['150.000', 150000],
    ['$ 1.234.567', 1234567],
    ['1.5', 1.5],
  ])('«%s» → %d', (texto, esperado) => {
    expect(leerImporte(texto)).toBe(esperado);
  });

  it.each(['', 'ciento cincuenta', '1,2,3', '-100'])('«%s» no es un importe', (texto) => {
    expect(leerImporte(texto)).toBeNull();
  });
});
