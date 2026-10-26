import { useState, type FormEvent } from 'react';
import { cuenta } from '../../api/cuenta';
import { renovarSesion } from '../../api/cliente';
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

  async function enviar(e: FormEvent) {
    e.preventDefault();
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
      setCambiada(true);
      // La renovación trae la sesión con «debe cambiar la contraseña» en falso: se libera la navegación.
      await renovarSesion().catch(() => undefined);
      alCambiar?.();
    } catch (error) {
      if (!(error instanceof ProblemaApi)) throw error;
      if (error.codigo === 'CONTRASENA_ACTUAL_INCORRECTA') {
        setErrores({ actual: error.message });
      } else if (error.codigo === 'CONTRASENA_REPETIDA') {
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
      {cambiada && <Alert tone="success" title="Contraseña actualizada">La próxima vez que ingreses, usá la nueva.</Alert>}
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
  if (nueva === actual) return 'La contraseña nueva tiene que ser distinta de la actual. Elegí otra.';
  return undefined;
}
