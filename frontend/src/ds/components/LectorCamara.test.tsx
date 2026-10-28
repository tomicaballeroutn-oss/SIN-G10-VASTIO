import { act, render, screen, fireEvent } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LectorCodigo } from './lector';
import { iniciarConZxing } from './camaraLector';

const scan = vi.hoisted(() => vi.fn(() => ({ stop: vi.fn() })));
vi.mock('@zxing/browser', () => ({ BrowserMultiFormatReader: class { scan = scan; } }));
function diferida<T>() { let resolver!: (v: T) => void; const promesa = new Promise<T>((r) => { resolver = r; }); return { promesa, resolver }; }
function captura() {
  const track = Object.assign(new EventTarget(), { stop: vi.fn(), readyState: 'live' });
  const stream = { getTracks: () => [track], getVideoTracks: () => [track] } as unknown as MediaStream;
  return { track, stream };
}
beforeEach(() => { vi.stubGlobal('isSecureContext', true); scan.mockClear(); vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(); });
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); Reflect.deleteProperty(navigator, 'mediaDevices'); });
function camara(getUserMedia: ReturnType<typeof vi.fn>) { Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia } }); }

describe('sesión real del adaptador con dispositivos simulados', () => {
  it('configura reproducción móvil antes de play y libera solo su stream al cerrar', async () => {
    const { track, stream } = captura(); camara(vi.fn().mockResolvedValue(stream));
    const video = document.createElement('video');
    vi.mocked(video.play).mockImplementation(async () => {
      expect(video.muted).toBe(true); expect(video.playsInline).toBe(true); expect(video.autoplay).toBe(true);
      expect(video.hasAttribute('playsinline')).toBe(true); expect(video.hasAttribute('webkit-playsinline')).toBe(true);
      expect(video.srcObject).toBe(stream);
    });
    const read = vi.fn(); const abort = new AbortController();
    const control = await iniciarConZxing(video, read, abort.signal);
    expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({ audio: false, video: { facingMode: { ideal: 'environment' } } });
    abort.abort(); control.stop();
    expect(track.stop).toHaveBeenCalledOnce(); expect(video.srcObject).toBeNull();
  });
  it('libera un permiso que llega después de cancelar sin reproducir ni decodificar', async () => {
    const { track, stream } = captura(); const permiso = diferida<MediaStream>();
    const get = vi.fn().mockReturnValue(permiso.promesa); camara(get);
    const abort = new AbortController(); const video = document.createElement('video');
    const inicio = iniciarConZxing(video, vi.fn(), abort.signal);
    await vi.waitFor(() => expect(get).toHaveBeenCalled()); abort.abort(); permiso.resolver(stream);
    await expect(inicio).rejects.toMatchObject({ name: 'AbortError' });
    expect(track.stop).toHaveBeenCalledOnce(); expect(video.play).not.toHaveBeenCalled(); expect(scan).not.toHaveBeenCalled();
  });
  it('libera la cámara cuando play rechaza y permite reintentar', async () => {
    const { track, stream } = captura(); camara(vi.fn().mockResolvedValue(stream));
    vi.mocked(HTMLMediaElement.prototype.play).mockRejectedValueOnce(new DOMException('reproducción bloqueada', 'NotAllowedError'));
    render(<LectorCodigo open onRead={vi.fn()} onClose={vi.fn()} onManual={vi.fn()} />);
    expect(await screen.findByText(/No pudimos abrir la cámara/)).toBeInTheDocument(); expect(track.stop).toHaveBeenCalledOnce();
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    await vi.waitFor(() => expect(scan).toHaveBeenCalledOnce());
  });
  it('cancela durante play, descarta la resolución tardía y no inicia el decodificador', async () => {
    const { track, stream } = captura(); camara(vi.fn().mockResolvedValue(stream)); const play = diferida<void>();
    vi.mocked(HTMLMediaElement.prototype.play).mockReturnValue(play.promesa);
    const abort = new AbortController(); const video = document.createElement('video');
    const inicio = iniciarConZxing(video, vi.fn(), abort.signal);
    await vi.waitFor(() => expect(video.play).toHaveBeenCalled()); abort.abort(); expect(track.stop).toHaveBeenCalledOnce();
    play.resolver(); await expect(inicio).rejects.toMatchObject({ name: 'AbortError' }); expect(scan).not.toHaveBeenCalled();
  });
  it('una pista interrumpida informa el error, apaga la captura y retira listeners al desmontar', async () => {
    const { track, stream } = captura(); camara(vi.fn().mockResolvedValue(stream)); const quitar = vi.spyOn(track, 'removeEventListener');
    const { unmount } = render(<LectorCodigo open onRead={vi.fn()} onClose={vi.fn()} onManual={vi.fn()} />);
    await vi.waitFor(() => expect(scan).toHaveBeenCalledOnce());
    await act(async () => track.dispatchEvent(new Event('ended')));
    expect(screen.getByText(/No pudimos abrir la cámara/)).toBeInTheDocument(); expect(track.stop).toHaveBeenCalledOnce();
    unmount(); expect(quitar).toHaveBeenCalledWith('ended', expect.any(Function));
  });
  it.each(['NotAllowedError', 'NotFoundError', 'NotReadableError', 'Error'])('controla inicialización fallida: %s', async (name) => {
    camara(vi.fn().mockRejectedValue(new DOMException('fallo simulado', name)));
    render(<LectorCodigo open onRead={vi.fn()} onClose={vi.fn()} onManual={vi.fn()} />);
    expect(await screen.findByRole('alert')).toBeInTheDocument(); expect(scan).not.toHaveBeenCalled();
    if (name === 'NotReadableError') expect(screen.getByRole('button', { name: 'Reintentar' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Elegir a mano' })).toBeEnabled();
  });
});
