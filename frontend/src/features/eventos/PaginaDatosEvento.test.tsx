import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import type { Ficha } from '../../api/eventos';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';
import { SIN_ACCIONES } from '../../test/fichas';

const FICHA: Ficha = {
  id: 5,
  codigo: 'EV-2026-00005',
  estado: 'PRE_RESERVA',
  nombre: 'Quince de Delfina Ríos',
  tipo: { id: 2, nombre: 'Quince' },
  salon: { id: 1, codigo: 'avril', nombre: 'Avril' },
  fecha: '2026-10-10',
  turno: { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00' },
  cliente: { id: 7, nombre: 'Delfina Ríos', documento: null, telefono: '351 555-1234', email: null },
  contactos: [
    { id: 3, nombre: 'Mariela Ríos', vinculo: 'madre', telefono: null, email: null },
    { id: 4, nombre: 'Jorge Ríos', vinculo: 'padre', telefono: null, email: null },
  ],
  vendedora: { id: 1, nombre: 'Lucía Ferreyra' },
  planner: null,
  cantidadInvitados: 150,
  invitadosDefinitivos: false,
  observacionesInternas: null,
  sena: null,
  fechaFirmaContrato: null,
  documentos: null,
  servicios: [],
  fechaCreacion: '2026-09-21T10:15:00-03:00',
  version: 2,
  acciones: { ...SIN_ACCIONES, modificar: true, liberar: true, registrarSena: true },
  historial: [
    { tipo: 'ESTADO', estadoNuevo: 'PRE_RESERVA', usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-21T10:15:00-03:00' },
    { tipo: 'MODIFICACION', campo: 'cantidad_invitados', valorNuevo: '150', usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-22T11:00:00-03:00' },
    { tipo: 'MODIFICACION', campo: 'contacto', valorNuevo: 'Mariela Ríos (madre)', usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-09-22T11:00:00-03:00' },
  ],
};

function backend(ficha: Ficha = FICHA, alGuardar: (p: Pedido) => Response = (p) => json(200, { ...ficha, ...(p.cuerpo as object), version: 3 })) {
  return backendFalso(sesionDe(['VENDEDORA']), (p) => {
    if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, ficha);
    if (p.metodo === 'PUT' && p.ruta === '/eventos/5') return alGuardar(p);
    if (p.ruta === '/tipos-evento') {
      return json(200, [
        { id: 1, nombre: 'Casamiento', usaSegmentos: false, activo: true },
        { id: 2, nombre: 'Quince', usaSegmentos: false, activo: true },
      ]);
    }
    return undefined;
  });
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('Registrar evento', () => {
  it('completa los datos desde la ficha y vuelve con el aviso', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    const router = montar('/eventos/5');

    await usuario.click(await screen.findByRole('button', { name: 'Modificar datos' }));
    expect(router.state.location.pathname).toBe('/eventos/5/datos');

    const nombre = await screen.findByLabelText('Nombre del evento');
    await usuario.clear(nombre);
    await usuario.type(nombre, 'Los 15 de Delfi');
    await usuario.click(screen.getByRole('button', { name: 'Sumar 10' }));
    await usuario.type(screen.getByLabelText(/Observaciones internas/), 'Llamar de tarde.');
    await usuario.click(screen.getByRole('button', { name: 'Quitar contacto Jorge Ríos' }));
    await usuario.click(screen.getByRole('button', { name: 'Agregar contacto' }));
    const nuevo = screen.getAllByRole('listitem').filter((li) => li.classList.contains('datos-evento__contacto')).at(-1) as HTMLElement;
    await usuario.type(within(nuevo).getByLabelText('Nombre'), 'Carla Paz');
    await usuario.type(within(nuevo).getByLabelText(/Vínculo/), 'organizadora');
    await usuario.click(screen.getByRole('button', { name: 'Guardar datos del evento' }));

    expect(await screen.findByText('Datos del evento guardados.')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/eventos/5');
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toEqual({
      version: 2,
      nombre: 'Los 15 de Delfi',
      tipoEventoId: 2,
      cantidadInvitados: 160,
      observacionesInternas: 'Llamar de tarde.',
      cliente: { nombre: 'Delfina Ríos', documento: '', telefono: '351 555-1234', email: '' },
      contactos: [
        { id: 3, nombre: 'Mariela Ríos', vinculo: 'madre', telefono: '', email: '' },
        { nombre: 'Carla Paz', vinculo: 'organizadora', telefono: '', email: '' },
      ],
    });
  });

  it('si otra persona guardó en el medio, avisa y no pisa', async () => {
    backend(FICHA, () => problema(409, 'EVENTO_MODIFICADO', 'Otra persona modificó el evento mientras lo editabas. Volvé a abrir la ficha para ver los cambios.'));
    const usuario = userEvent.setup();
    const router = montar('/eventos/5/datos');

    await usuario.click(await screen.findByRole('button', { name: 'Guardar datos del evento' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Otra persona modificó el evento');
    expect(router.state.location.pathname).toBe('/eventos/5/datos');
  });

  it('sin permiso, la ficha no ofrece modificar y el formulario lo explica', async () => {
    backend({ ...FICHA, acciones: { ...SIN_ACCIONES, modificar: false, liberar: false, registrarSena: false } });
    montar('/eventos/5/datos');

    expect(await screen.findByText('No podés modificar este evento')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar datos del evento' })).not.toBeInTheDocument();
  });

  it('el historial junta los cambios de un mismo guardado', async () => {
    backend();
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=historial');

    const entrada = (await screen.findByText('Datos modificados')).closest('li') as HTMLElement;
    expect(within(entrada).getByText('Invitados: sin dato → 150. Agregó el contacto Mariela Ríos (madre).')).toBeInTheDocument();
    expect(within(entrada).getByText('modificó los datos · mar 22 sep 11:00')).toBeInTheDocument();
    await usuario.click(screen.getByRole('tab', { name: 'Datos' }));
    expect(screen.getByText('Mariela Ríos')).toBeInTheDocument();
  });
});
