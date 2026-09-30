import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Combobox, type ComboboxOption } from './formularios';

const OPCIONES: ComboboxOption[] = [
  { value: '1', label: 'Delfina Ríos', detail: 'DNI 40123456' },
  { value: '2', label: 'Estudio Ríos y Asociados' },
];

function Prueba({ alElegir }: { alElegir: (o: ComboboxOption) => void }) {
  const [texto, setTexto] = useState('');
  return <Combobox label="Cliente" value={texto} onInputChange={setTexto} options={OPCIONES} onSelect={alElegir} emptyText="Sin coincidencias" />;
}

describe('Combobox', () => {
  it('se recorre con flechas, se elige con Enter y se cierra con Escape', async () => {
    const alElegir = vi.fn();
    const usuario = userEvent.setup();
    render(<Prueba alElegir={alElegir} />);
    const campo = screen.getByRole('combobox', { name: 'Cliente' });

    expect(campo).toHaveAttribute('aria-expanded', 'false');
    await usuario.type(campo, 'rí');
    expect(campo).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getAllByRole('option')).toHaveLength(2);

    await usuario.keyboard('{ArrowDown}{ArrowDown}');
    expect(campo).toHaveAttribute('aria-activedescendant', screen.getByRole('option', { name: 'Estudio Ríos y Asociados' }).id);
    await usuario.keyboard('{Enter}');
    expect(alElegir).toHaveBeenCalledWith(OPCIONES[1]);
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

    await usuario.type(campo, 'x');
    expect(screen.getByRole('listbox')).toBeInTheDocument();
    await usuario.keyboard('{Escape}');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('sin texto no abre la lista', async () => {
    const usuario = userEvent.setup();
    render(<Prueba alElegir={vi.fn()} />);

    await usuario.click(screen.getByRole('combobox'));
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });
});
