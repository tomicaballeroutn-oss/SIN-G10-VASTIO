import { createContext, useContext } from 'react';
import type { UsuarioSesion } from '../../api/cliente';

export type EstadoSesion = 'cargando' | 'anonima' | 'activa';

export interface ValorSesion {
  estado: EstadoSesion;
  usuario: UsuarioSesion | null;
  /** Venció por inactividad o el refresh falló: se muestra UI-02 sobre la pantalla actual. */
  vencida: boolean;
  /** Faltan MINUTOS_AVISO_EXPIRACION o menos para vencer. */
  avisoVencimiento: boolean;
  ingresar: (nombreUsuario: string, contrasena: string) => Promise<void>;
  salir: () => Promise<void>;
  seguirTrabajando: () => Promise<void>;
}

export const SesionContext = createContext<ValorSesion | null>(null);

export function useSesion(): ValorSesion {
  const valor = useContext(SesionContext);
  if (!valor) throw new Error('useSesion necesita <SesionProvider>');
  return valor;
}
