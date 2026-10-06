import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import { ROLES, type Usuario } from '../../api/usuarios';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';

const usuario = (id: number, nombreCompleto: string, nombreUsuario: string, roles: Usuario['roles'], activo = true): Usuario => ({
  id, nombreCompleto, nombreUsuario, roles, activo, email: null, telefono: null, debeCambiarContrasena: false,
  fechaAlta: '2026-09-01T10:00:00-03:00', fechaBaja: null,
});

const USUARIOS: Usuario[] = [
  usuario(1, 'Melina Sifón', 'melina.sifon', ['COORDINACION']),
  usuario(2, 'Lucía Ferreyra', 'lucia.ferreyra', ['VENDEDORA']),
  usuario(3, 'Ana Sosa', 'ana.sosa', ['PLANNER']),
  usuario(4, 'Ex Empleada', 'ex.empleada', ['BARRA'], false),
];

function backend({ metodo, ruta, cuerpo }: Pedido) {
  if (metodo === 'GET' && ruta === '/usuarios') return json(200, USUARIOS);
  if (metodo === 'POST' && ruta === '/usuarios') {
    const alta = cuerpo as { nombreCompleto: string; nombreUsuario: string; roles: Usuario['roles'] };
    return json(201, { ...usuario(9, alta.nombreCompleto, alta.nombreUsuario, alta.roles), debeCambiarContrasena: true });
  }
  if (metodo === 'POST' && ruta === '/usuarios/2/baja') return json(200, { ...USUARIOS[1], activo: false });
  if (metodo === 'GET' && ruta === '/eventos/afectados?usuarioId=2') return json(200, []);
  if (metodo === 'GET' && ruta === '/eventos/afectados?usuarioId=3') {
    return json(200, [{
      id: 5, codigo: 'EV-2026-00005', estado: 'CONTRATADO', nombre: 'Bruno y Martina', tipo: 'Casamiento',
      salon: { id: 1, codigo: 'avril', nombre: 'Avril', capacidad: null }, fecha: '2026-11-14',
      turno: { id: 2, codigo: 'noche', nombre: 'Noche', horaInicio: '20:00:00', horaFin: '06:00:00' },
      cliente: 'Martina Gómez', vendedora: { id: 2, nombre: 'Lucía Ferreyra' }, planner: { id: 3, nombre: 'Ana Sosa' }, cantidadInvitados: 180,
    }]);
  }
  if (metodo === 'POST' && ruta === '/usuarios/3/baja') return json(200, { ...USUARIOS[2], activo: false });
  if (metodo === 'PUT' && ruta === '/usuarios/3') {
    const datos = cuerpo as { nombreCompleto: string; roles: Usuario['roles']; email: string };
    // Como el backend: los perfiles vuelven en el orden de la matriz.
    return json(200, { ...USUARIOS[2], nombreCompleto: datos.nombreCompleto, roles: ROLES.filter((r) => datos.roles.includes(r)), email: datos.email || null });
  }
  if (metodo === 'PUT' && ruta === '/usuarios/1') {
    return problema(422, 'PERFIL_PROPIO', 'No podés quitarte tu propio perfil de Dirección o Coordinación. Pedíselo a otra persona con acceso total.');
  }
  if (metodo === 'POST' && ruta === '/usuarios/4/reactivacion') return json(200, { ...USUARIOS[3], activo: true });
  return undefined;
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-05 · alta mínima de usuarios', () => {
  it('lista los usuarios, filtra por perfil y marca los dados de baja', async () => {
    backendFalso(sesionDe(['COORDINACION'], 'Melina Sifón', 1), backend);
    const persona = userEvent.setup();
    montar('/usuarios');

    const ex = (await screen.findByText('Ex Empleada')).closest('tr') as HTMLElement;
    expect(within(ex).getByText('De baja')).toBeInTheDocument();
    expect(within(ex).queryByRole('button', { name: 'Dar de baja' })).not.toBeInTheDocument();
    // Nadie se da de baja a sí mismo.
    const melina = within(screen.getByRole('table')).getByText('Melina Sifón').closest('tr') as HTMLElement;
    expect(within(melina).queryByRole('button', { name: 'Dar de baja' })).not.toBeInTheDocument();

    await persona.selectOptions(screen.getByLabelText('Perfil'), 'PLANNER');
    expect(screen.getByText('Ana Sosa')).toBeInTheDocument();
    expect(screen.queryByText('Lucía Ferreyra')).not.toBeInTheDocument();
  });

  it('da de alta un usuario con perfiles y contraseña inicial', async () => {
    const fetchMock = backendFalso(sesionDe(['DIRECCION']), backend);
    const persona = userEvent.setup();
    montar('/usuarios');

    await persona.click(await screen.findByRole('button', { name: 'Nuevo usuario' }));
    const dialogo = screen.getByRole('dialog', { name: 'Nuevo usuario' });
    await persona.type(within(dialogo).getByLabelText('Nombre y apellido'), 'Paula Díaz');
    await persona.type(within(dialogo).getByLabelText('Usuario'), 'Paula.Diaz');
    await persona.click(within(dialogo).getByLabelText('Vendedora'));
    await persona.click(within(dialogo).getByLabelText('Planner'));
    await persona.type(within(dialogo).getByLabelText('Contraseña inicial'), 'bienvenida-2026');
    await persona.click(within(dialogo).getByRole('button', { name: 'Dar de alta usuario' }));

    expect(await screen.findByText(/Usuario creado\. Pasale a Paula Díaz/)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByText('paula.diaz')).toBeInTheDocument();
    expect(pedidos(fetchMock)).toContainEqual({
      metodo: 'POST',
      ruta: '/usuarios',
      cuerpo: { nombreCompleto: 'Paula Díaz', nombreUsuario: 'paula.diaz', roles: ['VENDEDORA', 'PLANNER'], email: '', telefono: '', contrasenaInicial: 'bienvenida-2026' },
    });
  });

  it('muestra los errores de cada campo que devuelve el backend', async () => {
    backendFalso(sesionDe(['DIRECCION']), (p) =>
      p.metodo === 'POST'
        ? problema(400, 'DATOS_INVALIDOS', 'Revisá los datos marcados.', [
            { campo: 'roles', mensaje: 'Elegí al menos un perfil.' },
            { campo: 'contrasenaInicial', mensaje: 'La contraseña inicial tiene que tener entre 8 y 72 caracteres.' },
          ])
        : backend(p),
    );
    const persona = userEvent.setup();
    montar('/usuarios');

    await persona.click(await screen.findByRole('button', { name: 'Nuevo usuario' }));
    await persona.click(screen.getByRole('button', { name: 'Dar de alta usuario' }));

    const dialogo = screen.getByRole('dialog');
    expect(await within(dialogo).findByText('Elegí al menos un perfil.')).toBeInTheDocument();
    expect(within(dialogo).getByLabelText('Contraseña inicial')).toHaveAccessibleDescription('La contraseña inicial tiene que tener entre 8 y 72 caracteres.');
  });

  it('da de baja con confirmación', async () => {
    const fetchMock = backendFalso(sesionDe(['DIRECCION'], 'Dirección', 1), backend);
    const persona = userEvent.setup();
    montar('/usuarios');

    const lucia = (await screen.findByText('Lucía Ferreyra')).closest('tr') as HTMLElement;
    await persona.click(within(lucia).getByRole('button', { name: 'Dar de baja' }));
    await persona.click(screen.getByRole('button', { name: 'Dar de baja usuario' }));

    expect(await screen.findByText('Se dio de baja a Lucía Ferreyra. Lo que registró se conserva.')).toBeInTheDocument();
    expect(within(lucia).getByText('De baja')).toBeInTheDocument();
    expect(pedidos(fetchMock)).toContainEqual({ metodo: 'POST', ruta: '/usuarios/2/baja', cuerpo: undefined });
  });
});

describe('UI-05 · administrar usuarios', () => {
  it('modifica nombre, perfiles y contacto; el usuario no se edita', async () => {
    const fetchMock = backendFalso(sesionDe(['COORDINACION'], 'Melina Sifón', 1), backend);
    const persona = userEvent.setup();
    montar('/usuarios');

    const ana = (await screen.findByText('Ana Sosa')).closest('tr') as HTMLElement;
    await persona.click(within(ana).getByRole('button', { name: 'Modificar' }));
    const dialogo = screen.getByRole('dialog', { name: 'Modificar usuario' });
    expect(within(dialogo).getByLabelText('Usuario')).toHaveAttribute('readonly');
    const nombre = within(dialogo).getByLabelText('Nombre y apellido');
    await persona.clear(nombre);
    await persona.type(nombre, 'Ana María Sosa');
    await persona.click(within(dialogo).getByLabelText('Vendedora'));
    await persona.type(within(dialogo).getByLabelText(/Correo/), 'ana@salonavril.com');
    await persona.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText('Datos de Ana María Sosa guardados.')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('Vendedora · Planner')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toEqual({
      nombreCompleto: 'Ana María Sosa', roles: ['PLANNER', 'VENDEDORA'], email: 'ana@salonavril.com', telefono: '',
    });
  });

  it('muestra por qué no se puede quitar el propio acceso total', async () => {
    backendFalso(sesionDe(['COORDINACION'], 'Melina Sifón', 1), backend);
    const persona = userEvent.setup();
    montar('/usuarios');

    const melina = within(await screen.findByRole('table')).getByText('Melina Sifón').closest('tr') as HTMLElement;
    await persona.click(within(melina).getByRole('button', { name: 'Modificar' }));
    const dialogo = screen.getByRole('dialog', { name: 'Modificar usuario' });
    expect(within(dialogo).getByText('No podés quitarte tu propio perfil de Dirección o Coordinación.')).toBeInTheDocument();
    await persona.click(within(dialogo).getByLabelText('Coordinación comercial'));
    await persona.click(within(dialogo).getByLabelText('Vendedora'));
    await persona.click(within(dialogo).getByRole('button', { name: 'Guardar cambios' }));

    expect(await within(dialogo).findByText(/Pedíselo a otra persona con acceso total/)).toBeInTheDocument();
  });

  it('antes de dar de baja muestra los eventos activos de la persona', async () => {
    backendFalso(sesionDe(['DIRECCION'], 'Roberto Díaz', 9), backend);
    const persona = userEvent.setup();
    montar('/usuarios');

    const ana = (await screen.findByText('Ana Sosa')).closest('tr') as HTMLElement;
    await persona.click(within(ana).getByRole('button', { name: 'Dar de baja' }));
    const dialogo = screen.getByRole('dialog', { name: 'Dar de baja usuario' });

    expect(await within(dialogo).findByText('Tiene 1 evento activo')).toBeInTheDocument();
    expect(within(dialogo).getByText(/Bruno y Martina · Avril · sáb 14 nov 2026 · Noche \(planner\)/)).toBeInTheDocument();
    await persona.click(within(dialogo).getByRole('button', { name: 'Dar de baja usuario' }));
    expect(await screen.findByText('Se dio de baja a Ana Sosa. Lo que registró se conserva.')).toBeInTheDocument();
  });

  it('sin eventos activos lo dice', async () => {
    backendFalso(sesionDe(['DIRECCION'], 'Roberto Díaz', 9), backend);
    const persona = userEvent.setup();
    montar('/usuarios');

    const lucia = (await screen.findByText('Lucía Ferreyra')).closest('tr') as HTMLElement;
    await persona.click(within(lucia).getByRole('button', { name: 'Dar de baja' }));

    expect(await within(screen.getByRole('dialog')).findByText('No tiene eventos activos a su cargo.')).toBeInTheDocument();
  });

  it('reactiva a un usuario dado de baja', async () => {
    const fetchMock = backendFalso(sesionDe(['COORDINACION'], 'Melina Sifón', 1), backend);
    const persona = userEvent.setup();
    montar('/usuarios');

    const ex = (await screen.findByText('Ex Empleada')).closest('tr') as HTMLElement;
    await persona.click(within(ex).getByRole('button', { name: 'Reactivar' }));

    expect(await screen.findByText('Se reactivó a Ex Empleada. Ya puede ingresar con su contraseña de siempre.')).toBeInTheDocument();
    expect(within(ex).queryByText('De baja')).not.toBeInTheDocument();
    expect(pedidos(fetchMock).some((p) => p.metodo === 'POST' && p.ruta === '/usuarios/4/reactivacion')).toBe(true);
  });
});
