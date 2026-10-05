import type { ReactNode } from 'react';
import type { Datos } from '../../api/useDatos';
import { Alert, Button } from '../../ds';

/** Mientras carga, error con reintento, o el contenido con los datos. */
export function Cargando<T>({ datos, children }: { datos: Datos<T>; children: (datos: T) => ReactNode }) {
  if (datos.error) {
    return (
      <Alert tone="danger" title="No pudimos traer los datos" action={<Button variant="outline" size="sm" onClick={datos.recargar}>Reintentar</Button>}>
        {datos.error.message}
      </Alert>
    );
  }
  if (datos.datos === undefined) return <p className="body-sm v-muted" role="status">Cargando…</p>;
  return children(datos.datos);
}
