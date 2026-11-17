import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import { backendFalso, json, montar, sesionDe } from '../../test/backendFalso';

beforeEach(() => {
  _reiniciarCliente();
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
});

describe('buscador del centro de ayuda', () => {
  it('muestra las categorías con búsqueda vacía y actualiza los resultados al escribir', async () => {
    backendFalso(sesionDe(['DIRECCION']), () => json(200, []));
    montar('/ayuda');
    const persona = userEvent.setup();
    const campo = await screen.findByRole('searchbox', { name: 'Buscar en la ayuda' });
    expect(document.querySelectorAll('.ayuda__categorias h2')).toHaveLength(6);
    await persona.type(campo, 'CONTRASENA');
    const resultados = screen.getByRole('region', { name: 'Resultados de búsqueda' });
    const enlace = within(resultados).getByRole('link', { name: 'Mi cuenta y contraseña' });
    expect(within(enlace.closest('li')!).getByText('Primeros pasos')).toBeInTheDocument();
    expect(document.querySelector('.ayuda__categorias')).toBeNull();
    expect(campo).toHaveFocus();
    await persona.clear(campo);
    await persona.type(campo, 'ocupacion');
    const actuales = screen.getByRole('region', { name: 'Resultados de búsqueda' });
    expect(screen.getByRole('status')).toHaveTextContent(`${within(actuales).getAllByRole('link').length} artículos encontrados`);
    expect(within(actuales).getByRole('link', { name: 'Reporte de ocupación' })).toBeInTheDocument();
    expect(within(actuales).queryByRole('link', { name: 'Mi cuenta y contraseña' })).not.toBeInTheDocument();
  });

  it('abre el artículo desde un resultado del contenido textual', async () => {
    backendFalso(sesionDe(['DIRECCION']), () => json(200, []));
    const router = montar('/ayuda');
    const persona = userEvent.setup();
    await persona.type(await screen.findByRole('searchbox'), 'remanente heredado');
    const enlace = within(screen.getByRole('region', { name: 'Resultados de búsqueda' })).getByRole('link', { name: 'Cerrar un evento' });
    expect(enlace).toHaveAttribute('href', '/ayuda/barra/cierre');
    await persona.click(enlace);
    expect(await screen.findByRole('heading', { level: 1, name: 'Cerrar un evento' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/ayuda/barra/cierre');
  });

  it('informa sin coincidencias y permite limpiar con teclado, recuperando categorías y foco', async () => {
    backendFalso(sesionDe(['DIRECCION']), () => json(200, []));
    montar('/ayuda');
    const persona = userEvent.setup();
    const campo = await screen.findByRole('searchbox');
    await persona.type(campo, 'inexistentezz');
    expect(screen.getByText('No encontramos artículos')).toBeInTheDocument();
    await persona.tab();
    expect(screen.getByRole('button', { name: 'Limpiar búsqueda' })).toHaveFocus();
    await persona.keyboard('{Enter}');
    expect(campo).toHaveValue('');
    expect(campo).toHaveFocus();
    expect(document.querySelectorAll('.ayuda__categorias h2')).toHaveLength(6);
    await persona.type(campo, '   ');
    expect(screen.queryByRole('region', { name: 'Resultados de búsqueda' })).not.toBeInTheDocument();
    expect(document.querySelectorAll('.ayuda__categorias h2')).toHaveLength(6);
  });

  it('filtra los resultados por perfil', async () => {
    backendFalso(sesionDe(['BARRA']), () => json(200, []));
    montar('/ayuda');
    const persona = userEvent.setup();
    const campo = await screen.findByRole('searchbox');
    await persona.type(campo, 'ocupacion');
    expect(screen.getByText('No encontramos artículos')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Reporte de ocupación' })).not.toBeInTheDocument();
    await persona.clear(campo);
    await persona.type(campo, 'devoluciones');
    expect(within(screen.getByRole('region', { name: 'Resultados de búsqueda' })).getByRole('link', { name: 'Devoluciones y remanentes' })).toBeInTheDocument();
  });
});
