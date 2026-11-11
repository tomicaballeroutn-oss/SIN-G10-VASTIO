import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { BotonTema } from './BotonTema';

describe('botón de tema', () => {
  afterEach(() => { document.documentElement.dataset.theme = 'light'; localStorage.removeItem('vastio.tema'); });

  it('pasa a oscuro y vuelve a claro, y lo recuerda', () => {
    document.documentElement.dataset.theme = 'light';
    render(<BotonTema />);
    fireEvent.click(screen.getByRole('button', { name: 'Tema oscuro' }));
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(localStorage.getItem('vastio.tema')).toBe('oscuro');
    fireEvent.click(screen.getByRole('button', { name: 'Tema claro' }));
    expect(document.documentElement.dataset.theme).toBe('light');
  });
});
