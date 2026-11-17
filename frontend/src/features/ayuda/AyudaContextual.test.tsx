import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Rol } from '../../api/cliente';
import { sesionDe } from '../../test/backendFalso';
import { SesionContext, type ValorSesion } from '../sesion/contexto';
import { ARTICULOS } from './articulos';
import { AyudaContextual, type AyudaContextualProps } from './AyudaContextual';
import { AYUDAS } from './contextos';
import { rutaArticulo } from './modelo';
import { PaginaArticulo } from './PaginaArticulo';

function montarAyuda(ayuda: AyudaContextualProps, roles: Rol[] = ['DIRECCION']) {
  const valor: ValorSesion = {
    estado: 'activa', usuario: sesionDe(roles).usuario, vencida: false, avisoVencimiento: false,
    ingresar: vi.fn(), salir: vi.fn(), seguirTrabajando: vi.fn(),
  };
  const router = createMemoryRouter([
    { path: '/origen', element: <AyudaContextual {...ayuda} /> },
    { path: '/ayuda/:categoria/:articulo', element: <PaginaArticulo /> },
  ], { initialEntries: ['/origen?mes=2030-03#detalle'] });
  render(<SesionContext.Provider value={valor}><RouterProvider router={router} /></SesionContext.Provider>);
  return router;
}

beforeEach(() => vi.spyOn(window, 'scrollTo').mockImplementation(() => {}));

describe('ayuda contextual conectada con artículos', () => {
  it.each(Object.values(AYUDAS))('Saber más de $titulo abre su artículo y permite regresar al origen', async (ayuda) => {
    const router = montarAyuda(ayuda);
    const persona = userEvent.setup();
    const articulo = ARTICULOS.find((item) => item.id === ayuda.articuloId)!;
    await persona.click(screen.getByRole('button'));
    expect(screen.getByText(ayuda.explicacion)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Saber más' })).toHaveAttribute('href', rutaArticulo(articulo));
    await persona.click(screen.getByRole('link', { name: 'Saber más' }));
    expect(screen.getByRole('heading', { level: 1, name: articulo.titulo })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(rutaArticulo(articulo));
    await persona.click(screen.getByRole('link', { name: 'Volver a la pantalla anterior' }));
    expect(router.state.location.pathname + router.state.location.search + router.state.location.hash).toBe('/origen?mes=2030-03#detalle');
  });

  it.each<Rol>(['ADMINISTRACION', 'VENDEDORA', 'PLANNER', 'COMPRAS', 'BARRA', 'COCINA'])(
    'omite el acceso a ocupación para %s', (rol) => {
      montarAyuda(AYUDAS.ocupacion, [rol]);
      expect(screen.queryByRole('button')).not.toBeInTheDocument();
    },
  );

  it('usa la unión de perfiles', async () => {
    montarAyuda(AYUDAS.historial, ['BARRA', 'VENDEDORA']);
    await userEvent.click(screen.getByRole('button'));
    expect(screen.getByRole('link', { name: 'Saber más' })).toBeInTheDocument();
  });

  it('omite un identificador inexistente', () => {
    montarAyuda({ ...AYUDAS.historial, articuloId: 'inexistente' });
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
