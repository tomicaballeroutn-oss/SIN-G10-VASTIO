import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { Logo } from '../../ds';
import { FormularioIngreso } from './FormularioIngreso';
import { useSesion } from './contexto';
import { rutaDeInicio } from '../layout/menu';
import './sesion.css';

/** UI-01 · Inicio de sesión. */
export function PaginaIngreso() {
  const { estado, usuario } = useSesion();
  const [parametros] = useSearchParams();
  const navegar = useNavigate();
  const volver = parametros.get('volver');
  // Solo rutas internas, para no redirigir afuera con un enlace armado.
  const destino = volver && volver.startsWith('/') && !volver.startsWith('//') ? volver : null;

  if (estado === 'activa' && usuario) {
    return <Navigate to={destino ?? rutaDeInicio(usuario.roles)} replace />;
  }

  return (
    <main className="pagina-ingreso">
      <div className="pagina-ingreso__columna">
        <Logo className="pagina-ingreso__logo" />
        <div className="pagina-ingreso__titulos">
          <h1 className="display-sm">Ingresá a tu cuenta</h1>
          <p className="body-lg v-muted">El sistema del complejo Avril · Club de Campo · Santa Bárbara</p>
        </div>
        {estado !== 'cargando' && <FormularioIngreso alIngresar={() => destino && navegar(destino, { replace: true })} />}
      </div>
    </main>
  );
}
