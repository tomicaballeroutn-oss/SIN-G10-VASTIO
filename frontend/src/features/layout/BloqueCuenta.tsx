import { useNavigate } from 'react-router';
import { NOMBRE_DE_ROL } from '../../api/usuarios';
import { Actor, Button } from '../../ds';
import { useSesion } from '../sesion/contexto';

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
