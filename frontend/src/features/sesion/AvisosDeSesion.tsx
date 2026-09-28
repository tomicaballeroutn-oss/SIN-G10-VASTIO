import { useState } from 'react';
import { Alert, Button, Dialog, Icon } from '../../ds';
import { FormularioIngreso } from './FormularioIngreso';
import { useSesion } from './contexto';
import './sesion.css';

/**
 * UI-02 · Sesión por vencer y sesión vencida. Se dibujan sobre la pantalla actual, que queda montada:
 * al volver a ingresar la persona sigue donde estaba, con lo que había tipeado.
 */
export function AvisosDeSesion() {
  const { vencida, avisoVencimiento, seguirTrabajando, usuario } = useSesion();
  const [reingresando, setReingresando] = useState(false);

  return (
    <>
      {avisoVencimiento && !vencida && (
        <div className="aviso-sesion">
          <Alert
            tone="info"
            icon="clock"
            title="Tu sesión va a expirar pronto"
            action={<Button variant="outline" size="md" onClick={() => void seguirTrabajando()}>Seguir trabajando</Button>}
          >
            Guardá los cambios que tengas pendientes.
          </Alert>
        </div>
      )}
      <Dialog
        open={vencida}
        title="Tu sesión expiró"
        actions={!reingresando && <Button size="lg" onClick={() => setReingresando(true)}>Volver a ingresar</Button>}
      >
        {reingresando ? (
          // Al vencer se conserva el usuario: solo se pide la contraseña de nuevo.
          <FormularioIngreso usuarioInicial={usuario?.nombreUsuario} alIngresar={() => setReingresando(false)} />
        ) : (
          <div className="sesion-vencida">
            <Icon name="lock" size={24} />
            <p className="body-lg">
              Por seguridad, cerramos tu sesión después de un tiempo sin actividad. Volvé a ingresar para seguir donde estabas.
            </p>
          </div>
        )}
      </Dialog>
    </>
  );
}
