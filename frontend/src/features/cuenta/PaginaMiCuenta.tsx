import { useState } from 'react';
import { NOMBRE_DE_ROL } from '../../api/usuarios';
import { Alert, Button, Card, Tabs, aplicarTema, temaGuardado, type Tema } from '../../ds';
import { Encabezado } from '../layout/paginas';
import { useSesion } from '../sesion/contexto';
import { FormularioContrasena } from './FormularioContrasena';

/**
 * Mi cuenta (UI-03): datos de la sesión, cambio de contraseña, tema de la pantalla y cierre de sesión.
 * Si la persona entró con la contraseña inicial que le dieron en el alta (`debeCambiarContrasena`), el marco de
 * la app no la deja salir de esta pantalla hasta que elija una propia.
 */
export function PaginaMiCuenta() {
  const { usuario, salir } = useSesion();
  const [tema, setTema] = useState<Tema>(temaGuardado());
  if (!usuario) return null;
  return (
    <section className="pantalla">
      <Encabezado titulo="Mi cuenta" />
      {usuario.debeCambiarContrasena && (
        <Alert tone="warning" title="Elegí tu contraseña">
          Ingresaste con la contraseña que te dieron al darte de alta. Elegí una propia para seguir usando Vastio.
        </Alert>
      )}
      <Card title={usuario.nombreCompleto} subtitle={`Usuario: ${usuario.nombreUsuario} · ${usuario.roles.map((r) => NOMBRE_DE_ROL[r]).join(' · ')}`}>
        <div className="pantalla">
          <div className="pantalla__titulos">
            <p className="label">Contraseña</p>
            <p className="body-sm v-muted">Para cambiarla necesitás la actual. Las sesiones que ya tengas abiertas siguen hasta que venzan.</p>
          </div>
          <FormularioContrasena />
        </div>
      </Card>
      <Card title="Tema de la pantalla">
        <div className="pantalla">
          <div className="pantalla__titulos">
            <p className="body-sm v-muted">El oscuro es para la operación nocturna en barra y depósito.</p>
          </div>
          <Tabs
            variant="segmented"
            label="Tema de la pantalla"
            value={tema}
            onChange={(id) => {
              setTema(id as Tema);
              aplicarTema(id as Tema);
            }}
            items={[
              { id: 'claro', label: 'Claro', icon: 'sun' },
              { id: 'oscuro', label: 'Oscuro', icon: 'moon' },
              { id: 'sistema', label: 'Como el dispositivo' },
            ]}
          />
        </div>
      </Card>
      <div>
        <Button variant="outline" icon="log-out" onClick={() => void salir()}>Cerrar sesión</Button>
      </div>
    </section>
  );
}
