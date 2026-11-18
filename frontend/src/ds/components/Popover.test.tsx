import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Dialog } from './feedback';
import { Popover } from './Popover';

function Ayuda() {
  return <Popover title="Ayuda de prueba" label="Abrir ayuda de prueba"><p>Una explicación breve.</p><a href="#articulo">Saber más</a></Popover>;
}

describe('Popover de ayuda', () => {
  it('abre con clic, permite cerrar y devuelve el foco al disparador', async () => {
    render(<Ayuda />);
    const persona = userEvent.setup();
    const boton = screen.getByRole('button', { name: 'Abrir ayuda de prueba' });
    expect(boton).toHaveAttribute('aria-expanded', 'false');
    await persona.click(boton);
    const panel = screen.getByRole('dialog', { name: 'Ayuda de prueba' });
    expect(panel).toHaveFocus();
    expect(panel).toHaveAccessibleDescription('Una explicación breve. Saber más');
    expect(boton).toHaveAttribute('aria-expanded', 'true');
    await persona.click(within(panel).getByRole('button', { name: 'Cerrar ayuda' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(boton).toHaveFocus();
  });

  it('se usa con Espacio, Tab y Escape', async () => {
    render(<Ayuda />);
    const persona = userEvent.setup();
    await persona.tab();
    await persona.keyboard(' ');
    expect(screen.getByRole('dialog')).toHaveFocus();
    await persona.tab();
    expect(screen.getByRole('button', { name: 'Cerrar ayuda' })).toHaveFocus();
    await persona.tab();
    expect(screen.getByRole('link', { name: 'Saber más' })).toHaveFocus();
    await persona.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Abrir ayuda de prueba' })).toHaveFocus();
    await persona.keyboard('{Enter}');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('se cierra al interactuar afuera y conserva la acción del control externo', async () => {
    const accion = vi.fn();
    render(<><Ayuda /><button onClick={accion}>Otra acción</button></>);
    const persona = userEvent.setup();
    await persona.click(screen.getByRole('button', { name: 'Abrir ayuda de prueba' }));
    await persona.click(screen.getByRole('button', { name: 'Otra acción' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(accion).toHaveBeenCalledOnce();
  });

  it('Escape cierra solo la ayuda dentro de un diálogo y permite continuar con sus acciones', async () => {
    const cerrar = vi.fn();
    const confirmar = vi.fn();
    render(<Dialog open title="Confirmar operación" onClose={cerrar} actions={<button onClick={confirmar}>Confirmar</button>}><Ayuda /></Dialog>);
    const persona = userEvent.setup();
    const dialogo = screen.getByRole('dialog', { name: 'Confirmar operación' });
    const disparador = within(dialogo).getByRole('button', { name: 'Abrir ayuda de prueba' });
    await persona.click(disparador);
    expect(dialogo).toContainElement(screen.getByRole('dialog', { name: 'Ayuda de prueba' }));
    await persona.keyboard('{Escape}');
    expect(cerrar).not.toHaveBeenCalled();
    expect(disparador).toHaveFocus();
    await persona.click(within(dialogo).getByRole('button', { name: 'Confirmar' }));
    expect(confirmar).toHaveBeenCalledOnce();
    await persona.keyboard('{Escape}');
    expect(cerrar).toHaveBeenCalledOnce();
  });

  it('abrir la ayuda dentro de summary conserva la leyenda cerrada', async () => {
    render(<details><summary>Estados <Ayuda /></summary><p>La leyenda existente.</p></details>);
    const persona = userEvent.setup();
    await persona.click(screen.getByRole('button', { name: 'Abrir ayuda de prueba' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(document.querySelector('details')).not.toHaveAttribute('open');
  });
});
