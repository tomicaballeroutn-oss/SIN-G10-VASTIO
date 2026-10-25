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
  servicios: servicios({ Bodega: 'Malbec Luigi Bosca' }).map((s) => (s.categoria === 'Menús especiales' ? {
    ...s, renglones: [{ texto: 'Celíaco', cantidad: 2 }, { texto: 'Sin lactosa', cantidad: 3 }],
    usuario: { id: 1, nombre: 'Lucía Ferreyra' }, fechaModificacion: '2026-10-01T09:40:00-03:00',
  } : s)),
  historial: [
    ...EDITABLE.historial,
    {
      tipo: 'MODIFICACION', campo: 'servicio.Menús especiales', valorNuevo: 'Celíaco (2), Sin lactosa (3)',
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

describe('UI-13 · servicios contratados (M-09)', () => {
  it('carga renglones con cantidad y un detalle, y manda solo las categorías que cambiaron', async () => {
    const fetchMock = backend();
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=servicios');

    const especiales = (await screen.findByRole('group', { name: 'Menús especiales' }));
    const guardar = screen.getByRole('button', { name: 'Guardar servicios' });
    expect(guardar).toBeDisabled();

    await usuario.type(within(especiales).getByLabelText('Menús especiales: renglón 1'), 'Celíaco');
    await usuario.type(within(especiales).getByLabelText('Menús especiales: cantidad del renglón 1'), '2a');
    await usuario.click(within(especiales).getByRole('button', { name: 'Agregar renglón' }));
    await usuario.type(within(especiales).getByLabelText('Menús especiales: renglón 2'), 'Sin lactosa');
    await usuario.type(within(especiales).getByLabelText('Menús especiales: cantidad del renglón 2'), '3');
    await usuario.click(within(especiales).getByRole('button', { name: 'Agregar renglón' }));
    await usuario.type(screen.getByLabelText(/Detalle de Bodega/), 'Malbec Luigi Bosca');
    await usuario.click(guardar);

    expect(await screen.findByText('Servicios guardados.')).toBeInTheDocument();
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toEqual({
      version: 1,
      servicios: [
        { categoriaId: 5, descripcion: '', renglones: [{ texto: 'Celíaco', cantidad: 2 }, { texto: 'Sin lactosa', cantidad: 3 }] },
        { categoriaId: 6, descripcion: 'Malbec Luigi Bosca', renglones: [] },
      ],
    });
    expect(screen.getByRole('tab', { name: /Servicios/ })).toHaveTextContent('2');
  });

  it('quitar el único renglón lo deja vacío para volver a escribir', async () => {
    const lista = servicios({}).map((s) => (s.categoria === 'Menú' ? { ...s, renglones: [{ texto: 'Lomo', cantidad: null }] } : s));
    const fetchMock = backend({ ...EDITABLE, servicios: lista });
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=servicios');

    expect(await screen.findByLabelText('Menú: renglón 1')).toHaveValue('Lomo');
    await usuario.click(screen.getByRole('button', { name: 'Quitar el renglón 1 de Menú' }));
    expect(screen.getByLabelText('Menú: renglón 1')).toHaveValue('');
    await usuario.click(screen.getByRole('button', { name: 'Guardar servicios' }));

    await screen.findByText('Servicios guardados.');
    expect(pedidos(fetchMock).find((p) => p.metodo === 'PUT')?.cuerpo).toEqual({
      version: 1, servicios: [{ categoriaId: 2, descripcion: '', renglones: [] }],
    });
  });

  it('sin permiso para modificar los ve en solo lectura, con las cantidades', async () => {
    backend({ ...GUARDADA, acciones: SIN_ACCIONES }, undefined, ['COMPRAS']);
    montar('/eventos/5?pestana=servicios');

    expect(await screen.findByText('Malbec Luigi Bosca')).toBeInTheDocument();
    expect(screen.getAllByRole('listitem').map((li) => li.textContent)).toEqual(['Celíaco · 2', 'Sin lactosa · 3']);
    expect(screen.getByRole('heading', { name: 'Menús especiales' })).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar servicios' })).not.toBeInTheDocument();
  });

  it('una categoría dada de baja se ve pero no se edita', async () => {
    const baja = { ...servicios({})[0], categoriaId: 4, categoria: 'After', activa: false, requeridaParaConfirmar: false, descripcion: 'DJ hasta las 5' };
    backend({ ...EDITABLE, servicios: [...servicios({}), baja] });
    montar('/eventos/5?pestana=servicios');

    expect(await screen.findByText('DJ hasta las 5')).toBeInTheDocument();
    expect(screen.getByText('Categoría dada de baja')).toBeInTheDocument();
    expect(screen.queryByLabelText('After: renglón 1')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Menú: renglón 1')).toBeInTheDocument();
  });

  it('las requeridas para confirmar lo avisan', async () => {
    backend();
    montar('/eventos/5?pestana=servicios');

    const menu = await screen.findByRole('group', { name: 'Menú' });
    expect(within(menu).getByText('Hace falta para confirmar')).toBeInTheDocument();
    expect(within(screen.getByRole('group', { name: 'Bodega' })).queryByText('Hace falta para confirmar')).not.toBeInTheDocument();
  });

  it('muestra lo que rechaza el sistema', async () => {
    backend(EDITABLE, () => problema(422, 'SERVICIO_REQUERIDO', 'El evento está confirmado: Tipo de barra no puede quedar vacío.'));
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=servicios');

    await usuario.type(await screen.findByLabelText('Otros: renglón 1'), 'Cabina de fotos');
    await usuario.click(screen.getByRole('button', { name: 'Guardar servicios' }));

    expect(await screen.findByText('El evento está confirmado: Tipo de barra no puede quedar vacío.')).toBeInTheDocument();
  });

  it('el error de un renglón se muestra en ese renglón, aunque haya renglones vacíos antes', async () => {
    backend(EDITABLE, () => problema(400, 'DATOS_INVALIDOS', 'Revisá los datos marcados.', [
      { campo: 'servicios[1].renglones[0].texto', mensaje: 'Usá hasta 120 caracteres.' },
    ]));
    const usuario = userEvent.setup();
    montar('/eventos/5?pestana=servicios');

    await usuario.type(await screen.findByLabelText('Menú: renglón 1'), 'Lomo');
    const otros = screen.getByRole('group', { name: 'Otros' });
    await usuario.click(within(otros).getByRole('button', { name: 'Agregar renglón' }));
    await usuario.type(within(otros).getByLabelText('Otros: renglón 2'), 'Mucho texto');
    await usuario.click(screen.getByRole('button', { name: 'Guardar servicios' }));

    expect(await screen.findByText('Usá hasta 120 caracteres.')).toBeInTheDocument();
    expect(screen.getByLabelText('Otros: renglón 2')).toHaveAccessibleDescription('Usá hasta 120 caracteres.');
    expect(screen.getByLabelText('Otros: renglón 1')).not.toHaveAccessibleDescription('Usá hasta 120 caracteres.');
  });

  it('el historial cuenta qué servicio cambió', async () => {
    backend(GUARDADA);
    montar('/eventos/5?pestana=historial');

    const historial = await screen.findByText(/Cargó Menús especiales: Celíaco \(2\), Sin lactosa \(3\)/);
    expect(within(historial.closest('li') ?? document.body).getByText(/Bodega: Malbec → Malbec Luigi Bosca/)).toBeInTheDocument();
  });
});
