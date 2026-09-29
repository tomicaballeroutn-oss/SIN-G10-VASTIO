import { useCallback, useEffect, useState } from 'react';
import { ProblemaApi } from './problema';

export interface Datos<T> {
  datos: T | undefined;
  error: ProblemaApi | undefined;
  cargando: boolean;
  /** Vuelve a pedir los datos. */
  recargar: () => void;
  /** Reemplaza los datos en memoria, p. ej. con lo que devolvió un guardado. */
  fijar: (actualizar: (previos: T | undefined) => T) => void;
}

/**
 * Carga datos de la API al montar y cada vez que cambia `cargar`. Pasá una función estable (useCallback)
 * o definida fuera del componente, si no se vuelve a pedir en cada render.
 */
export function useDatos<T>(cargar: () => Promise<T>): Datos<T> {
  const [estado, setEstado] = useState<{ datos?: T; error?: ProblemaApi; cargando: boolean }>({ cargando: true });
  const [pedido, setPedido] = useState(0);

  useEffect(() => {
    let vigente = true;
    cargar().then(
      (datos) => {
        if (vigente) setEstado({ datos, cargando: false });
      },
      (error: unknown) => {
        if (vigente) setEstado({ error: error instanceof ProblemaApi ? error : ProblemaApi.sinConexion(), cargando: false });
      },
    );
    return () => {
      vigente = false;
    };
  }, [cargar, pedido]);

  const recargar = useCallback(() => {
    setEstado((previo) => ({ ...previo, cargando: true }));
    setPedido((n) => n + 1);
  }, []);

  const fijar = useCallback((actualizar: (previos: T | undefined) => T) => {
    setEstado((previo) => ({ ...previo, datos: actualizar(previo.datos) }));
  }, []);

  return { datos: estado.datos, error: estado.error, cargando: estado.cargando, recargar, fijar };
}

/** Estado de un envío: guardando, error de la API y confirmación. */
export function useEnvio() {
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<ProblemaApi | null>(null);

  const enviar = useCallback(async <R,>(accion: () => Promise<R>): Promise<R | undefined> => {
    setGuardando(true);
    setError(null);
    try {
      return await accion();
    } catch (e) {
      setError(e instanceof ProblemaApi ? e : ProblemaApi.sinConexion());
      return undefined;
    } finally {
      setGuardando(false);
    }
  }, []);

  return { guardando, error, enviar, limpiarError: () => setError(null) };
}
