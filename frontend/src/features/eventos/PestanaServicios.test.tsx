import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import type { Ficha } from '../../api/eventos';
import { backendFalso, json, montar, pedidos, problema, sesionDe, type Pedido } from '../../test/backendFalso';
import { fichaDePrueba, servicios, SIN_ACCIONES } from '../../test/fichas';

const EDITABLE = fichaDePrueba({ acciones: { ...SIN_ACCIONES, modificar: true } });

const GUARDADA: Ficha = {
  ...EDITABLE,
  version: 2,
  servicios: servicios({ 'Plato principal': 'Lomo con papas rústicas', Bodega: 'Malbec Luigi Bosca' }),
  historial: [
    ...EDITABLE.historial,
    {
      tipo: 'MODIFICACION', campo: 'servicio.Plato principal', valorNuevo: 'Lomo con papas rústicas',
      usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-10-01T09:40:00-03:00',
    },
    {
      tipo: 'MODIFICACION', campo: 'servicio.Bodega', valorAnterior: 'Malbec', valorNuevo: 'Malbec Luigi Bosca',
      usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaHora: '2026-10-01T09:40:00-03:00',
    },
  ],
};

function backend(ficha: Ficha = EDITABLE, alGuardar: (p: Pedido) => Response = () => json(200, GUARDADA), roles: Rol[] = ['VENDEDORA']) {
  return backendFalso(sesionDe(roles), (p) => {
    if (p.metodo === 'GET' && p.ruta === '/eventos/5') return json(200, ficha);
    if (p.metodo === 'PUT' && p.ruta === '/eventos/5/servicios') return alGuardar(p);
    return undefined;
  });
}

beforeEach(() => {
  _reiniciarCliente();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('UI-13 · servicios contratados', () => {
  it('carga un texto por categoría y manda solo las que cambiaron', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=servicios');

    expect(await screen.findByLabelText('Plato principal')).toHaveAccessibleDescription('Hace falta para confirmar el evento.');
    const guardar = screen.getByRole('button', { name: 'Guardar servicios' });
    expect(guardar).toBeDisabled();

    await usuario.type(screen.getByLabelText('Plato principal'), 'Lomo con papas rústicas');
    await usuario.type(screen.getByLabelText('Bodega'), 'Malbec Luigi Bosca');
    await usuario.click(guardar);

    expect(await screen.findByText('Servicios guardados.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toEqual({
      version: 1,
      servicios: [
        { categoriaId: 2, descripcion: 'Lomo con papas rústicas' },
        { categoriaId: 6, descripcion: 'Malbec Luigi Bosca' },
      ],
    });
    expect(screen.getByRole('tab', { name: /Servicios/ })).toHaveTextContent('2');
    expect(screen.getAllByText('Ana Sosa').length).toBeGreaterThan(0);
  });

  it('sin permiso para modificar los ve en solo lectura', async () => {
    backend(fichaDePrueba({ servicios: servicios({ 'Tipo de barra': 'Barra libre premium' }) }), undefined, ['COMPRAS']);
    montar('/eventos/5?pestana=servicios');

    expect(await screen.findByText('Barra libre premium')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Tipo de barra' })).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar servicios' })).not.toBeInTheDocument();
  });

  it('una categoría dada de baja se ve pero no se edita', async () => {
    const lista = servicios({ After: 'DJ hasta las 5' }).map((s) => (s.categoria === 'After' ? { ...s, activa: false } : s));
    backend({ ...EDITABLE, servicios: lista });
    montar('/eventos/5?pestana=servicios');

    expect(await screen.findByText('DJ hasta las 5')).toBeInTheDocument();
    expect(screen.getByText('Categoría dada de baja')).toBeInTheDocument();
    expect(screen.queryByLabelText('After')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Recepción')).toBeInTheDocument();
  });

  it('muestra lo que rechaza el sistema', async () => {
    backend(EDITABLE, () => problema(422, 'SERVICIO_REQUERIDO', 'El evento está confirmado: Tipo de barra no puede quedar vacío.'));
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=servicios');

    await usuario.type(await screen.findByLabelText('Postre'), 'Mesa dulce');
    await usuario.click(screen.getByRole('button', { name: 'Guardar servicios' }));

    expect(await screen.findByText('El evento está confirmado: Tipo de barra no puede quedar vacío.')).toBeInTheDocument();
  });

  it('el error de un campo se muestra en esa categoría', async () => {
    backend(EDITABLE, () => problema(400, 'DATOS_INVALIDOS', 'Revisá los datos marcados.', [
      { campo: 'servicios[1].descripcion', mensaje: 'Usá hasta 2000 caracteres.' },
    ]));
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=servicios');

    await usuario.type(await screen.findByLabelText('Recepción'), 'Finger food');
    await usuario.type(screen.getByLabelText('Extras'), 'Mucho texto');
    await usuario.click(screen.getByRole('button', { name: 'Guardar servicios' }));

    expect(await screen.findByText('Usá hasta 2000 caracteres.')).toBeInTheDocument();
    expect(screen.getByLabelText('Extras')).toHaveAccessibleDescription('Usá hasta 2000 caracteres.');
  });

  it('el historial cuenta qué servicio cambió', async () => {
    backend(GUARDADA);
    montar('/eventos/5?pestana=historial');

    const historial = await screen.findByText(/Cargó Plato principal: Lomo con papas rústicas/);
    expect(within(historial.closest('li') ?? document.body).getByText(/Bodega: Malbec → Malbec Luigi Bosca/)).toBeInTheDocument();
  });
});
