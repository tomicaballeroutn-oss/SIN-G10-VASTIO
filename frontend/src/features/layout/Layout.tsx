import { Navigate, Outlet, useLocation, useNavigate } from 'react-router';
import { AnimacionMarca, Logo, Nav, useEsperaAnimada } from '../../ds';
import { Campana } from '../notificaciones/Campana';
import { AvisosDeSesion } from '../sesion/AvisosDeSesion';
import { useSesion } from '../sesion/contexto';
import { BloqueCuenta } from './BloqueCuenta';
import { ITEM_MAS, itemsInferiores, itemsPara } from './menu';
import './layout.css';

const RUTA_MI_CUENTA = '/mi-cuenta';

/**
 * Marco de las pantallas con sesión: riel flotante en escritorio (≥ 960 px) y barra inferior flotante en teléfono y tableta.
 * Cada perfil ve solo sus ítems; un grupo sin ítems no se dibuja. Arriba del contenido, la campana de notificaciones.
 */
export function Layout() {
  const { estado, usuario } = useSesion();
  const ubicacion = useLocation();
  const navegar = useNavigate();
  // Al abrir la app, mientras se recupera la sesión: la marca animada si tarda, completa.
  const { animando, alTerminarVuelta } = useEsperaAnimada(estado === 'cargando');

  if (animando) {
    return (
      <div className="v-espera-animada v-espera-animada--pantalla" role="status" aria-label="Cargando Vastio">
        <AnimacionMarca tamano={160} repetir alTerminar={alTerminarVuelta} />
      </div>
    );
  }
  // Mientras se recupera la sesión, solo el fondo de la app: la marca aparece si de verdad se anima, no un instante.
  if (estado === 'cargando') return <div className="v-espera-animada v-espera-animada--pantalla" role="status" aria-label="Cargando Vastio" />;
  if (estado === 'anonima' || !usuario) {
    return <Navigate to={`/login?volver=${encodeURIComponent(ubicacion.pathname)}`} replace />;
  }

  // Entró con la contraseña inicial del alta: tiene que elegir una propia antes de usar el sistema (UI-03).
  if (usuario.debeCambiarContrasena && ubicacion.pathname !== RUTA_MI_CUENTA) {
    return <Navigate to={RUTA_MI_CUENTA} replace />;
  }

  const items = itemsPara(usuario.roles);
  const inferiores = itemsInferiores(items);
  const todos = [...items, ITEM_MAS];
  const actual = todos.find((it) => ubicacion.pathname.startsWith(it.ruta))?.id;
  const actualInferior = inferiores.some((it) => it.id === actual) ? actual : ITEM_MAS.id;
  const ir = (id: string) => {
    const destino = todos.find((it) => it.id === id);
    if (destino) navegar(destino.ruta);
  };

  return (
    <div className={usuario.debeCambiarContrasena ? 'app app--cuenta-inicial' : 'app'}>
      {!usuario.debeCambiarContrasena && (
        <div className="app__lateral">
          <Nav layout="rail" label="Navegación principal" items={items} value={actual} onSelect={ir} logo={<Logo variant="marca" reverse />} footer={<BloqueCuenta />} />
        </div>
      )}
      <main className="app__contenido">
        {!usuario.debeCambiarContrasena && (
          <div className="app__barra">
            <Campana />
          </div>
        )}
        <Outlet />
      </main>
      {!usuario.debeCambiarContrasena && (
        <div className="app__inferior">
          <Nav layout="bottom" label="Navegación principal" items={inferiores} value={actualInferior} onSelect={ir} />
        </div>
      )}
      <AvisosDeSesion />
    </div>
  );
}
