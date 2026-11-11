import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { BotonSubir } from './BotonSubir';

describe('botón para subir', () => {
  afterEach(() => { vi.restoreAllMocks(); Object.defineProperty(window, 'scrollY', { value: 0, configurable: true }); });

  it('aparece al bajar y sube al tocarlo', () => {
    const subir = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    render(<BotonSubir />);
    expect(screen.queryByRole('button', { name: 'Subir' })).not.toBeInTheDocument();

    Object.defineProperty(window, 'scrollY', { value: 900, configurable: true });
    act(() => { window.dispatchEvent(new Event('scroll')); });
    fireEvent.click(screen.getByRole('button', { name: 'Subir' }));
    expect(subir).toHaveBeenCalledWith(expect.objectContaining({ top: 0 }));
  });
});
