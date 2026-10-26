import type { IniciarLector, ControlLector } from './lector';

export const iniciarConZxing: IniciarLector = async (video, alLeer, signal) => {
  const [{ BrowserMultiFormatReader }, { BarcodeFormat, DecodeHintType }] = await Promise.all([
    import('@zxing/browser'), import('@zxing/library'),
  ]);
  signal?.throwIfAborted();
  const formatos = [BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.ITF, BarcodeFormat.CODE_128];
  const lector = new BrowserMultiFormatReader(new Map([[DecodeHintType.POSSIBLE_FORMATS, formatos]]));
  // La sesión es dueña del stream, incluso si play() falla o el permiso llega después del cierre.
  const stream = await navigator.mediaDevices.getUserMedia({ audio: false, video: { facingMode: { ideal: 'environment' } } });
  let control: ControlLector | undefined;
  let detenido = false;
  const stop = () => {
    if (detenido) return;
    detenido = true;
    signal?.removeEventListener('abort', stop);
    control?.stop();
    stream.getTracks().forEach((track) => track.stop());
    video.srcObject = null;
  };
  signal?.addEventListener('abort', stop, { once: true });
  try {
    signal?.throwIfAborted();
    video.muted = true;
    video.playsInline = true;
    video.autoplay = true;
    video.setAttribute('playsinline', '');
    video.setAttribute('webkit-playsinline', '');
    video.srcObject = stream;
    try {
      await video.play();
    } catch (cause) {
      throw new Error('No se pudo reproducir la cámara', { cause });
    }
    signal?.throwIfAborted();
    if (detenido) throw new DOMException('Captura finalizada', 'AbortError');
    control = lector.scan(video, (resultado) => {
      if (!detenido && resultado) alLeer(resultado.getText());
    });
    if (detenido) control.stop();
    return { stop };
  } catch (error) {
    stop();
    throw error;
  }
};
