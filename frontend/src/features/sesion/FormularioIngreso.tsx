import { useState, type FormEvent } from 'react';
import { ProblemaApi } from '../../api/problema';
import { Alert, Button, Input } from '../../ds';
import { useSesion } from './contexto';

interface Errores {
  nombreUsuario?: string;
  contrasena?: string;
  general?: { titulo: string; detalle: string };
}

/** Usuario, contraseña e «Ingresar». Lo usan UI-01 y el diálogo de sesión vencida (UI-02). */
export function FormularioIngreso({ usuarioInicial = '', alIngresar }: { usuarioInicial?: string; alIngresar?: () => void }) {
  const { ingresar } = useSesion();
  const [nombreUsuario, setNombreUsuario] = useState(usuarioInicial);
  const [contrasena, setContrasena] = useState('');
  const [errores, setErrores] = useState<Errores>({});
  const [enviando, setEnviando] = useState(false);

  async function enviar(e: FormEvent) {
    e.preventDefault();
    const faltan: Errores = {
      nombreUsuario: nombreUsuario.trim() ? undefined : 'Escribí tu usuario.',
      contrasena: contrasena ? undefined : 'Escribí tu contraseña.',
    };
    setErrores(faltan);
    if (faltan.nombreUsuario || faltan.contrasena) return;

    setEnviando(true);
    try {
      await ingresar(nombreUsuario.trim(), contrasena);
      alIngresar?.();
    } catch (error) {
      setContrasena('');
      if (error instanceof ProblemaApi && error.codigo === 'CREDENCIALES_INVALIDAS') {
        setErrores({ general: { titulo: 'Usuario o contraseña incorrectos', detalle: 'Volvé a intentarlo.' } });
      } else if (error instanceof ProblemaApi) {
        setErrores({
          nombreUsuario: error.errorDe('nombreUsuario'),
          contrasena: error.errorDe('contrasena'),
          general: { titulo: 'No pudimos iniciar la sesión', detalle: error.message },
        });
      } else {
        throw error;
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <form className="ingreso" onSubmit={enviar} noValidate>
      <Input
        label="Usuario"
        name="usuario"
        placeholder="nombre.apellido"
        size="lg"
        autoComplete="username"
        value={nombreUsuario}
        onChange={(e) => setNombreUsuario(e.target.value)}
        error={errores.nombreUsuario}
      />
      <Input
        label="Contraseña"
        name="contrasena"
        type="password"
        placeholder="••••••••"
        size="lg"
        autoComplete="current-password"
        value={contrasena}
        onChange={(e) => setContrasena(e.target.value)}
        error={errores.contrasena}
      />
      {errores.general && (
        <Alert tone="danger" title={errores.general.titulo}>{errores.general.detalle}</Alert>
      )}
      <Button type="submit" size="lg" block loading={enviando}>Ingresar</Button>
    </form>
  );
}
