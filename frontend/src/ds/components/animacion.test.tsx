import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CelebracionHito, EsperaAnimada } from './animacion';

function navegadorQueAnima(reducir = false) {
  vi.stubGlobal('matchMedia', (consulta: string) => ({ matches: reducir && consulta.includes('reduce'), media: consulta }));
}

describe('EsperaAnimada', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

  it('en una carga corta no muestra la animación', () => {
    navegadorQueAnima();
    const { rerender } = render(<EsperaAnimada cargando mientras={<p>Cargando…</p>}><p>Listo</p></EsperaAnimada>);
    act(() => { vi.advanceTimersByTime(200); });
    rerender(<EsperaAnimada cargando={false} mientras={<p>Cargando…</p>}><p>Listo</p></EsperaAnimada>);
    expect(screen.getByText('Listo')).toBeInTheDocument();
    expect(document.querySelector('video')).toBeNull();
  });

  it('si tarda, la animación se ve completa aunque los datos lleguen antes', () => {
    navegadorQueAnima();
    const { rerender } = render(<EsperaAnimada cargando mientras={<p>Cargando…</p>}><p>Listo</p></EsperaAnimada>);
    act(() => { vi.advanceTimersByTime(500); });
    expect(screen.getByRole('status', { name: 'Cargando' })).toBeInTheDocument();

    rerender(<EsperaAnimada cargando={false} mientras={<p>Cargando…</p>}><p>Listo</p></EsperaAnimada>);
    expect(screen.queryByText('Listo')).not.toBeInTheDocument();

    fireEvent.ended(document.querySelector('video')!);
    expect(screen.getByText('Listo')).toBeInTheDocument();
  });

  it('con «reducir movimiento» no anima', () => {
    navegadorQueAnima(true);
    render(<EsperaAnimada cargando mientras={<p>Cargando…</p>}><p>Listo</p></EsperaAnimada>);
    act(() => { vi.advanceTimersByTime(1000); });
    expect(screen.getByText('Cargando…')).toBeInTheDocument();
    expect(document.querySelector('video')).toBeNull();
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
