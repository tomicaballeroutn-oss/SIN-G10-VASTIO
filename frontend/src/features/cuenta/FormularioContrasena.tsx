import { useEffect, useState, type FormEvent } from 'react';
import { cuenta } from '../../api/cuenta';
import { renovarSesion, suscribirSesion } from '../../api/cliente';
import { ProblemaApi } from '../../api/problema';
import { Alert, Button, Input } from '../../ds';

export const MINIMO_CONTRASENA = 8;
export const MAXIMO_CONTRASENA = 72;

interface Errores {
  actual?: string;
  nueva?: string;
  repetida?: string;
  general?: { titulo: string; detalle: string };
}

/** Cambio de contraseña de Mi cuenta (UI-03): la actual, la nueva y la nueva otra vez. */
export function FormularioContrasena({ alCambiar }: { alCambiar?: () => void }) {
  const [actual, setActual] = useState('');
  const [nueva, setNueva] = useState('');
  const [repetida, setRepetida] = useState('');
  const [errores, setErrores] = useState<Errores>({});
  const [enviando, setEnviando] = useState(false);
  const [cambiada, setCambiada] = useState(false);
  const [renovacionPendiente, setRenovacionPendiente] = useState(false);

  useEffect(() => suscribirSesion((evento) => {
    if (renovacionPendiente && (evento.tipo === 'iniciada' || evento.tipo === 'renovada')
        && !evento.sesion.usuario.debeCambiarContrasena) {
      setRenovacionPendiente(false);
      setCambiada(true);
    }
  }), [renovacionPendiente]);

  async function actualizarSesion() {
    try {
      const sesion = await renovarSesion();
      // Una renovación compartida pudo iniciarse antes de que se guardara el cambio.
      if (sesion.usuario.debeCambiarContrasena) {
        setRenovacionPendiente(true);
        return;
      }
      setRenovacionPendiente(false);
      setCambiada(true);
      alCambiar?.();
    } catch {
      setRenovacionPendiente(true);
    }
  }

  async function enviar(e: FormEvent) {
    e.preventDefault();
    if (enviando) return;
    setCambiada(false);
    const faltan: Errores = {
      actual: actual ? undefined : 'Escribí tu contraseña actual.',
      nueva: validarNueva(nueva, actual),
      repetida: repetida === nueva ? undefined : 'Las dos contraseñas nuevas tienen que ser iguales.',
    };
    setErrores(faltan);
    if (faltan.actual || faltan.nueva || faltan.repetida) return;

    setEnviando(true);
    try {
      await cuenta.cambiarContrasena({ contrasenaActual: actual, contrasenaNueva: nueva });
      setActual('');
      setNueva('');
      setRepetida('');
      // El cambio ya se guardó; el fallo de sesión tiene su propia recuperación.
      await actualizarSesion();
    } catch (error) {
      if (!(error instanceof ProblemaApi)) throw error;
      if (error.codigo === 'CONTRASENA_ACTUAL_INCORRECTA') {
        setErrores({ actual: error.message });
      } else if (error.codigo === 'CONTRASENA_REPETIDA' || error.codigo === 'CONTRASENA_DEMASIADO_LARGA') {
        setErrores({ nueva: error.message });
      } else {
        setErrores({
          actual: error.errorDe('contrasenaActual'),
          nueva: error.errorDe('contrasenaNueva'),
          general: { titulo: 'No pudimos cambiar la contraseña', detalle: error.message },
        });
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className="pantalla" onSubmit={enviar} noValidate aria-label="Cambiar contraseña">
      <Input
        label="Contraseña actual"
        name="contrasena-actual"
        type="password"
        autoComplete="current-password"
        value={actual}
        onChange={(e) => setActual(e.target.value)}
        error={errores.actual}
      />
      <Input
        label="Contraseña nueva"
        name="contrasena-nueva"
        type="password"
        autoComplete="new-password"
        hint={`De ${MINIMO_CONTRASENA} a ${MAXIMO_CONTRASENA} caracteres.`}
        value={nueva}
        onChange={(e) => setNueva(e.target.value)}
        error={errores.nueva}
      />
      <Input
        label="Repetí la contraseña nueva"
        name="contrasena-repetida"
        type="password"
        autoComplete="new-password"
        value={repetida}
        onChange={(e) => setRepetida(e.target.value)}
        error={errores.repetida}
      />
      {errores.general && <Alert tone="danger" title={errores.general.titulo}>{errores.general.detalle}</Alert>}
      {cambiada && !renovacionPendiente && <Alert tone="success" title="Contraseña actualizada">La próxima vez que ingreses, usá la nueva.</Alert>}
      {renovacionPendiente && (
        <Alert tone="warning" title="Contraseña guardada; sesión sin actualizar">
          Tu contraseña nueva ya está guardada. Reintentá actualizar la sesión o cerrá sesión y volvé a ingresar con la nueva contraseña.
          <Button type="button" loading={enviando} onClick={async () => {
            setEnviando(true);
            try { await actualizarSesion(); } finally { setEnviando(false); }
          }}>Reintentar actualizar sesión</Button>
        </Alert>
      )}
      <div>
        <Button type="submit" loading={enviando}>Actualizar contraseña</Button>
      </div>
    </form>
  );
}

function validarNueva(nueva: string, actual: string): string | undefined {
  if (!nueva) return 'Escribí la contraseña nueva.';
  if (nueva.length < MINIMO_CONTRASENA || nueva.length > MAXIMO_CONTRASENA) {
    return `La contraseña nueva tiene que tener entre ${MINIMO_CONTRASENA} y ${MAXIMO_CONTRASENA} caracteres.`;
  }
  if (new TextEncoder().encode(nueva).length > MAXIMO_CONTRASENA) {
    return 'La contraseña nueva es demasiado larga. Usá menos caracteres.';
  }
  if (nueva === actual) return 'La contraseña nueva tiene que ser distinta de la actual. Elegí otra.';
  return undefined;
}
