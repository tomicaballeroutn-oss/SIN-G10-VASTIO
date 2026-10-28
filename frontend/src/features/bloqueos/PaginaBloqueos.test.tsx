import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Bloqueo, PedidoDeBloqueo } from '../../api/bloqueos';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';

const SALONES = [
  { id: 1, codigo: 'avril', nombre: 'Avril', capacidad: null, activo: true },
  { id: 2, codigo: 'club', nombre: 'Club de Campo', capacidad: null, activo: true },
  { id: 3, codigo: 'santa-barbara', nombre: 'Santa Bárbara', capacidad: null, activo: true },
];
const TURNOS = [
  { id: 1, codigo: 'mediodia', nombre: 'Mediodía', horaInicio: '12:00:00', horaFin: '18:00:00', cruzaMedianoche: false },
  { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00', cruzaMedianoche: true },
];
const MOTIVOS = [
  { id: 1, ambito: 'CANCELACION', nombre: 'Desistimiento del cliente', activo: true },
  { id: 7, ambito: 'BLOQUEO', nombre: 'Mantenimiento', activo: true },
  { id: 8, ambito: 'BLOQUEO', nombre: 'Feriado', activo: true },
  { id: 9, ambito: 'BLOQUEO', nombre: 'Evento propio', activo: false },
];

const PINTURA: Bloqueo = {
  id: 1,
  motivo: { id: 7, nombre: 'Mantenimiento' },
  detalle: 'Pintura',
  salones: [{ id: 2, nombre: 'Club de Campo' }],
  desde: '2033-04-04',
  hasta: '2033-04-08',
  turnos: [{ id: 1, nombre: 'Mediodía' }, { id: 2, nombre: 'Noche' }],
  cantidadUnidades: 10,
  registradoPorId: 4,
  registradoPor: 'Gabriela Soria',
  fechaRegistro: '2033-03-01T10:00:00-03:00',
};
const NAVIDAD: Bloqueo = {
  id: 2,
  motivo: { id: 8, nombre: 'Feriado' },
  detalle: null,
  salones: [{ id: 3, nombre: 'Santa Bárbara' }],
  desde: '2033-12-25',
  hasta: '2033-12-25',
  turnos: [{ id: 2, nombre: 'Noche' }],
  cantidadUnidades: 1,
  registradoPorId: 4,
  registradoPor: 'Gabriela Soria',
  fechaRegistro: '2033-03-01T10:00:00-03:00',
};

/** Backend falso con la lista de bloqueos en memoria: registrar suma uno, desbloquear lo saca. */
function backend(
  roles: Rol[] = ['ADMINISTRACION'],
  inicial: Bloqueo[] = [PINTURA, NAVIDAD],
  rechazos: { alRegistrar?: (p: Pedido) => Response | Promise<Response>; alDesbloquear?: (p: Pedido) => Response | Promise<Response> } = {},
) {
  let lista = [...inicial];
  return backendFalso(sesionDe(roles), (p) => {
    if (p.metodo === 'GET' && p.ruta === '/salones') return json(200, SALONES);
    if (p.metodo === 'GET' && p.ruta === '/turnos') return json(200, TURNOS);
    if (p.metodo === 'GET' && p.ruta === '/motivos') return json(200, MOTIVOS);
    if (p.metodo === 'GET' && p.ruta === '/bloqueos') return json(200, lista);
    if (p.metodo === 'POST' && p.ruta === '/bloqueos') {
      if (rechazos.alRegistrar) return rechazos.alRegistrar(p);
      const pedido = p.cuerpo as PedidoDeBloqueo;
      const nuevo: Bloqueo = {
        ...PINTURA,
        id: 3,
        detalle: pedido.detalle,
        salones: SALONES.filter((s) => pedido.salonIds.includes(s.id)).map((s) => ({ id: s.id, nombre: s.nombre })),
        desde: pedido.desde,
        hasta: pedido.hasta ?? pedido.desde,
      };
      lista = [...lista, nuevo];
      return json(201, nuevo);
    }
    const desbloqueo = /^\/bloqueos\/(\d+)\/desbloqueo$/.exec(p.ruta);
    if (p.metodo === 'POST' && desbloqueo) {
      if (rechazos.alDesbloquear) return rechazos.alDesbloquear(p);
      lista = lista.filter((b) => b.id !== Number(desbloqueo[1]));
      return new Response(null, { status: 204 });
    }
    return undefined;
  });
}

const fecha = (campo: string, valor: string) => fireEvent.change(screen.getByLabelText(campo), { target: { value: valor } });

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-21 · bloqueo de unidades', () => {
  it('lista los bloqueos activos con salón, rango, turnos y motivo', async () => {
    backend();
    montar('/bloqueos');

    const tabla = await screen.findByRole('table', { name: 'Bloqueos activos' });
    const filas = within(tabla).getAllByRole('row').slice(1);
    expect(filas).toHaveLength(2);
    expect(filas[0]).toHaveTextContent('Club de Campo');
    expect(filas[0]).toHaveTextContent('lun 4 abr 2033 al vie 8 abr 2033');
    expect(filas[0]).toHaveTextContent('Ambos');
    expect(filas[0]).toHaveTextContent('Mantenimiento · Pintura');
    expect(filas[0]).toHaveTextContent('Gabriela Soria');
    expect(filas[0]).toHaveTextContent('mar 1 mar 10:00');
    expect(filas[1]).toHaveTextContent('Santa Bárbara');
    expect(filas[1]).toHaveTextContent('dom 25 dic 2033');
    expect(filas[1]).toHaveTextContent('Noche');
    expect(filas[1]).toHaveTextContent('Feriado');
  });

  it('sin bloqueos lo dice', async () => {
    backend(['ADMINISTRACION'], []);
    montar('/bloqueos');

    expect(await screen.findByText('No hay bloqueos activos.')).toBeInTheDocument();
  });

  it('registra el bloqueo, avisa y actualiza la lista', async () => {
    const fetchMock = backend(['ADMINISTRACION'], [NAVIDAD]);
    const usuario = userEvent.setup();
    montar('/bloqueos');

    await usuario.click(await screen.findByRole('checkbox', { name: 'Club de Campo' }));
    fecha('Desde', '2033-04-04');
    fecha('Hasta (opcional)', '2033-04-08');
    await usuario.selectOptions(screen.getByLabelText('Turnos'), 'Ambos');
    // Solo los motivos de bloqueo activos.
    expect(within(screen.getByLabelText('Motivo')).getAllByRole('option').map((o) => o.textContent)).toEqual(['Elegí el motivo', 'Mantenimiento', 'Feriado']);
    await usuario.selectOptions(screen.getByLabelText('Motivo'), 'Mantenimiento');
    await usuario.type(screen.getByLabelText('Detalle (opcional)'), '  Pintura  ');
    await usuario.click(screen.getByRole('button', { name: 'Registrar bloqueo' }));

    expect(await screen.findByText(/Bloqueo registrado: Club de Campo, lun 4 abr 2033 al vie 8 abr 2033\./)).toBeInTheDocument();
    expect(pedidos(fetchMock).filter((p) => p.metodo === 'POST')).toEqual([
      {
        metodo: 'POST',
        ruta: '/bloqueos',
        cuerpo: { salonIds: [2], desde: '2033-04-04', hasta: '2033-04-08', turnoIds: [1, 2], motivoId: 7, detalle: 'Pintura' },
      },
    ]);
    const tabla = screen.getByRole('table', { name: 'Bloqueos activos' });
    await waitFor(() => expect(within(tabla).getAllByRole('row')).toHaveLength(3));
    // El formulario quedó vacío para el siguiente.
    expect(screen.getByRole('checkbox', { name: 'Club de Campo' })).not.toBeChecked();
    expect(screen.getByLabelText('Desde')).toHaveValue('');
  });

  it('un solo día y un solo turno: manda hasta vacío y ese turno', async () => {
    const fetchMock = backend(['COORDINACION'], []);
    const usuario = userEvent.setup();
    montar('/bloqueos');

    await usuario.click(await screen.findByRole('checkbox', { name: 'Avril' }));
    await usuario.click(screen.getByRole('checkbox', { name: 'Santa Bárbara' }));
    fecha('Desde', '2033-12-25');
    await usuario.selectOptions(screen.getByLabelText('Turnos'), 'Noche');
    await usuario.selectOptions(screen.getByLabelText('Motivo'), 'Feriado');
    await usuario.click(screen.getByRole('button', { name: 'Registrar bloqueo' }));

    await screen.findByText(/Bloqueo registrado/);
    expect(pedidos(fetchMock).find((p) => p.metodo === 'POST')?.cuerpo).toEqual({
      salonIds: [1, 3], desde: '2033-12-25', hasta: null, turnoIds: [2], motivoId: 8, detalle: null,
    });
  });

  it('valida antes de enviar: nada elegido y un rango al revés', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    montar('/bloqueos');
    await screen.findByRole('checkbox', { name: 'Avril' });

    await usuario.click(screen.getByRole('button', { name: 'Registrar bloqueo' }));
    expect(screen.getByText('Elegí al menos un salón.')).toBeInTheDocument();
    expect(screen.getByText('Elegí la fecha desde la que se bloquea.')).toBeInTheDocument();
    expect(screen.getByText('Elegí el turno.')).toBeInTheDocument();
    expect(screen.getByText('Elegí el motivo.')).toBeInTheDocument();

    fecha('Desde', '2033-04-08');
    fecha('Hasta (opcional)', '2033-04-04');
    await usuario.click(screen.getByRole('button', { name: 'Registrar bloqueo' }));
    expect(screen.getByText('La fecha final no puede ser anterior a la inicial. Revisá el rango.')).toBeInTheDocument();
    expect(pedidos(fetchMock).some((p) => p.metodo === 'POST')).toBe(false);
  });

  it('si el servidor rechaza por un evento, muestra el mensaje y conserva lo cargado', async () => {
    backend(['ADMINISTRACION'], [], { alRegistrar: () =>
      problema(409, 'UNIDAD_OCUPADA', 'Club de Campo el 05/04/2033 (turno noche) ya tiene el evento EV-2033-00012 (Casamiento Gómez-Paz). Liberalo, cancelalo o reprogramalo antes de bloquear esa fecha, o excluila del rango.') },
    );
    const usuario = userEvent.setup();
    montar('/bloqueos');

    await usuario.click(await screen.findByRole('checkbox', { name: 'Club de Campo' }));
    fecha('Desde', '2033-04-04');
    await usuario.selectOptions(screen.getByLabelText('Turnos'), 'Noche');
    await usuario.selectOptions(screen.getByLabelText('Motivo'), 'Mantenimiento');
    await usuario.click(screen.getByRole('button', { name: 'Registrar bloqueo' }));

    const alerta = await screen.findByText(/ya tiene el evento EV-2033-00012/);
    expect(alerta).toBeInTheDocument();
    expect(screen.queryByText(/Bloqueo registrado/)).not.toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Club de Campo' })).toBeChecked();
    expect(screen.getByLabelText('Desde')).toHaveValue('2033-04-04');
  });

  it('un error de campo del servidor queda en ese campo', async () => {
    backend(['ADMINISTRACION'], [], { alRegistrar: () =>
      problema(400, 'DATOS_INVALIDOS', 'Revisá los datos marcados.', [{ campo: 'detalle', mensaje: 'Usá hasta 255 caracteres.' }]) },
    );
    const usuario = userEvent.setup();
    montar('/bloqueos');

    await usuario.click(await screen.findByRole('checkbox', { name: 'Avril' }));
    fecha('Desde', '2033-04-04');
    await usuario.selectOptions(screen.getByLabelText('Turnos'), 'Noche');
    await usuario.selectOptions(screen.getByLabelText('Motivo'), 'Feriado');
    await usuario.click(screen.getByRole('button', { name: 'Registrar bloqueo' }));

    expect(await screen.findByText('Usá hasta 255 caracteres.')).toBeInTheDocument();
  });

  it('desbloquear pide confirmación, desbloquea y saca el bloqueo de la lista', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    montar('/bloqueos');

    const tabla = await screen.findByRole('table', { name: 'Bloqueos activos' });
    await usuario.click(within(within(tabla).getAllByRole('row')[1]).getByRole('button', { name: 'Desbloquear' }));
    const dialogo = screen.getByRole('dialog', { name: 'Desbloquear unidades' });
    expect(dialogo).toHaveTextContent('Las fechas vuelven a estar disponibles en la agenda para pre-reservar.');
    expect(dialogo).toHaveTextContent('Club de Campo');
    expect(pedidos(fetchMock).some((p) => p.metodo === 'POST')).toBe(false);
    await usuario.click(within(dialogo).getByRole('button', { name: 'Desbloquear' }));

    expect(await screen.findByText(/Se desbloqueó Club de Campo/)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(pedidos(fetchMock).filter((p) => p.metodo === 'POST')).toEqual([{ metodo: 'POST', ruta: '/bloqueos/1/desbloqueo', cuerpo: undefined }]);
    await waitFor(() => expect(within(screen.getByRole('table', { name: 'Bloqueos activos' })).getAllByRole('row')).toHaveLength(2));
    expect(screen.queryByText('Mantenimiento · Pintura')).not.toBeInTheDocument();
  });

  it('cancelar no desbloquea', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    montar('/bloqueos');

    const tabla = await screen.findByRole('table', { name: 'Bloqueos activos' });
    await usuario.click(within(within(tabla).getAllByRole('row')[1]).getByRole('button', { name: 'Desbloquear' }));
    await usuario.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(pedidos(fetchMock).some((p) => p.metodo === 'POST')).toBe(false);
  });

  it('si otra persona ya lo desbloqueó, el diálogo lo explica', async () => {
    backend(['ADMINISTRACION'], undefined, {
      alDesbloquear: () => problema(409, 'BLOQUEO_YA_DESBLOQUEADO', 'Ese bloqueo ya estaba desbloqueado. Actualizá la lista para ver cómo está ahora.'),
    });
    const usuario = userEvent.setup();
    montar('/bloqueos');

    const tabla = await screen.findByRole('table', { name: 'Bloqueos activos' });
    await usuario.click(within(within(tabla).getAllByRole('row')[1]).getByRole('button', { name: 'Desbloquear' }));
    await usuario.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Desbloquear' }));

    expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent('Ese bloqueo ya estaba desbloqueado');
  });

  it.each<Rol>(['VENDEDORA', 'PLANNER', 'COMPRAS', 'BARRA', 'COCINA'])('el perfil %s no ve la pantalla', async (rol) => {
    backend([rol]);
    montar('/bloqueos');

    expect(await screen.findByText('Tu perfil no tiene acceso a esta pantalla')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Registrar bloqueo' })).not.toBeInTheDocument();
  });
});

describe('UI-21 · envío en curso', () => {
  it('mantiene el registro ocupado y no duplica el pedido al volver a pulsar', async () => {
    let responder!: (respuesta: Response) => void;
    const pendiente = new Promise<Response>((resolve) => { responder = resolve; });
    const fetchMock = backend(['ADMINISTRACION'], [], { alRegistrar: () => pendiente });
    montar('/bloqueos');
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Avril' }));
    fecha('Desde', '2033-04-04');
    await userEvent.selectOptions(screen.getByLabelText('Turnos'), 'Noche');
    await userEvent.selectOptions(screen.getByLabelText('Motivo'), 'Mantenimiento');
    const boton = screen.getByRole('button', { name: 'Registrar bloqueo' });
    await userEvent.click(boton);
    expect(boton).toBeDisabled();
    expect(boton).toHaveAttribute('aria-busy', 'true');
    await userEvent.click(boton);
    expect(pedidos(fetchMock).filter((p) => p.metodo === 'POST')).toHaveLength(1);
    await act(async () => responder(problema(503, 'NO_DISPONIBLE', 'Volvé a intentarlo.')));
    expect(await screen.findByText('Volvé a intentarlo.')).toBeInTheDocument();
    expect(boton).toBeEnabled();
    expect(screen.getByLabelText('Desde')).toHaveValue('2033-04-04');
  });

  it('no duplica un desbloqueo pendiente y conserva el diálogo al fallar', async () => {
    let responder!: (respuesta: Response) => void;
    const pendiente = new Promise<Response>((resolve) => { responder = resolve; });
    const fetchMock = backend(['ADMINISTRACION'], [PINTURA], { alDesbloquear: () => pendiente });
    montar('/bloqueos');
    const tabla = await screen.findByRole('table', { name: 'Bloqueos activos' });
    await userEvent.click(within(tabla).getByRole('button', { name: 'Desbloquear' }));
    const boton = within(screen.getByRole('dialog')).getByRole('button', { name: 'Desbloquear' });
    await userEvent.click(boton);
    expect(boton).toBeDisabled();
    await userEvent.click(boton);
    expect(pedidos(fetchMock).filter((p) => p.metodo === 'POST')).toHaveLength(1);
    await act(async () => responder(problema(409, 'BLOQUEO_YA_DESBLOQUEADO', 'Ese bloqueo ya estaba desbloqueado.')));
    expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent('ya estaba desbloqueado');
    expect(boton).toBeEnabled();
  });
});
