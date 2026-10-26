import { act, render } from '@testing-library/react';
import { StrictMode } from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { LectorCodigo, type ControlLector, type IniciarLector } from './lector';

afterEach(() => { vi.unstubAllGlobals(); Reflect.deleteProperty(navigator, 'mediaDevices'); });
it('aísla el video de cada efecto y descarta lecturas obsoletas bajo StrictMode', async () => {
  vi.stubGlobal('isSecureContext', true);
  Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn() } });
  const sesiones: { video: HTMLVideoElement; leer: (s: string) => void; resolver: (c: ControlLector) => void }[] = [];
  const iniciar: IniciarLector = (video, leer) => new Promise((resolver) => sesiones.push({ video, leer, resolver }));
  const onRead = vi.fn();
  const { unmount } = render(<StrictMode><LectorCodigo open onRead={onRead} onClose={() => undefined} onManual={() => undefined} iniciar={iniciar} /></StrictMode>);
  expect(sesiones).toHaveLength(2);
  act(() => sesiones[0].leer('7790000000019'));
  expect(onRead).not.toHaveBeenCalled();
  expect(sesiones[0].video).not.toBe(sesiones[1].video);
  const actual = { stop: vi.fn() };
  await act(async () => sesiones[1].resolver(actual));
  const viejo = { stop: vi.fn(() => { sesiones[0].video.srcObject = null; }) };
  await act(async () => sesiones[0].resolver(viejo));
  expect(viejo.stop).toHaveBeenCalledOnce();
  expect(actual.stop).not.toHaveBeenCalled();
  act(() => sesiones[1].leer('7790000000019'));
  expect(onRead).toHaveBeenCalledOnce();
  unmount();
  expect(actual.stop).toHaveBeenCalledOnce();
  act(() => sesiones[1].leer('7790000000026'));
  expect(onRead).toHaveBeenCalledOnce();
});

it('descarta el error tardío de una apertura cerrada y conserva la segunda', async () => {
  vi.stubGlobal('isSecureContext', true);
  Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn() } });
  let rechazar!: (e: Error) => void;
  const actual = { stop: vi.fn() };
  const iniciar = vi.fn<IniciarLector>().mockImplementationOnce(() => new Promise((_r, reject) => { rechazar = reject; })).mockResolvedValue(actual);
  const props = { onRead: vi.fn(), onClose: vi.fn(), onManual: vi.fn(), iniciar };
  const { rerender, unmount } = render(<LectorCodigo open {...props} />);
  rerender(<LectorCodigo open={false} {...props} />);
  rerender(<LectorCodigo open {...props} />);
  await act(async () => rechazar(new Error('apertura obsoleta')));
  expect(document.querySelector('[role="alert"]')).toBeNull();
  expect(actual.stop).not.toHaveBeenCalled();
  unmount(); expect(actual.stop).toHaveBeenCalledOnce();
});
