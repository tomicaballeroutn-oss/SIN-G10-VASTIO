import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Bienvenida } from './Bienvenida';

describe('Bienvenida', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.stubGlobal('matchMedia', (q: string) => ({ matches: false, media: q }));
  });
  afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });

  it('se ve una vez por pestaña y se va al terminar la vuelta', () => {
    vi.useFakeTimers();
    const { unmount } = render(<Bienvenida />);
    expect(screen.getByRole('status', { name: 'Bienvenida a Vastio' })).toBeInTheDocument();
    fireEvent.ended(document.querySelector('video')!);
    act(() => { vi.advanceTimersByTime(300); });
    expect(screen.queryByRole('status', { name: 'Bienvenida a Vastio' })).not.toBeInTheDocument();
    unmount();

    render(<Bienvenida />); // recargar la misma pestaña
    expect(screen.queryByRole('status', { name: 'Bienvenida a Vastio' })).not.toBeInTheDocument();
  });

  it('se puede saltear tocando', () => {
    vi.useFakeTimers();
    render(<Bienvenida />);
    fireEvent.click(screen.getByRole('status', { name: 'Bienvenida a Vastio' }));
    act(() => { vi.advanceTimersByTime(300); });
    expect(screen.queryByRole('status', { name: 'Bienvenida a Vastio' })).not.toBeInTheDocument();
  });

  it('con «reducir movimiento» no aparece', () => {
    vi.stubGlobal('matchMedia', (q: string) => ({ matches: q.includes('reduce'), media: q }));
    render(<Bienvenida />);
    expect(screen.queryByRole('status', { name: 'Bienvenida a Vastio' })).not.toBeInTheDocument();
  });
});
