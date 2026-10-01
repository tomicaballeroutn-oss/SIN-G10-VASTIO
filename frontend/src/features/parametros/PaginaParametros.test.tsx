import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import type { Parametro, Salon, TipoEvento, Turno } from '../../api/catalogos';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';

const SALONES: Salon[] = [
  { id: 1, codigo: 'avril', nombre: 'Avril', capacidad: null, activo: true },
  { id: 2, codigo: 'club', nombre: 'Club de Campo', capacidad: 250, activo: true },
  { id: 3, codigo: 'santa-barbara', nombre: 'Santa Bárbara', capacidad: null, activo: true },
];

const TURNOS: Turno[] = [
  { id: 1, codigo: 'mediodia', nombre: 'Mediodía', horaInicio: '12:00:00', horaFin: '18:00:00', cruzaMedianoche: false },
  { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00', cruzaMedianoche: true },
];

const TIPOS: TipoEvento[] = [
  { id: 1, nombre: 'Casamiento', usaSegmentos: false, activo: true },
  { id: 5, nombre: 'Cumpleaños', usaSegmentos: false, activo: false },
];

const PARAMETROS: Parametro[] = [
  { clave: 'HORIZONTE_COCINA_DIAS', valor: '15', descripcion: null, minimo: 1, maximo: 90 },
  { clave: 'MINUTOS_AVISO_EXPIRACION', valor: '5', descripcion: null, minimo: 1, maximo: 60 },
  { clave: 'MINUTOS_EXPIRACION_SESION', valor: '60', descripcion: null, minimo: 5, maximo: 480 },
];

function catalogo({ metodo, ruta, cuerpo }: Pedido) {
  if (metodo === 'GET' && ruta === '/salones') return json(200, SALONES);
  if (metodo === 'GET' && ruta === '/turnos') return json(200, TURNOS);
  if (metodo === 'GET' && ruta === '/tipos-evento') return json(200, TIPOS);
  if (metodo === 'GET' && ruta === '/parametros') return json(200, PARAMETROS);
  if (metodo === 'PUT' && ruta === '/salones/1') return json(200, { ...SALONES[0], ...(cuerpo as object) });
  if (metodo === 'POST' && ruta === '/tipos-evento') return json(201, { id: 6, activo: true, ...(cuerpo as object) });
  if (metodo === 'PUT' && ruta.startsWith('/parametros/')) {
    const clave = ruta.split('/')[2];
    return json(200, { ...PARAMETROS.find((p) => p.clave === clave), valor: (cuerpo as { valor: string }).valor });
  }
  return undefined;
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-06 · parámetros del sistema', () => {
  it('Administración edita el nombre y la capacidad de un salón', async () => {
    const fetchMock = backendFalso(sesionDe(['ADMINISTRACION'], 'Gabriela Paz'), catalogo);
    const usuario = userEvent.setup();
    montar('/parametros');

    expect(await screen.findByRole('heading', { name: 'Parámetros del sistema' })).toBeInTheDocument();
    await usuario.click(await screen.findByRole('button', { name: 'Editar Avril' }));
    const nombre = screen.getByLabelText('Nombre');
    await usuario.clear(nombre);
    await usuario.type(nombre, 'Avril Eventos');
    await usuario.type(screen.getByLabelText(/Capacidad/), '3a50');
    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Cambios guardados.')).toBeInTheDocument();
    expect(pedidos(fetchMock)).toContainEqual({
      metodo: 'PUT', ruta: '/salones/1', cuerpo: { nombre: 'Avril Eventos', capacidad: 350, activo: true },
    });
    expect(screen.getByRole('button', { name: 'Editar Avril Eventos' })).toBeInTheDocument();
  });

  it('muestra el motivo del rechazo, p. ej. un nombre repetido', async () => {
    backendFalso(sesionDe(['COORDINACION']), (p) =>
      p.metodo === 'PUT' ? problema(409, 'NOMBRE_REPETIDO', 'Ya hay un salón con ese nombre.') : catalogo(p),
    );
    const usuario = userEvent.setup();
    montar('/parametros');

    await usuario.click(await screen.findByRole('button', { name: 'Editar Avril' }));
    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ya hay un salón con ese nombre.');
  });

  it('en turnos avisa cuando el horario termina al día siguiente', async () => {
    backendFalso(sesionDe(['DIRECCION']), catalogo);
    const usuario = userEvent.setup();
    montar('/parametros');

    await usuario.click(await screen.findByRole('tab', { name: 'Turnos' }));
    expect(await screen.findByText('20:00 a 06:00 del día siguiente')).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Editar Mediodía' }));
    expect(screen.queryByText('Termina al día siguiente.')).not.toBeInTheDocument();

    const fin = screen.getByLabelText('Hora de fin');
    await usuario.clear(fin);
    await usuario.type(fin, '01:00');
    expect(screen.getByText('Termina al día siguiente.')).toBeInTheDocument();
  });

  it('agrega un tipo de evento y marca los dados de baja', async () => {
    const fetchMock = backendFalso(sesionDe(['ADMINISTRACION']), catalogo);
    const usuario = userEvent.setup();
    montar('/parametros?seccion=tipos');

    const cumple = (await screen.findByText('Cumpleaños')).closest('li') as HTMLElement;
    expect(within(cumple).getByText('De baja')).toBeInTheDocument();

    await usuario.click(screen.getByRole('button', { name: 'Agregar tipo de evento' }));
    await usuario.type(screen.getByLabelText('Nombre'), 'Bautismo');
    // El segundo botón es el del formulario; el primero, el de la lista.
    await usuario.click(screen.getAllByRole('button', { name: 'Agregar tipo de evento' })[1]);

    expect(await screen.findByRole('button', { name: 'Editar Bautismo' })).toBeInTheDocument();
    expect(pedidos(fetchMock)).toContainEqual({ metodo: 'POST', ruta: '/tipos-evento', cuerpo: { nombre: 'Bautismo', usaSegmentos: false } });
  });

  it('si el aviso de sesión sube, guarda primero la duración de la sesión', async () => {
    const fetchMock = backendFalso(sesionDe(['ADMINISTRACION']), catalogo);
    const usuario = userEvent.setup();
    montar('/parametros?seccion=generales');

    const sesion = await screen.findByLabelText('Sesión sin uso antes de pedir volver a ingresar');
    const aviso = screen.getByLabelText('Aviso antes de que venza la sesión');
    await usuario.clear(sesion);
    await usuario.type(sesion, '120');
    await usuario.clear(aviso);
    await usuario.type(aviso, '10');
    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Cambios guardados.')).toBeInTheDocument();
    const puts = pedidos(fetchMock).filter((p) => p.metodo === 'PUT').map((p) => p.ruta);
    expect(puts).toEqual(['/parametros/MINUTOS_EXPIRACION_SESION', '/parametros/MINUTOS_AVISO_EXPIRACION']);
  });

  it('la vendedora no entra a parámetros', async () => {
    backendFalso(sesionDe(['VENDEDORA']), catalogo);
    montar('/parametros');

    expect(await screen.findByText('Tu perfil no tiene acceso a esta pantalla')).toBeInTheDocument();
  });
});
