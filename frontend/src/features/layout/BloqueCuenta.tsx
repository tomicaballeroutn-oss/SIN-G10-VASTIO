import { useNavigate } from 'react-router';
import { Actor, Button } from '../../ds';
import { useSesion } from '../sesion/contexto';

const NOMBRE_DE_ROL: Record<string, string> = {
  DIRECCION: 'Dirección',
  COORDINACION: 'Coordinación comercial',
  ADMINISTRACION: 'Administración',
  VENDEDORA: 'Vendedora',
  PLANNER: 'Planner',
  COMPRAS: 'Compras',
  BARRA: 'Barra',
  COCINA: 'Cocina',
};

/** Quién está usando el sistema, «Mi cuenta» y «Cerrar sesión». Va al pie del Nav lateral y en «Más». */
export function BloqueCuenta() {
  const { usuario, salir } = useSesion();
  const navegar = useNavigate();
  if (!usuario) return null;
  return (
    <div className="app__cuenta">
      <Actor name={usuario.nombreCompleto} action={usuario.roles.map((r) => NOMBRE_DE_ROL[r]).join(' · ')} size="sm" />
      <div className="app__cuenta-acciones">
        <Button variant="text" size="sm" icon="user" onClick={() => navegar('/mi-cuenta')}>Mi cuenta</Button>
        <Button variant="text" size="sm" icon="log-out" onClick={() => void salir()}>Cerrar sesión</Button>
      </div>
    </div>
  );
}
