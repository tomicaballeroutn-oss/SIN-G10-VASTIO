import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Input } from './formularios';

describe('Input con contraseña', () => {
  it('el ojito muestra y oculta lo escrito sin enviar el formulario ni sacar el foco', async () => {
    const usuario = userEvent.setup();
    const enviar = vi.fn((e: SubmitEvent) => e.preventDefault());
    render(<form onSubmit={(e) => enviar(e.nativeEvent as SubmitEvent)}><Input label="Contraseña" type="password" defaultValue="clave" /></form>);
    const campo = screen.getByLabelText('Contraseña');
    const ojito = screen.getByRole('button', { name: 'Mostrar contraseña' });

    expect(campo).toHaveAttribute('type', 'password');
    expect(ojito).toHaveAttribute('aria-pressed', 'false');
    expect(ojito).toHaveAttribute('aria-controls', campo.id);
    expect(ojito).toHaveAttribute('type', 'button');

    await usuario.click(campo);
    await usuario.click(ojito);
    expect(campo).toHaveAttribute('type', 'text');
    expect(screen.getByRole('button', { name: 'Ocultar contraseña' })).toHaveAttribute('aria-pressed', 'true');
    expect(campo).toHaveFocus();

    await usuario.click(screen.getByRole('button', { name: 'Ocultar contraseña' }));
    expect(campo).toHaveAttribute('type', 'password');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('con revelable={false} no hay ojito', () => {
    render(<Input label="Contraseña" type="password" revelable={false} />);
    expect(screen.queryByRole('button', { name: 'Mostrar contraseña' })).not.toBeInTheDocument();
  });

  it('en un campo de texto no hay ojito', () => {
    render(<Input label="Usuario" />);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
