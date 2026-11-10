import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { _reiniciarCliente } from '../../api/cliente';
import { backendFalso, json, montar, sesionDe } from '../../test/backendFalso';

beforeEach(_reiniciarCliente);

describe('teléfono con perfil Barra', () => {
  it('la barra inferior tiene «Más» y desde ahí se cierra sesión', async () => {
    backendFalso(sesionDe(['BARRA'], 'Rocío Blanco'), () => json(200, []));
    const usuario = userEvent.setup();
    montar('/existencias');

    const navs = await screen.findAllByRole('navigation', { name: 'Navegación principal' });
    const inferior = navs[navs.length - 1];
    await usuario.click(within(inferior).getByRole('button', { name: /Más/ }));
    const mas = (await screen.findByRole('heading', { name: 'Más', level: 1 })).closest('section') as HTMLElement;
    expect(within(mas).getByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument();
    expect(within(mas).getByRole('button', { name: 'Mi cuenta' })).toBeInTheDocument();
  });

  it('la flecha para volver aparece dentro de una pantalla y no en las del menú', async () => {
    backendFalso(sesionDe(['BARRA'], 'Rocío Blanco'), () => json(200, []));
    const usuario = userEvent.setup();
    const router = montar('/existencias');
    await screen.findAllByRole('navigation', { name: 'Navegación principal' });
    expect(screen.queryByRole('button', { name: 'Volver' })).not.toBeInTheDocument();

    await router.navigate('/mi-cuenta');
    await usuario.click(await screen.findByRole('button', { name: 'Volver' }));
    expect(router.state.location.pathname).toBe('/existencias');
  });
});
