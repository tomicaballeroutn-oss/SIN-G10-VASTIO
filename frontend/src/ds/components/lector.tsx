import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Button } from './Button';
import { Alert, Dialog } from './feedback';

/** Lo que devuelve la librería al empezar a leer: para apagar la cámara al cerrar. */
export interface ControlLector {
  stop: () => void;
}

/**
 * Enciende la cámara trasera en `video` y llama a `alLeer` con cada código leído. Separado del componente para
 * poder reemplazarlo en los tests (jsdom no tiene cámara). La librería se carga recién al abrir el lector.
 */
export type IniciarLector = (video: HTMLVideoElement, alLeer: (codigo: string) => void) => Promise<ControlLector>;

const iniciarConZxing: IniciarLector = async (video, alLeer) => {
  const [{ BrowserMultiFormatReader }, { BarcodeFormat, DecodeHintType }] = await Promise.all([
    import('@zxing/browser'),
    import('@zxing/library'),
  ]);
  // Botella (EAN-13, EAN-8, UPC-A) y caja (DUN-14 en ITF, GS1-128): menos formatos, lectura más rápida.
  const formatos = [BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.ITF, BarcodeFormat.CODE_128];
  const lector = new BrowserMultiFormatReader(new Map([[DecodeHintType.POSSIBLE_FORMATS, formatos]]));
  return lector.decodeFromConstraints({ audio: false, video: { facingMode: { ideal: 'environment' } } }, video, (resultado) => {
    if (resultado) alLeer(resultado.getText());
  });
};

type Problema = 'sin-https' | 'sin-camara' | 'sin-permiso' | 'error';

const MENSAJES: Record<Problema, string> = {
  'sin-https': 'La cámara necesita una conexión segura (HTTPS). Cargá el código a mano.',
  'sin-camara': 'No encontramos una cámara en este dispositivo. Cargá el código a mano.',
  'sin-permiso': 'No tenemos permiso para usar la cámara. Habilitalo en la configuración del navegador o cargá el código a mano.',
  error: 'No pudimos abrir la cámara. Volvé a intentarlo o cargá el código a mano.',
};

function problemaDe(error: unknown): Problema {
  const nombre = error instanceof Error || error instanceof DOMException ? error.name : '';
  if (nombre === 'NotAllowedError' || nombre === 'SecurityError') return 'sin-permiso';
  if (nombre === 'NotFoundError' || nombre === 'OverconstrainedError' || nombre === 'NotReadableError') return 'sin-camara';
  return 'error';
}

/** El mismo código leído varias veces seguidas (la cámara sigue apuntando) cuenta una sola vez. */
const PAUSA_MISMO_CODIGO_MS = 2000;

export interface LectorCodigoProps {
  open: boolean;
  onClose: () => void;
  /** Cada código leído. La pantalla decide si cierra el lector o sigue leyendo (p. ej. varias cajas en un ingreso). */
  onRead: (codigo: string) => void;
  /** Carga manual: siempre disponible (la cámara puede no estar o no tener permiso). */
  manualLabel?: string;
  onManual: () => void;
  /** Indicación arriba de la cámara. */
  instruction?: string;
  /** Confirmación de la última lectura (p. ej. «Fernet Branca · Caja de 6»). */
  children?: ReactNode;
  /** Solo para tests y la página del sistema de diseño. */
  iniciar?: IniciarLector;
}

/**
 * Lector de código de barras con la cámara del celular (UI-30), en Android y en iOS. Necesita HTTPS; si no hay cámara,
 * permiso o conexión segura, lo dice y ofrece la carga manual. El código identifica el artículo; la cantidad se tipea.
 */
export function LectorCodigo(props: LectorCodigoProps) {
  // Montado solo mientras está abierto: cada apertura arranca sin problemas ni lecturas anteriores.
  return props.open ? <LectorAbierto {...props} /> : null;
}

/** Sin conexión segura o sin cámara no hay nada que intentar: se sabe antes de encender nada. */
function problemaDelDispositivo(): Problema | null {
  if (!window.isSecureContext) return 'sin-https';
  if (!navigator.mediaDevices?.getUserMedia) return 'sin-camara';
  return null;
}

function LectorAbierto({ onClose, onRead, manualLabel = 'Elegir a mano', onManual, instruction = 'Apuntá al código de barras', children, iniciar = iniciarConZxing }: LectorCodigoProps) {
  const video = useRef<HTMLVideoElement>(null);
  const [fallo, setFallo] = useState<Problema | null>(null);
  const [intento, setIntento] = useState(0);
  const delDispositivo = problemaDelDispositivo();
  const problema = delDispositivo ?? fallo;
  const alLeer = useRef(onRead);
  useEffect(() => {
    alLeer.current = onRead;
  }, [onRead]);

  useEffect(() => {
    if (delDispositivo) return;
    let vigente = true;
    let control: ControlLector | undefined;
    let ultimo = { codigo: '', momento: 0 };
    iniciar(video.current as HTMLVideoElement, (codigo) => {
      const ahora = Date.now();
      if (codigo === ultimo.codigo && ahora - ultimo.momento < PAUSA_MISMO_CODIGO_MS) return;
      ultimo = { codigo, momento: ahora };
      alLeer.current(codigo);
    }).then(
      (c) => {
        if (vigente) control = c;
        else c.stop();
      },
      (error: unknown) => {
        if (vigente) setFallo(problemaDe(error));
      },
    );
    return () => {
      vigente = false;
      control?.stop();
    };
  }, [delDispositivo, iniciar, intento]);

  function reintentar() {
    setFallo(null);
    setIntento((n) => n + 1);
  }

  return (
    <Dialog
      open
      title="Leer código de barras"
      onClose={onClose}
      className="v-lector"
      actions={<Button variant="outline" onClick={onManual}>{manualLabel}</Button>}
    >
      <div className="v-lector__cuerpo">
        {problema ? (
          <Alert
            tone="warning"
            icon="triangle-alert"
            action={problema === 'error' || problema === 'sin-permiso' ? (
              <Button variant="outline" size="sm" onClick={reintentar}>Reintentar</Button>
            ) : undefined}
          >
            {MENSAJES[problema]}
          </Alert>
        ) : (
          <p className="body-sm v-muted">{instruction}</p>
        )}
        {/* Siempre montado, para que «Reintentar» tenga dónde mostrar la cámara. */}
        <div className="v-lector__visor" hidden={problema !== null}>
          {/* playsInline y muted: sin ellos iOS abre el video a pantalla completa o no lo reproduce. */}
          <video ref={video} className="v-lector__video" playsInline muted autoPlay aria-label="Vista de la cámara" />
          <div className="v-lector__marco" aria-hidden="true" />
        </div>
        {children && <div className="v-lector__lectura" role="status">{children}</div>}
      </div>
    </Dialog>
  );
}
