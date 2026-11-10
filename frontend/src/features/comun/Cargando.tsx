import type { ReactNode } from 'react';
import type { Datos } from '../../api/useDatos';
import { Alert, Button, EsperaAnimada } from '../../ds';

/**
 * Mientras carga, error con reintento, o el contenido con los datos. Si la carga tarda, la marca animada; una vez que
 * aparece se ve completa.
 */
export function Cargando<T>({ datos, children }: { datos: Datos<T>; children: (datos: T) => ReactNode }) {
  if (datos.error) {
    return (
      <Alert tone="danger" title="No pudimos traer los datos" action={<Button variant="outline" size="sm" onClick={datos.recargar}>Reintentar</Button>}>
        {datos.error.message}
      </Alert>
    );
  }
  return (
    <EsperaAnimada cargando={datos.datos === undefined} mientras={<p className="body-sm v-muted" role="status">Cargando…</p>}>
      {datos.datos === undefined ? null : children(datos.datos)}
    </EsperaAnimada>
  );
}
