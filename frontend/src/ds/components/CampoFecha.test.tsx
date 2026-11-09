import { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { Input } from './formularios';
import { fechaDdMmAaaa, fechaIso } from '../fecha';

function Prueba({ inicial = '' }: { inicial?: string }) {
  const [valor, setValor] = useState(inicial);
  return (
    <>
      <Input label="Fecha" type="date" value={valor} onChange={(e) => setValor(e.target.value)} />
      <output aria-label="valor">{valor}</output>
    </>
  );
}

describe('CampoFecha', () => {
  it('convierte entre día/mes/año y aaaa-mm-dd y rechaza fechas que no existen', () => {
    expect(fechaDdMmAaaa('2026-11-09')).toBe('09/11/2026');
    expect(fechaIso('09/11/2026')).toBe('2026-11-09');
    expect(fechaIso('2026-11-09')).toBe('2026-11-09');
    expect(fechaIso('31/02/2026')).toBeNull();
    expect(fechaIso('09/11')).toBeNull();
  });

  it('muestra el valor como dd/mm/aaaa y pone las barras al escribir', async () => {
    const persona = userEvent.setup();
    render(<Prueba inicial="2026-11-09" />);
    const campo = screen.getByLabelText('Fecha');
    expect(campo).toHaveValue('09/11/2026');

    await persona.clear(campo);
    expect(screen.getByLabelText('valor')).toHaveTextContent('');
    await persona.type(campo, '0105');
    expect(campo).toHaveValue('01/05');
    expect(campo).toBeInvalid();
    await persona.type(campo, '2038');
    expect(campo).toHaveValue('01/05/2038');
    expect(campo).toBeValid();
    expect(screen.getByLabelText('valor')).toHaveTextContent('2038-05-01');
  });

  it('se elige en el calendario', async () => {
    const persona = userEvent.setup();
    render(<Prueba inicial="2030-03-09" />);
    await persona.click(screen.getByRole('button', { name: 'Elegir en el calendario' }));
    await persona.click(screen.getByRole('button', { name: /^15 de marzo/ }));
    expect(screen.getByLabelText('Fecha')).toHaveValue('15/03/2030');
    expect(screen.getByLabelText('valor')).toHaveTextContent('2030-03-15');
    expect(screen.queryByRole('dialog', { name: 'Elegir fecha' })).not.toBeInTheDocument();
  });
});
