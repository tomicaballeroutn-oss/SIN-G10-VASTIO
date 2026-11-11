import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { aplicarTema } from '../../ds/tema';
import { BotonTema } from './BotonTema';

describe('botón de tema', () => {
  afterEach(() => {
    aplicarTema('claro');
    localStorage.removeItem('vastio.tema');
    vi.unstubAllGlobals();
  });

  it('pasa a oscuro y vuelve a claro, y lo recuerda', () => {
    document.documentElement.dataset.theme = 'light';
    render(<BotonTema />);
    fireEvent.click(screen.getByRole('button', { name: 'Tema oscuro' }));
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(localStorage.getItem('vastio.tema')).toBe('oscuro');
    fireEvent.click(screen.getByRole('button', { name: 'Tema claro' }));
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('actualiza el botón si cambia el tema del dispositivo', () => {
    let cambio: (() => void) | undefined;
    const consulta = {
      matches: false,
      addEventListener: vi.fn((_evento: string, listener: () => void) => { cambio = listener; }),
      removeEventListener: vi.fn(),
    };
    vi.stubGlobal('matchMedia', vi.fn(() => consulta));
    aplicarTema('sistema');
    render(<BotonTema />);
    expect(screen.getByRole('button', { name: 'Tema oscuro' })).toBeInTheDocument();

    consulta.matches = true;
    act(() => cambio?.());

    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(screen.getByRole('button', { name: 'Tema claro' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Tema claro' }));
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(localStorage.getItem('vastio.tema')).toBe('claro');
  });
});
