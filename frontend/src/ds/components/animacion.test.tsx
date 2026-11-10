import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CelebracionHito } from './animacion';

describe('CelebracionHito', () => {
  afterEach(() => { vi.useRealTimers(); });

  it('muestra el hito y se cierra tocando o con Escape', () => {
    const alCerrar = vi.fn();
    render(<CelebracionHito titulo="Evento confirmado" detalle="Se avisó a Compras y Cocina." alCerrar={alCerrar} />);
    expect(screen.getByRole('status')).toHaveTextContent('Evento confirmado');
    fireEvent.keyDown(document, { key: 'Escape' });
    fireEvent.click(screen.getByText('Evento confirmado'));
    expect(alCerrar).toHaveBeenCalledTimes(2);
  });

  it('se cierra solo', () => {
    vi.useFakeTimers();
    const alCerrar = vi.fn();
    render(<CelebracionHito titulo="Evento cerrado" alCerrar={alCerrar} />);
    act(() => { vi.advanceTimersByTime(6000); });
    expect(alCerrar).toHaveBeenCalled();
  });
});
