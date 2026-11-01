import { useState } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { FiltroMultiple } from './FiltroMultiple';

function Ejemplo() {
  const [valores, setValores] = useState<string[]>([]);
  return <><FiltroMultiple label="Estados" opciones={[{ value: 'a', label: 'Señado' }, { value: 'b', label: 'Confirmado' }]}
    valores={valores} alCambiar={setValores} /><button>Fuera</button></>;
}

describe('selector múltiple accesible', () => {
  it.each(['Listo', 'Cerrar filtro de estados'])('devuelve el foco al disparador al cerrar con %s', async (nombre) => {
    render(<Ejemplo />);
    const usuario = userEvent.setup();
    const disparador = screen.getByRole('button', { name: 'Estados' });
    disparador.focus();
    await usuario.keyboard('{Enter}');
    const grupo = screen.getByRole('group', { name: 'Estados' });
    expect(disparador).toHaveAttribute('aria-expanded', 'true');
    expect(document.getElementById(disparador.getAttribute('aria-controls')!)).toContainElement(grupo);
    await usuario.click(screen.getByRole('button', { name: nombre }));
    expect(screen.queryByRole('group', { name: 'Estados' })).not.toBeInTheDocument();
    expect(disparador).toHaveFocus();
    expect(disparador).toHaveAttribute('aria-expanded', 'false');
  });

  it('permite selección múltiple con teclado, limpiar y Escape sin perder el foco', async () => {
    render(<Ejemplo />);
    const usuario = userEvent.setup();
    const disparador = screen.getByRole('button', { name: 'Estados' });
    await usuario.click(disparador);
    const grupo = screen.getByRole('group', { name: 'Estados' });
    const senado = within(grupo).getByRole('checkbox', { name: 'Señado' });
    senado.focus();
    await usuario.keyboard(' ');
    expect(senado).toBeChecked();
    await usuario.click(within(grupo).getByRole('checkbox', { name: 'Confirmado' }));
    expect(disparador).toHaveAccessibleName('Estados 2');
    await usuario.click(screen.getByRole('button', { name: 'Limpiar' }));
    expect(senado).not.toBeChecked();
    await usuario.keyboard('{Escape}');
    expect(disparador).toHaveFocus();
    await usuario.click(disparador);
    await usuario.click(screen.getByRole('button', { name: 'Fuera' }));
    expect(screen.getByRole('button', { name: 'Fuera' })).toHaveFocus();
    expect(screen.queryByRole('group', { name: 'Estados' })).not.toBeInTheDocument();
  });
});
