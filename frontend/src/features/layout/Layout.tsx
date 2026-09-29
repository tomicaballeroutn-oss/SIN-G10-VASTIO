import { Navigate, Outlet, useLocation, useNavigate } from 'react-router';
import { Logo, Nav } from '../../ds';
import { AvisosDeSesion } from '../sesion/AvisosDeSesion';
import { useSesion } from '../sesion/contexto';
import { BloqueCuenta } from './BloqueCuenta';
import { ITEM_MAS, itemsInferiores, itemsPara } from './menu';
import './layout.css';

/**
 * Marco de las pantallas con sesión: Nav lateral en escritorio (≥ 960 px) e inferior en teléfono y tableta.
 * Cada perfil ve solo sus ítems; un grupo sin ítems no se dibuja.
 */
export function Layout() {
  const { estado, usuario } = useSesion();
  const ubicacion = useLocation();
  const navegar = useNavigate();

  if (estado === 'cargando') return null;
  if (estado === 'anonima' || !usuario) {
    return <Navigate to={`/login?volver=${encodeURIComponent(ubicacion.pathname)}`} replace />;
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
    <div className="app">
      <div className="app__lateral">
        <Nav label="Navegación principal" items={items} value={actual} onSelect={ir} logo={<Logo variant="marca" />} footer={<BloqueCuenta />} />
      </div>
      <main className="app__contenido">
        <Outlet />
      </main>
      <div className="app__inferior">
        <Nav layout="bottom" label="Navegación principal" items={inferiores} value={actualInferior} onSelect={ir} />
      </div>
      <AvisosDeSesion />
    </div>
  );
}
