import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AnimacionMarca, CelebracionHito } from './animacion';

describe('AnimacionMarca', () => {
  afterEach(() => { vi.unstubAllGlobals(); });

  it('aplica la velocidad al video', () => {
    vi.stubGlobal('matchMedia', (q: string) => ({ matches: false, media: q }));
    render(<AnimacionMarca tono="blanco" velocidad={1.8} />);
    const video = document.querySelector('video')!;
    expect(video.playbackRate).toBe(1.8);
    expect(video.defaultPlaybackRate).toBe(1.8);
    video.playbackRate = 1;
    fireEvent.loadedMetadata(video);
    expect(video.playbackRate).toBe(1.8);
  });

  it('sin velocidad va a velocidad normal', () => {
    vi.stubGlobal('matchMedia', (q: string) => ({ matches: false, media: q }));
    render(<AnimacionMarca tono="azul" />);
    expect(document.querySelector('video')!.playbackRate).toBe(1);
  });
});

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
