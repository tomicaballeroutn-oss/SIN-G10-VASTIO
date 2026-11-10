import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { AnimacionMarca } from '../../ds';
import { FormularioIngreso } from './FormularioIngreso';
import { useSesion } from './contexto';
import { rutaDeInicio } from '../layout/menu';
import './sesion.css';

/**
 * UI-01 · Inicio de sesión animado («Telón», design/propuestas/login-animado): la marca se arma sobre el azul, el azul se
 * retira con un borde curvo y aparece el formulario sobre el crema. Siempre en tema claro.
 */
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
    <main className="pagina-ingreso" data-theme="light">
      <div className="pagina-ingreso__telon" aria-hidden />
      <div className="pagina-ingreso__marca" aria-hidden>
        <span className="pagina-ingreso__logo"><AnimacionMarca tono="blanco" tamano={300} velocidad={1.8} /></span>
        <p className="pagina-ingreso__nombre">VASTIO</p>
        <p className="pagina-ingreso__bajada">Todo a la vista.</p>
      </div>
      <div className="pagina-ingreso__lado">
        <div className="pagina-ingreso__form">
          <div className="pagina-ingreso__titulos">
            <p className="overline v-muted">Inicio de sesión</p>
            <h1 className="h1">Ingresá a tu cuenta</h1>
          </div>
          {estado !== 'cargando' && <FormularioIngreso alIngresar={() => destino && navegar(destino, { replace: true })} />}
        </div>
      </div>
    </main>
  );
}
