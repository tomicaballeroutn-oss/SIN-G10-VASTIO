import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import type { Parametro, Salon, Segmento, TipoEvento, Turno } from '../../api/catalogos';
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

const SEGMENTOS: Segmento[] = [
  { id: 1, nombre: 'Comensales cena', activo: true, equivaleInvitados: true },
  { id: 2, nombre: 'Entradas anticipadas', activo: true, equivaleInvitados: false },
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
  if (metodo === 'GET' && ruta === '/segmentos-asistencia') return json(200, SEGMENTOS);
  if (metodo === 'PUT' && ruta === '/segmentos-asistencia/2') return json(200, { id: 2, ...(cuerpo as object) });
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

  it('marca qué segmento de asistencia equivale a los invitados del evento', async () => {
    const fetchMock = backendFalso(sesionDe(['ADMINISTRACION']), catalogo);
    const usuario = userEvent.setup();
    montar('/parametros?seccion=segmentos');

    expect(await screen.findByText('· los invitados del evento', { exact: false })).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Editar Entradas anticipadas' }));
    await usuario.click(screen.getByLabelText(/Equivale a los invitados del evento/));
    await usuario.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByRole('button', { name: 'Editar Entradas anticipadas' })).toBeInTheDocument();
    expect(pedidos(fetchMock)).toContainEqual({
      metodo: 'PUT', ruta: '/segmentos-asistencia/2', cuerpo: { nombre: 'Entradas anticipadas', activo: true, equivaleInvitados: true },
    });
    // Se vuelve a pedir la lista: el anterior perdió la marca.
    expect(pedidos(fetchMock).filter((p) => p.ruta === '/segmentos-asistencia' && p.metodo === 'GET')).toHaveLength(2);
  });

  it('carga las proporciones de consumo de un tipo de evento', async () => {
    const fernet = {
      id: 10, nombre: 'Fernet Branca', presentacion: '750 ml', tipo: { id: 3, nombre: 'Destilado' },
      unidad: { id: 1, nombre: 'Caja', esBotella: false }, unidadesPorBulto: 6, stockMinimo: null, codigos: [], activo: true, fechaBaja: null,
    };
    const guardada = { id: 1, tipoEventoId: 1, bebidaId: 10, nombre: 'Fernet Branca', presentacion: '750 ml', activa: true, botellasPorAsistente: 0.05 };
    let cargadas: unknown[] = [];
    const fetchMock = backendFalso(sesionDe(['ADMINISTRACION']), (p) => {
      if (p.metodo === 'GET' && p.ruta === '/bebidas') return json(200, [fernet]);
      if (p.metodo === 'GET' && p.ruta === '/proporciones-consumo?tipoEventoId=1') return json(200, cargadas);
      if (p.metodo === 'PUT' && p.ruta === '/proporciones-consumo') {
        cargadas = [guardada];
        return json(200, guardada);
      }
      return catalogo(p);
    });
    const usuario = userEvent.setup();
    montar('/parametros?seccion=proporciones');

    expect(await screen.findByText('Sin proporciones cargadas para este tipo de evento.')).toBeInTheDocument();
    await usuario.type(screen.getByLabelText('Bebida'), 'fer');
    await usuario.click(await screen.findByRole('option', { name: /Fernet Branca 750 ml/ }));
    await usuario.type(screen.getByLabelText('Botellas por asistente'), '0,05');
    await usuario.click(screen.getByRole('button', { name: 'Guardar proporción' }));

    const tabla = await screen.findByRole('table', { name: 'Proporciones del tipo de evento' });
    expect(await within(tabla).findByText('0,05')).toBeInTheDocument();
    expect(pedidos(fetchMock)).toContainEqual({
      metodo: 'PUT', ruta: '/proporciones-consumo', cuerpo: { tipoEventoId: 1, bebidaId: 10, botellasPorAsistente: 0.05 },
    });
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
